package fr.scanneat.presentation.diary

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.nutrition.DayNotesRepository
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.computeMetabolics
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.nutrition.withOutdoorVitD
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.engine.scoring.healthConditionCautions
import fr.scanneat.domain.engine.scoring.dailyTargets
import fr.scanneat.domain.engine.scoring.hasMinimalProfile
import fr.scanneat.domain.engine.scoring.withKcalOverride
import fr.scanneat.domain.engine.scoring.currentPregnancyTrimester
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.DailySummary
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.domain.model.ScanSource
import fr.scanneat.presentation.common.ActionFailureViewModel
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
    private val priceRepo: PriceRepository,
    private val activityRepo: ActivityRepository,
    private val pantryRepo: fr.scanneat.data.repository.pantry.PantryRepository,
) : ActionFailureViewModel() {

    // Fix 13: selectedDate as a StateFlow — avoids stale data across midnight
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // R&D audit finding, phase 2: profileId was dead scaffolding until
    // multi-profile support (UserPreferences' Profile section) made it real -
    // every tracker call below now targets the actually-active profile
    // instead of the implicit "default" every call site previously assumed.
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")
    val useImperial: StateFlow<Boolean> = prefs.useImperialWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currencySymbol: StateFlow<String> = prefs.currencySymbol
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

    // User-requested: long-press a Journal overflow tab (Activity/Fasting/
    // Treatment/Expenses) in the "more" dropdown and drag it onto one of the
    // three always-visible header tabs (Meals/Weight/Water) to swap it in.
    // Persisted (not just session state) so the swap survives app restart,
    // same as every other Settings-level preference.
    val primaryDiaryTabsOrder: StateFlow<String> = prefs.diaryPrimaryTabsOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setPrimaryDiaryTabsOrder(csv: String) {
        viewModelScope.launch { runCatching { prefs.setDiaryPrimaryTabsOrder(csv) } }
    }

    // User-requested: "what did today's 3 eggs actually cost me" - derives an
    // estimated cost per logged entry from whatever price/weight the user
    // already entered for that same barcode in PriceEntryCard (Result screen),
    // rather than a new field/dialog of its own. Keyed by barcode -> most
    // recently logged price/kg for it (maxByOrNull loggedAt-equivalent: entries
    // arrive newest-first from PriceRepository.observeAll, so first() per group
    // is already the latest); entries with no weight (pricePerKg == null, e.g.
    // a restaurant bill with no per-kg meaning) simply don't contribute here.
    val pricePerKgByBarcode: StateFlow<Map<String, Double>> = activeProfileId
        .flatMapLatest { id -> priceRepo.observeAll(id) }
        .map { entries ->
            entries.filter { it.barcode != null && it.pricePerKg != null }
                .groupBy { it.barcode!! }
                .mapValues { (_, group) -> group.first().pricePerKg!! }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Flat-map so the observation restarts whenever the date OR the active
    // profile changes.
    //
    // User-reported: logging an outdoor activity credits vitamin D on Dashboard
    // (see VITD_OUTDOOR_UG) but this same day's Journal macro summary
    // (MacroSummaryCard) never reflected it and never even reacted to a new
    // activity log - consumptionRepo.observeDay alone has no way to know an
    // activity was logged. Combined here with activityRepo so an outdoor
    // activity both contributes vitD to this total and re-triggers the summary
    // the same way logging a food ingredient already does.
    val summary: StateFlow<DailySummary> = combine(_selectedDate, activeProfileId) { date, id -> date to id }
        .flatMapLatest { (date, id) ->
            combine(consumptionRepo.observeDay(date, id), activityRepo.observeByDate(date, id)) { day, activity ->
                day.copy(totals = day.totals.withOutdoorVitD(activity.any { it.wasOutdoors }))
            }
        }
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
    val targets: StateFlow<DailyTargets?> = combine(prefs.profile, biolismRepo.profile, prefs.isPremium) { profile, bioProfile, isPremium ->
        val base = if (hasMinimalProfile(profile)) dailyTargets(profile) else null
        // Biolism is Premium-gated (see UserPreferences.isPremium) - a non-Premium
        // user's target must never reflect a stored bioProfile, even a stale one
        // from before downgrading or before this gate existed.
        val bioTdee = if (isPremium && bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay else null
        // Previously only kcal was swapped for Biolism's tdee, leaving fat/carbs
        // computed from the old profile-only kcal - the shown macros no longer
        // summed to the kcal figure right next to them. withKcalOverride rescales
        // every kcal-derived field together so the whole row stays consistent.
        base?.let { if (bioTdee != null) it.withKcalOverride(bioTdee, profile.goal, currentPregnancyTrimester(profile)) else it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // "What would my macros be at my goal weight" - previously the Journal only
    // ever showed targets derived from the current profile weight, even for a
    // user who set a goal weight in Profile explicitly to plan around it. Only
    // emits when a goal weight is actually set and differs from the current one
    // (otherwise it would just silently duplicate the row above).
    val goalTargets: StateFlow<DailyTargets?> = combine(prefs.profile, biolismRepo.profile, prefs.isPremium) { profile, bioProfile, isPremium ->
        val goalWeight = profile.goalWeightKg
        if (!hasMinimalProfile(profile) || goalWeight == null || goalWeight == profile.weightKg) return@combine null
        val base = dailyTargets(profile, weightKgOverride = goalWeight) ?: return@combine null
        val bioTdee = if (isPremium && bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile.copy(weightKg = goalWeight))?.tdeeDay else null
        if (bioTdee != null) base.withKcalOverride(bioTdee, profile.goal, currentPregnancyTrimester(profile)) else base
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
    // entry id -> (warning text or null, "recommended" flag) - single pass shared
    // by diaryWarnings and diaryRecommended below so the allergen/diet/condition
    // checks only run once per entry per recomposition, not twice.
    private data class FoodProfileStatus(val warning: String?, val recommended: Boolean)

    private val diaryFoodStatus: StateFlow<Map<Long, FoodProfileStatus>> = combine(summary, prefs.profile, language) { s, profile, lang ->
        s.entries.associate { entry ->
            val product = entry.toCheckProduct()
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(product, profile.diet, lang)
            // Same profile.healthConditions the score itself reads (diabetes/
            // hypertension/ibs/crohn_ibd/... - see healthConditionCautions()) -
            // previously only allergens/diet ever resurfaced here, so a logged
            // product that tripped one of the user's health conditions never
            // showed anything once it left the Result screen.
            val conditionHits = healthConditionCautions(product, profile.healthConditions, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            conditionHits.firstOrNull()?.let { parts += it }
            val warning = if (parts.isEmpty()) null else parts.joinToString(" · ")
            // Only surface a positive "recommended" badge when the user has
            // actually declared something to be recommended against (allergens,
            // a diet, or a health condition) - otherwise every entry would show
            // it, which communicates nothing.
            val hasDeclaredProfile = profile.allergens.isNotEmpty() || profile.diet != fr.scanneat.domain.engine.scoring.DietKey.NONE || profile.healthConditions.isNotEmpty()
            entry.id to FoodProfileStatus(warning, recommended = warning == null && hasDeclaredProfile)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * entry id -> short warning, e.g. "Allergen: gluten" or a diet-compliance
     * reason - same checkUserAllergens()/checkDiet() pattern already used live
     * by RecipesViewModel.recipeWarnings/GroceryViewModel/TemplatesViewModel.
     * Previously nothing in the Diary ever ran either check: a user could log
     * a product containing one of their declared allergens and see the
     * warning on Result once, then never again anywhere in the Journal.
     */
    val diaryWarnings: StateFlow<Map<Long, String>> = diaryFoodStatus
        .map { statuses -> statuses.mapNotNull { (id, status) -> status.warning?.let { id to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Entry ids with no allergen/diet/condition issue, when the user has declared at least one of those. */
    val diaryRecommended: StateFlow<Set<Long>> = diaryFoodStatus
        .map { statuses -> statuses.filterValues { it.recommended }.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun goToPreviousDay() { _selectedDate.value = _selectedDate.value.minusDays(1) }
    fun goToNextDay()     { _selectedDate.value = _selectedDate.value.plusDays(1) }
    fun goToToday()       { _selectedDate.value = LocalDate.now() }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    // Every write below previously called consumptionRepo's/notesRepo's Room/DataStore
    // writes completely unguarded - unlike every sibling tracker ViewModel (Weight/
    // Activity/Dashboard/MealPlan/Templates all wrap theirs in runCatching), so a
    // write failure here wasn't just silent, it was an uncaught exception that would
    // crash the app (e.g. disk-full or a Room constraint violation while deleting an entry).
    // R&D audit finding: Fasting and the Diary had zero cross-reference - see
    // ConsumptionRepository.log's own doc comment.
    private val _loggedDuringFast = MutableStateFlow(false)
    val loggedDuringFast: StateFlow<Boolean> = _loggedDuringFast.asStateFlow()
    fun clearLoggedDuringFast() { _loggedDuringFast.value = false }

    // Delete and edit wired to repository
    fun deleteEntry(id: Long) {
        guardedLaunch { consumptionRepo.delete(id) }
    }

    /** Re-creates a deleted entry (used by the "Undo" snackbar action) - mirrors
     *  WeightViewModel.restore()'s identical pattern for the same delete-recovery gap. */
    fun restore(entry: DiaryEntry) {
        guardedLaunch { consumptionRepo.log(entry) }
    }

    fun updateEntry(entry: DiaryEntry) {
        guardedLaunch { consumptionRepo.update(entry) }
    }

    /**
     * User-reported: tapping a logged entry only ever opened the portion-edit
     * dialog - there was no way to see the actual product's Result screen
     * (full audit, ingredients, warnings) from the Diary at all. DiaryEntry
     * itself carries no scan_history row id (it's a denormalized snapshot, see
     * its own doc comment), so this resolves one by barcode instead - the same
     * getCachedByBarcode() lookup ScanRepository already exposes, matching
     * ScanHistoryCard's onItemClick(scan.dbId) pattern elsewhere in the app.
     * Returns null (caller falls back to onEdit) for a FOOD_DB/custom-food
     * quick-added entry with no barcode, or one whose barcode was never
     * actually scanned/cached (e.g. imported via backup on a different device).
     */
    suspend fun findScanIdForEntry(entry: DiaryEntry): Long? {
        val barcode = entry.barcode ?: return null
        val scan = scanRepo.getCachedByBarcode(barcode, activeProfileId.value, language.value) ?: return null
        return scan.dbId.takeIf { it > 0 }
    }

    /**
     * User-reported: there was no way to add/correct a logged entry's price
     * from Diary at all - [pricePerKgByBarcode] only ever displayed whatever
     * was already logged elsewhere (Result screen's PriceEntryCard/Expenses),
     * with no write path back to it here. Logs a new PriceRepository entry for
     * this entry's barcode, same shape PriceEntryCard itself uses - price/kg
     * display is barcode-keyed (see pricePerKgByBarcode's own doc comment),
     * not tied to one specific diary row, so this is a new price-history entry
     * rather than an edit of an existing one, exactly like scanning a fresh
     * price tag for the same product would produce.
     */
    fun savePrice(entry: DiaryEntry, priceEuros: Double, weightG: Double?) {
        val barcode = entry.barcode ?: return
        guardedLaunch {
            priceRepo.log(
                date = LocalDate.now(),
                productName = entry.productName,
                barcode = barcode,
                category = entry.category,
                priceEuros = priceEuros,
                weightG = weightG,
                profileId = activeProfileId.value,
            )
        }
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
    val dayNote: Flow<String> = combine(_selectedDate, activeProfileId) { date, id -> date to id }.flatMapLatest { (date, id) ->
        notesRepo.observe(date, id)
    }

    fun saveNote(text: String) {
        guardedLaunch { notesRepo.set(_selectedDate.value, text, activeProfileId.value) }
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
        combine(_searchQuery.debounce(200), activeProfileId.flatMapLatest { id -> customFoodRepo.observeAll(id) }) { q, customs -> q to customs }
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
        combine(_searchQuery.debounce(200), activeProfileId) { q, id -> q to id }
            .flatMapLatest { (q, id) -> if (q.isBlank()) flowOf(emptyList()) else scanRepo.searchHistory(q, id) }
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
                        category    = product.category,
                        profileId   = activeProfileId.value,
                    )
                )
            }.onSuccess { loggedDuringFast -> _searchQuery.value = ""; if (loggedDuringFast) _loggedDuringFast.value = true }
                .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
        }
    }

    /** Same "Repas"/"Garde-manger" destination split as ResultScreen's own
     *  LogSheet call - see FoodSearchViewModel.confirmLogWithDestinations. */
    fun addEntryWithDestinations(entry: FoodEntry, portionG: Double, mealSlot: MealSlot, destinations: Set<fr.scanneat.presentation.result.LogDestination>) {
        viewModelScope.launch {
            val product = customFoodRepo.toProduct(entry)
            runCatching {
                var loggedDuringFast = false
                if (fr.scanneat.presentation.result.LogDestination.REPAS in destinations) {
                    loggedDuringFast = consumptionRepo.log(
                        DiaryEntry(
                            date        = _selectedDate.value,
                            mealSlot    = mealSlot,
                            productName = entry.name,
                            barcode     = null,
                            portionG    = portionG,
                            nutrition   = product.nutrition,
                            source      = ScanSource.MANUAL,
                            ingredients = product.ingredients,
                            category    = product.category,
                            profileId   = activeProfileId.value,
                        )
                    )
                }
                if (fr.scanneat.presentation.result.LogDestination.GARDE_MANGER in destinations) {
                    pantryRepo.addOrUpdate(
                        name = entry.name, barcode = null, category = product.category,
                        quantity = portionG, unit = fr.scanneat.data.repository.pantry.PantryUnit.GRAMS,
                        expiryDate = null, profileId = activeProfileId.value,
                    )
                }
                loggedDuringFast
            }.onSuccess { loggedDuringFast -> _searchQuery.value = ""; if (loggedDuringFast) _loggedDuringFast.value = true }
                .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
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
                        category    = scan.product.category,
                        profileId   = activeProfileId.value,
                    )
                )
            }.onSuccess { loggedDuringFast -> _searchQuery.value = ""; if (loggedDuringFast) _loggedDuringFast.value = true }
                .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
        }
    }

    /** Same "Repas"/"Garde-manger" destination split as ResultScreen's own
     *  LogSheet call - see FoodSearchViewModel.confirmLogWithDestinations. */
    fun addEntryFromScanWithDestinations(scan: ScanResult, portionG: Double, mealSlot: MealSlot, destinations: Set<fr.scanneat.presentation.result.LogDestination>) {
        viewModelScope.launch {
            runCatching {
                var loggedDuringFast = false
                if (fr.scanneat.presentation.result.LogDestination.REPAS in destinations) {
                    loggedDuringFast = consumptionRepo.log(
                        DiaryEntry(
                            date        = _selectedDate.value,
                            mealSlot    = mealSlot,
                            productName = scan.product.name,
                            barcode     = scan.barcode,
                            portionG    = portionG,
                            nutrition   = scan.product.nutrition,
                            source      = scan.source,
                            ingredients = scan.product.ingredients,
                            category    = scan.product.category,
                            profileId   = activeProfileId.value,
                        )
                    )
                }
                if (fr.scanneat.presentation.result.LogDestination.GARDE_MANGER in destinations) {
                    pantryRepo.addOrUpdate(
                        name = scan.product.name, barcode = scan.barcode, category = scan.product.category,
                        quantity = scan.product.weightG ?: portionG, unit = fr.scanneat.data.repository.pantry.PantryUnit.GRAMS,
                        expiryDate = null, profileId = activeProfileId.value,
                    )
                }
                loggedDuringFast
            }.onSuccess { loggedDuringFast -> _searchQuery.value = ""; if (loggedDuringFast) _loggedDuringFast.value = true }
                .onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
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
            val previous = consumptionRepo.observeDay(previousDay, activeProfileId.value).first()
            if (previous.entries.isEmpty()) return@launch
            val copies = previous.entries.map { entry ->
                entry.copy(id = 0, date = _selectedDate.value, loggedAt = LocalDateTime.now())
            }
            runCatching { consumptionRepo.logAll(copies) }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
        }
    }
}
