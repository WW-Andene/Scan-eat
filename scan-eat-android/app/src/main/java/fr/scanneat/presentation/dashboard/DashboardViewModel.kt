package fr.scanneat.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// CalorieBalance, DashboardUiState, and OtherTrackersSnapshot moved to
// DashboardModels.kt (same package) - pure data shapes, kept separate from
// the ViewModel logic that builds/emits them.

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val consumptionRepo: ConsumptionRepository,
    private val weightRepo: WeightRepository,
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,
    private val biolismRepo: BiolismRepository,
    private val activityRepo: ActivityRepository,
    private val customFoodRepo: CustomFoodRepository,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
    private val medicationRepo: MedicationRepository,
) : ViewModel() {

    // LocalDate.now() captured once at property-init time (the previous shape
    // of this ViewModel) would keep every combine() below observing the day
    // this ViewModel happened to be constructed on forever - a session left
    // open across midnight (the single most common way to use a home-screen
    // Dashboard) silently kept showing yesterday's totals/streaks/rollups,
    // and a diary entry logged after midnight wouldn't even count toward
    // "today". Polling + distinctUntilChanged re-subscribes every date-scoped
    // query to the new day exactly when it rolls over, same fix already
    // applied to HydrationViewModel/DiaryViewModel/ActivityViewModel.
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    // Combine 5 flows — the 30-day range is now a Flow itself, so
    // DashboardViewModel never issues a suspend DB call on every upstream tick.
    // recentScans is deliberately NOT in this combine: it changes on every
    // single new scan, and merging it here would re-run the entire 30-day
    // weekly rollup / gap-closer / Biolism TDEE recomputation on every scan
    // even though none of that depends on the scan-history list. It's merged
    // in separately below, as a cheap .copy().
    private val heavyState: StateFlow<DashboardUiState> = today.flatMapLatest { date ->
        combine(
            consumptionRepo.observeDay(date),
            consumptionRepo.observeRange(date.minusDays(30), date),
            prefs.profile,
            weightRepo.observeLatest(),
            biolismRepo.profile,
        ) { todayData, allEntries, profile, _, bioProfile ->
            // weightRepo.observeLatest() (4th param, ignored) is a trigger-only input -
            // it makes this combine re-run when weight changes, but wSummary below
            // is fetched fresh via weightRepo.summarize() rather than threaded
            // through here. Was observeAll() (the full, unbounded weight_log table)
            // until a performance audit flagged it as a full-table fetch+map on
            // every single unrelated Dashboard recompute purely to get Room's
            // table-level invalidation signal - observeLatest() gets the identical
            // re-trigger from a 1-row query instead. Kotlin's typed 5-flow combine()
            // overload removes the Array<*>-indexed unchecked casts the previous
            // form needed.
            Quad(todayData, allEntries, profile, bioProfile)
        }
            // Nested rather than folded into the 5-way combine above (which is
            // already at Kotlin's typed-overload limit) - closeTheGap()/
            // chronicNutrientGaps() below documented their own foodDB param as
            // "FOOD_DB + custom foods" but this ViewModel only ever passed bare
            // FOOD_DB, so a user's own custom foods (e.g. "Lentilles maison",
            // high in iron) could never be suggested to close a real deficit.
            .combine(customFoodRepo.observeAll()) { quad, customFoods -> quad to customFoods }
            .flatMapLatest { (quad, customFoods) ->
                val (todayData, allEntries, profile, bioProfile) = quad
                val foodDb = FOOD_DB + customFoods
                // Full computation extracted to buildHeavyDashboardState() in
                // DashboardHeavyState.kt (same package) - moved verbatim, this
                // flatMapLatest just wires its inputs/output.
                flow {
                    // Biolism is Premium-gated (see UserPreferences.isPremium) - a
                    // non-Premium user's TDEE/macro targets must come from the plain
                    // profile estimate only, even if a bioProfile from before
                    // downgrading (or before this gate existed) is still stored.
                    val gatedBioProfile = if (prefs.isPremium.first()) bioProfile else BiolismProfile()
                    emit(
                        buildHeavyDashboardState(
                            date = date,
                            todayData = todayData,
                            allEntries = allEntries,
                            profile = profile,
                            bioProfile = gatedBioProfile,
                            foodDb = foodDb,
                            consumptionRepo = consumptionRepo,
                            weightRepo = weightRepo,
                            activityRepo = activityRepo,
                            fastingRepo = fastingRepo,
                            hydrationRepo = hydrationRepo,
                        )
                    )
                }
            }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val state: StateFlow<DashboardUiState> = today.flatMapLatest { date ->
        combine(
            heavyState, scanRepo.observeHistory(limit = 20), activityRepo.observeByDate(date),
        ) { s, scans, activity ->
            // In-memory only, both lists already loaded for other purposes above - no
            // new DB query. Matched by the same "barcode when present, else lowercased
            // name" identity ScanRepository.matchKeyFor uses internally for its own
            // score-history dedup.
            fun DiaryEntry.matchKey() = barcode ?: productName.lowercase()
            fun ScanResult.matchKey() = barcode ?: product.name.lowercase()
            val loggedToday = s.todayEntries.mapTo(mutableSetOf()) { it.matchKey() }
            val neverLogged = scans.filter { scan ->
                Instant.ofEpochMilli(scan.scannedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date &&
                    scan.matchKey() !in loggedToday
            }
            s.copy(
                recentScans      = scans,
                neverLoggedScans = neverLogged,
                calorieBalance   = s.calorieBalance?.copy(exerciseKcal = activity.sumOf { it.kcalBurned }),
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // In-app language can differ from the device locale — WeeklyBarsCard's day-of-week
    // labels must follow it explicitly, same as DiaryScreen/WeightScreen/MealPlanScreen.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Dashboard's WeightCard previously always showed kg via a hardcoded string
    // resource, ignoring this same metric/imperial toggle the Weight tab itself
    // already respects for its own headline weight display (WeightScreen's
    // dispWeight()) — a user in imperial mode saw a different unit for the exact
    // same weight value depending on which screen showed it.
    val useImperialWeight: StateFlow<Boolean> = prefs.useImperialWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * dbId -> short personal-safety warning, same checkUserAllergens()/
     * checkDiet() pattern as DiaryViewModel.diaryWarnings and
     * ScanHistoryViewModel.historyWarnings. Dashboard's recentScans card
     * (ScanHistoryCard) previously showed a bare grade badge with no trace of
     * an allergen/diet conflict the Result screen had already flagged for
     * that exact product — the very first screen a user sees after opening
     * the app, silently disagreeing with the personalized warning they saw
     * moments earlier when they scanned it.
     */
    val recentScanWarnings: StateFlow<Map<Long, String>> = combine(
        scanRepo.observeHistory(limit = 20), prefs.profile, language,
    ) { scans, profile, lang ->
        scans.mapNotNull { scan ->
            if (scan.dbId <= 0) return@mapNotNull null
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(scan.product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(scan.product, profile.diet, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            if (parts.isEmpty()) null else scan.dbId to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Dashboard previously showed nutrition + weight only - a user relying on it as
    // their single daily view had no idea they were behind on water, mid-fast, or had
    // an unlogged dose due, despite all three already being tracked elsewhere (behind
    // Journal's Water/Fasting/Treatment tabs). Kept as its own StateFlow rather than
    // folded into the heavy combine above - these are all today-only cheap reads with
    // no dependency on the 30-day window that combine recomputes on every diary edit.
    val otherTrackers: StateFlow<OtherTrackersSnapshot> = today.flatMapLatest { date ->
        combine(
            hydrationRepo.observe(date),
            fastingRepo.state,
            medicationRepo.observeAll(),
            medicationRepo.observeLogByDate(date),
            prefs.profile,
        ) { hydrationMl, fasting, meds, todayLogs, profile ->
            val activeMeds = meds.filter { it.active }
            val takenIds = todayLogs.map { it.medicationId }.toSet()
            OtherTrackersSnapshot(
                hydrationMl     = hydrationMl,
                hydrationGoalMl = hydrationRepo.goalMl(profile.sex, profile.activityLevel, profile.healthConditions),
                fastingActive   = fasting?.takeIf { it.isActive },
                medsTakenCount  = activeMeds.count { it.id in takenIds },
                medsActiveCount = activeMeds.size,
            )
        }.combine(
            // Same window-capping trap logStreakDays' own doc comment warns about -
            // getAllLoggedDates() (not a fixed observeRange window) is the only safe
            // input, same as the diary-logging streak above.
            flow { emit(activityRepo.getAllLoggedDates()) },
        ) { snapshot, workoutDates -> snapshot.copy(workoutStreak = logStreakDays(workoutDates, date)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OtherTrackersSnapshot())

    // Gap-suggestion / never-logged-scan logging state+actions extracted to
    // DashboardGapLoggingDelegate in DashboardGapLogging.kt (same package) -
    // moved verbatim; the public surface below just forwards to it so no
    // external caller (GapCloserCard/NeverLoggedScansCard/DashboardScreen) needs to change.
    private val gapLogging = DashboardGapLoggingDelegate(viewModelScope, consumptionRepo, customFoodRepo)

    /** Non-null briefly after a successful log, for a one-shot confirmation snackbar. */
    val gapLoggedName: StateFlow<String?> get() = gapLogging.gapLoggedName

    /** True briefly after a failed log, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> get() = gapLogging.actionFailed
    fun clearActionFailed() = gapLogging.clearActionFailed()

    fun logGapSuggestion(suggestion: GapSuggestion) = gapLogging.logGapSuggestion(suggestion)

    fun clearGapLoggedMessage() = gapLogging.clearGapLoggedMessage()

    /** Logs a never-logged scan straight from its real product/barcode/source — see [DashboardUiState.neverLoggedScans]. */
    fun logNeverLoggedScan(scan: ScanResult, portionG: Double, mealSlot: MealSlot) =
        gapLogging.logNeverLoggedScan(scan, portionG, mealSlot)

    // Local tuple to carry 4 values cleanly through flatMapLatest
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
