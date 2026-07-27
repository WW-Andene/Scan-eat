package fr.scanneat.presentation.dashboard

import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.result.defaultMealForHour
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Delegate holding [DashboardViewModel]'s gap-suggestion / never-logged-scan logging
 * state and actions - extracted verbatim out of the ViewModel (same package) so its
 * own re-entrancy guard, snackbar StateFlows, and the two log* functions live
 * together. [DashboardViewModel] forwards its public gapLoggedName/actionFailed/
 * logGapSuggestion/logNeverLoggedScan/clearActionFailed/clearGapLoggedMessage
 * surface straight through to this delegate, so external callers see no change.
 */
internal class DashboardGapLoggingDelegate(
    private val scope: CoroutineScope,
    private val consumptionRepo: ConsumptionRepository,
    private val customFoodRepo: CustomFoodRepository,
) {
    // ── Gap-closer suggestions: previously a dead end ────────────────────────
    // GapCloserCard rendered suggestion chips (e.g. "Lentilles, 80 g") with no
    // way to act on them — tapping did nothing. GapSuggestion.name is always
    // an exact FoodEntry.name from the same merged FOOD_DB+custom-foods list
    // closeTheGap/chronicNutrientGaps were given, so it's looked up directly
    // rather than fuzzy-searched.
    private val _gapLoggedName = MutableStateFlow<String?>(null)
    /** Non-null briefly after a successful log, for a one-shot confirmation snackbar. */
    val gapLoggedName: StateFlow<String?> = _gapLoggedName.asStateFlow()

    // logGapSuggestion/logNeverLoggedScan both guarded their Room write with runCatching
    // to avoid crashing on a disk-full/constraint failure, but neither had a failure
    // path at all - a save that failed produced zero visible effect, so the user
    // couldn't tell their tap hadn't actually logged anything.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed log, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    // logGapSuggestion/logNeverLoggedScan both previously had no re-entrancy guard - a fast
    // double-tap (or a second tap landing before the first log's suspend DB write finished)
    // fired the whole function twice, silently creating two duplicate diary entries for the
    // same suggestion/scan. Keyed by the same suggestion.name/scan matchKey a tap is already
    // logically about, so a duplicate tap on the SAME item while it's still in flight is a
    // no-op, but a tap on a DIFFERENT item is never blocked by an unrelated one in progress.
    private val loggingKeys = mutableSetOf<String>()

    fun logGapSuggestion(suggestion: GapSuggestion) {
        if (!loggingKeys.add(suggestion.name)) return
        scope.launch {
            try {
                // Previously only ever searched raw FOOD_DB - a suggestion built from a
                // user's own custom food (now possible since closeTheGap/chronicNutrientGaps
                // are given FOOD_DB + custom foods) would silently no-op here otherwise.
                val food = (FOOD_DB + customFoodRepo.observeAll().first()).find { it.name == suggestion.name } ?: return@launch
                // Unguarded suspend DB write previously crashed the app on any Room insert
                // failure (disk-full, constraint violation) — ResultViewModel.log() guards
                // its equivalent call the same way.
                runCatching {
                    consumptionRepo.log(
                        DiaryEntry(
                            date        = LocalDate.now(),
                            mealSlot    = defaultMealForHour(LocalTime.now().hour),
                            productName = food.name,
                            barcode     = null,
                            portionG    = suggestion.grams.toDouble(),
                            nutrition   = food.toProduct(suggestion.grams.toDouble()).nutrition,
                            source      = ScanSource.MANUAL,
                        )
                    )
                }.onSuccess { _gapLoggedName.value = food.name }
                    .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
            } finally {
                loggingKeys.remove(suggestion.name)
            }
        }
    }

    fun clearGapLoggedMessage() { _gapLoggedName.value = null }

    /** Logs a never-logged scan straight from its real product/barcode/source — see [DashboardUiState.neverLoggedScans]. */
    fun logNeverLoggedScan(scan: ScanResult, portionG: Double, mealSlot: MealSlot) {
        val key = "scan:" + (scan.barcode ?: scan.product.name.lowercase())
        if (!loggingKeys.add(key)) return
        scope.launch {
            try {
                runCatching {
                    consumptionRepo.log(
                        DiaryEntry(
                            date        = LocalDate.now(),
                            mealSlot    = mealSlot,
                            productName = scan.product.name,
                            barcode     = scan.barcode,
                            portionG    = portionG,
                            nutrition   = scan.product.nutrition,
                            source      = scan.source,
                            ingredients = scan.product.ingredients,
                        )
                    )
                }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
            } finally {
                loggingKeys.remove(key)
            }
        }
    }
}
