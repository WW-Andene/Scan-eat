package fr.scanneat.presentation.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.nutrition.DayNotesRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.computeMetabolics
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.engine.scoring.dailyTargets
import fr.scanneat.domain.engine.scoring.hasMinimalProfile
import fr.scanneat.domain.engine.scoring.withKcalOverride
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.DailySummary
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.domain.model.ScanSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val consumptionRepo: ConsumptionRepository,
    private val notesRepo: DayNotesRepository,
    private val customFoodRepo: CustomFoodRepository,
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,
    private val biolismRepo: BiolismRepository,
) : ViewModel() {

    // Fix 13: selectedDate as a StateFlow — avoids stale data across midnight
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Flat-map so the observation restarts whenever the date changes
    val summary: StateFlow<DailySummary> = _selectedDate
        .flatMapLatest { date -> consumptionRepo.observeDay(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            DailySummary(LocalDate.now(), emptyList(), ConsumedNutrition.ZERO))

    // Journal's macro summary previously showed only raw totals ("120g protein")
    // with no reference to the profile's actual daily target, even though
    // Dashboard's equivalent card computes and displays exactly that.
    //
    // The kcal target itself previously came from dailyTargets(profile) alone,
    // ignoring a valid Biolism profile entirely - Dashboard's CalorieBalanceCard
    // already prefers BiolismEngine.computeMetabolics().tdeeDay (a richer,
    // body-composition-aware TDEE) over the plain PAL-based profile estimate
    // whenever one exists, so Journal and Dashboard could silently disagree on
    // the same day's calorie target. Same override rule, applied here too.
    val targets: StateFlow<DailyTargets?> = combine(prefs.profile, biolismRepo.profile) { profile, bioProfile ->
        val base = if (hasMinimalProfile(profile)) dailyTargets(profile) else null
        val bioTdee = if (bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay else null
        // Previously only kcal was swapped for Biolism's tdee, leaving fat/carbs
        // computed from the old profile-only kcal - the shown macros no longer
        // summed to the kcal figure right next to them. withKcalOverride rescales
        // every kcal-derived field together so the whole row stays consistent.
        base?.let { if (bioTdee != null) it.withKcalOverride(bioTdee, profile.goal) else it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // "What would my macros be at my goal weight" - previously the Journal only
    // ever showed targets derived from the current profile weight, even for a
    // user who set a goal weight in Profile explicitly to plan around it. Only
    // emits when a goal weight is actually set and differs from the current one
    // (otherwise it would just silently duplicate the row above).
    val goalTargets: StateFlow<DailyTargets?> = combine(prefs.profile, biolismRepo.profile) { profile, bioProfile ->
        val goalWeight = profile.goalWeightKg
        if (!hasMinimalProfile(profile) || goalWeight == null || goalWeight == profile.weightKg) return@combine null
        val base = dailyTargets(profile, weightKgOverride = goalWeight) ?: return@combine null
        val bioTdee = if (bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile.copy(weightKg = goalWeight))?.tdeeDay else null
        if (bioTdee != null) base.withKcalOverride(bioTdee, profile.goal) else base
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** For the goal-targets row's label ("Objectif : NN kg") - null hides the row. */
    val goalWeightKg: StateFlow<Double?> = combine(prefs.profile, goalTargets) { profile, goal ->
        profile.goalWeightKg.takeIf { goal != null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * entry id -> short warning, e.g. "Allergen: gluten" or a diet-compliance
     * reason - same checkUserAllergens()/checkDiet() pattern already used live
     * by RecipesViewModel.recipeWarnings/GroceryViewModel/TemplatesViewModel.
     * Previously nothing in the Diary ever ran either check: a user could log
     * a product containing one of their declared allergens and see the
     * warning on Result once, then never again anywhere in the Journal.
     */
    val diaryWarnings: StateFlow<Map<Long, String>> = combine(summary, prefs.profile, language) { s, profile, lang ->
        s.entries.mapNotNull { entry ->
            val product = entry.toCheckProduct()
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(product, profile.diet, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            if (parts.isEmpty()) null else entry.id to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun goToPreviousDay() { _selectedDate.value = _selectedDate.value.minusDays(1) }
    fun goToNextDay()     { _selectedDate.value = _selectedDate.value.plusDays(1) }
    fun goToToday()       { _selectedDate.value = LocalDate.now() }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    // Every write below previously called consumptionRepo's/notesRepo's Room/DataStore
    // writes completely unguarded - unlike every sibling tracker ViewModel (Weight/
    // Activity/Dashboard/MealPlan/Templates all wrap theirs in runCatching), so a
    // write failure here wasn't just silent, it was an uncaught exception that would
    // crash the app (e.g. disk-full or a Room constraint violation while deleting an entry).
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    // Delete and edit wired to repository
    fun deleteEntry(id: Long) {
        viewModelScope.launch { runCatching { consumptionRepo.delete(id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    fun updateEntry(entry: DiaryEntry) {
        viewModelScope.launch { runCatching { consumptionRepo.update(entry) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    // Was `_selectedDate.map { it == LocalDate.now() }` - despite the "safe across
    // midnight" comment, that only re-evaluates when _selectedDate itself changes,
    // never from real time passing. A session left open on "today" across midnight
    // kept isToday == true forever (until the user manually navigated), disabling
    // the next-day chevron and misfiling anything logged via "+" under the now-
    // stale previous day. Same 60s-poll fix already applied to CalendarViewModel/
    // MealPlanViewModel for this exact bug class.
    private val currentDate: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val isToday: Flow<Boolean> = combine(_selectedDate, currentDate) { selected, today -> selected == today }

    // ── Day notes ─────────────────────────────────────────────────────────────
    val dayNote: Flow<String> = _selectedDate.flatMapLatest { date ->
        notesRepo.observe(date)
    }

    fun saveNote(text: String) {
        viewModelScope.launch { runCatching { notesRepo.set(_selectedDate.value, text) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    // ── Manual add: search + log ─────────────────────────────────────────────
    // Previously the only way to add a diary entry was via the barcode/photo
    // scan flow — there was no way to search and log something (a home-cooked
    // meal, a fruit, anything not barcode-scanned) directly from the Journal.
    // Same debounced search-over-observeAll() pattern as CustomFoodViewModel,
    // so results can't go stale after a save/delete the way a one-shot
    // repo.search() snapshot could.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<FoodEntry>> =
        combine(_searchQuery.debounce(200), customFoodRepo.observeAll()) { q, customs -> q to customs }
            .map { (q, customs) -> if (q.isBlank()) emptyList() else searchFoodDB(q, limit = 10, customs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Previously the FOOD_DB/custom-food search above was the only source Quick
    // Add could ever find - ScanHistoryDao.searchByName already exists, already
    // indexed, and already powers ScanHistoryScreen, but a barcoded product
    // scanned last week (with real OFF/LLM-sourced nutrition, not a FOOD_DB
    // approximation) was completely unreachable from this "+" flow: the user
    // had to leave Diary, go to History, and there was no logging affordance
    // there either.
    val scanSearchResults: StateFlow<List<ScanResult>> =
        _searchQuery.debounce(200)
            .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else scanRepo.searchHistory(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }

    /** Logs [entry] to the currently selected day (not always "today" — the user may be browsing a past date). */
    fun addEntry(entry: FoodEntry, portionG: Double, mealSlot: MealSlot) {
        viewModelScope.launch {
            val product = customFoodRepo.toProduct(entry)
            runCatching {
                consumptionRepo.log(
                    DiaryEntry(
                        date        = _selectedDate.value,
                        mealSlot    = mealSlot,
                        productName = entry.name,
                        barcode     = null,
                        portionG    = portionG,
                        nutrition   = product.nutrition,
                        source      = ScanSource.MANUAL,
                        ingredients = product.ingredients,
                    )
                )
            }.onSuccess { _searchQuery.value = "" }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /**
     * Same as [addEntry] but for a picked scan-history item — logs directly from
     * [scan]'s real product/barcode/source instead of reconstructing a lossy
     * FoodEntry-shaped Product (which has no barcode, no saturated fat/sugars,
     * and previously dropped iron/calcium/vitD/B12 too - see
     * CustomFoodRepository.toProduct()'s identical fix).
     */
    fun addEntryFromScan(scan: ScanResult, portionG: Double, mealSlot: MealSlot) {
        viewModelScope.launch {
            runCatching {
                consumptionRepo.log(
                    DiaryEntry(
                        date        = _selectedDate.value,
                        mealSlot    = mealSlot,
                        productName = scan.product.name,
                        barcode     = scan.barcode,
                        portionG    = portionG,
                        nutrition   = scan.product.nutrition,
                        source      = scan.source,
                        ingredients = scan.product.ingredients,
                    )
                )
            }.onSuccess { _searchQuery.value = "" }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /**
     * Logs a copy of every entry from the day before [selectedDate] onto the
     * currently viewed day - a MyFitnessPal/Cronometer staple this app never had:
     * repeating a typical day meant re-searching and re-portioning every item by
     * hand. Relative to the viewed day (not always literal "yesterday") so it
     * still does something sensible while browsing a past date. No-ops silently
     * if the previous day has nothing logged. logAll() writes atomically, same
     * as a template/recipe expanding to several entries.
     */
    fun copyPreviousDayMeals() {
        viewModelScope.launch {
            val previousDay = _selectedDate.value.minusDays(1)
            val previous = consumptionRepo.observeDay(previousDay).first()
            if (previous.entries.isEmpty()) return@launch
            val copies = previous.entries.map { entry ->
                entry.copy(id = 0, date = _selectedDate.value, loggedAt = LocalDateTime.now())
            }
            runCatching { consumptionRepo.logAll(copies) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
