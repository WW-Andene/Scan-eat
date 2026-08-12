package fr.scanneat.presentation.dashboard

import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.domain.engine.health.OverhydrationWarning
import fr.scanneat.domain.engine.health.OvertrainingWarning
import fr.scanneat.domain.engine.health.checkDailyOvertraining
import fr.scanneat.domain.engine.health.checkOverhydration
import fr.scanneat.domain.engine.health.detectActivityRelevantDrugClasses
import fr.scanneat.domain.engine.medication.checkFoodDrugInteractions
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.Profile
import fr.scanneat.presentation.medication.InteractionWarning
import fr.scanneat.presentation.medication.detectInteractions

/**
 * User-requested "centre de vigilance" - the various safety checks built
 * this session (activity overtraining, hydration over-consumption,
 * medication drug-drug interactions, medication-food interactions) each only
 * ever surfaced on their own tab or scan sheet - nothing on Dashboard pulled
 * them together into one place a user could check at a glance for "is
 * anything about today worth a second look".
 *
 * Pure, non-composable (mirrors DashboardHeavyState.kt's own style) so
 * DashboardViewModel can build this from repo reads without threading
 * Context/stringResource down into the ViewModel layer - the actual message
 * text is built in DashboardScreen's Composable layer instead, same
 * separation MedicationViewModel's own doc comment already establishes for
 * InteractionWarning ("a ViewModel has no stringResource()").
 */
sealed class DashboardSafetyWarning {
    data class Overtraining(val warning: OvertrainingWarning) : DashboardSafetyWarning()
    data class Overhydration(val warning: OverhydrationWarning) : DashboardSafetyWarning()
    data class MedicationInteraction(val warning: InteractionWarning) : DashboardSafetyWarning()
    /** Already fully localized (see checkFoodDrugInteractions's own `lang`
     *  param) - built from today's logged Diary entries, not a single
     *  scanned product like ProductHints.medicationRisks. */
    data class MedicationFood(val message: String) : DashboardSafetyWarning()
    /** Pantry items expiring soon or already past their date - previously only
     *  ever shown inside the Pantry screen's own banner, never surfaced here
     *  alongside every other "worth a second look today" check. */
    data class PantryExpiry(val itemNames: List<String>) : DashboardSafetyWarning()
}

fun buildDashboardSafetyWarnings(
    todayActivity: List<ActivityEntry>,
    profile: Profile,
    activeMedicationNames: List<String>,
    hydrationMl: Int,
    hydrationGoalMl: Int,
    todayDiaryEntries: List<DiaryEntry>,
    lang: String,
    expiringPantryItemNames: List<String> = emptyList(),
): List<DashboardSafetyWarning> {
    val activeMedNamesSet = activeMedicationNames.toSet()
    // Same activity-relevant drug classes (beta-blocker/anticoagulant/diuretic/
    // antidiabetic) ActivityViewModel already derives for its own inline warning -
    // recomputed here from the same active-medication list rather than threading
    // ActivityViewModel's own StateFlow through, since this is a different
    // ViewModel with its own repo reads.
    val drugClasses = detectActivityRelevantDrugClasses(activeMedicationNames)

    // Grouped by type - checkDailyOvertraining's own contract is "this type's
    // own cumulative minutes today", not the day's grand total across types.
    val overtraining = todayActivity.groupBy { it.type }
        .mapNotNull { (type, entries) ->
            checkDailyOvertraining(type, entries.sumOf { it.minutes }, profile.ageYears, profile.healthConditions, drugClasses)
        }
        .map { DashboardSafetyWarning.Overtraining(it) }

    val overhydration = checkOverhydration(hydrationMl, hydrationGoalMl)
        ?.let { listOf(DashboardSafetyWarning.Overhydration(it)) } ?: emptyList()

    val medInteractions = detectInteractions(activeMedicationNames).map { DashboardSafetyWarning.MedicationInteraction(it) }

    // distinct() - several logged foods triggering the identical caution text
    // (e.g. two vitamin-K-rich vegetables logged today) would otherwise repeat
    // the same line.
    val medFood = todayDiaryEntries
        .flatMap { entry -> checkFoodDrugInteractions(entry.productName, entry.ingredients, activeMedNamesSet, lang) }
        .distinct()
        .map { DashboardSafetyWarning.MedicationFood(it) }

    val pantryExpiry = if (expiringPantryItemNames.isEmpty()) emptyList()
        else listOf(DashboardSafetyWarning.PantryExpiry(expiringPantryItemNames))

    return overtraining + overhydration + medInteractions + medFood + pantryExpiry
}
