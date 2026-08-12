package fr.scanneat.data.repository.backup

import fr.scanneat.data.backup.BackupBundle
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import java.time.LocalDate

// ============================================================================
// DATASTORE-BACKED RESTORE — extracted verbatim out of BackupRepository.kt,
// the cohesive "apply the non-Room half of a backup bundle" concern
// (profile/settings/reminders/fasting/hydration/notes/meal plan/grocery/
// biolism/manual grocery). Extension function on BackupRepository since it
// needs several of its private repo-dependency fields, widened from private
// to internal for this file to reach (prefs/remindersRepo/fastingRepo/
// hydrationRepo/dayNotesRepo/mealPlanRepo/groceryCheckedRepo/biolismRepo/
// manualGroceryRepo) - only called internally from importFromJson() (same
// package), no external caller changes.
// ============================================================================

/**
 * Restores every DataStore-backed data source (including, since v4, Biolism's
 * own "biolism_prefs" DataStore) from [bundle]. This is separate storage from
 * the Room tables importFromJson() restores inside its own transaction, and
 * applies right after, best-effort per field.
 */
internal suspend fun BackupRepository.restoreDataStoreData(bundle: BackupBundle) {
    bundle.profile?.let { p ->
        // Restored raw before this fix, unlike WeightEntry.weightKg (clamped to
        // WeightRepository.log()'s own require(0, 400] just above in
        // importFromJson) - a hand-edited or corrupted backup's weightKg/
        // heightCm could land in the profile verbatim, bypassing
        // ProfileScreen's own coerceIn(20.0, 400.0) entry bound and silently
        // producing Infinity/NaN downstream in BMI/TDEE/protein-target math
        // (see BmrCalculations.kt's own non-positive guards).
        prefs.saveProfile(Profile(
            name = p.name,
            sex = runCatching { Sex.valueOf(p.sex) }.getOrDefault(Sex.NOT_SPECIFIED),
            ageYears = p.ageYears,
            heightCm = p.heightCm?.coerceIn(50.0, 300.0),
            weightKg = p.weightKg?.coerceIn(20.0, 400.0),
            goalWeightKg = p.goalWeightKg?.coerceIn(20.0, 400.0),
            activityLevel = runCatching { ActivityLevel.valueOf(p.activityLevel) }.getOrDefault(ActivityLevel.MODERATELY_ACTIVE),
            goal = runCatching { Goal.valueOf(p.goal) }.getOrDefault(Goal.MAINTAIN),
            diet = DietKey.fromKey(p.diet),
            allergens = p.allergens.toSet(),
            isMenstruating = p.isMenstruating,
            healthConditions = p.healthConditions.toSet(),
            pregnancyStartDate = p.pregnancyStartDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
        ))
    }
    bundle.settings?.let { s ->
        prefs.setApiMode(ApiMode.fromKey(s.apiMode))
        prefs.setServerUrl(s.serverUrl)
        prefs.setLanguage(s.language)
        prefs.setTheme(s.theme)
        prefs.setDyslexicFont(s.dyslexicFont)
        prefs.setColorblindMode(s.colorblindMode)
        prefs.setUseImperialWeight(s.useImperialWeight)
        prefs.setOnboardingComplete(s.onboardingComplete)
        prefs.setAnimatedBackground(s.animatedBackground)
        prefs.setBiolismAdvancedView(s.biolismAdvancedView)
        prefs.setActivityBestStreak(s.activityBestStreak)
        prefs.setBudgetWeeklyEuros(s.budgetWeeklyEuros)
        prefs.setBudgetPerMealEuros(s.budgetPerMealEuros)
    }
    // restoreAll writes every ReminderSettings field in one transaction — the
    // previous piecemeal setBreakfast/setLunch/setDinner/setHydration/setWeight
    // calls silently dropped snack, all four custom labels, hydration/weight
    // custom-time reminders, and every user-created custom reminder despite
    // exportToJson serializing all of them.
    bundle.reminderSettings?.let { r -> remindersRepo.restoreAll(r) }
    fastingRepo.importForBackup(bundle.fastingActiveStartMs, bundle.fastingActiveTargetHours, bundle.fastingHistory)
    hydrationRepo.importAll(bundle.hydration.mapNotNull { entry ->
        runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it to entry.ml }
    })
    dayNotesRepo.importAll(bundle.dayNotes.mapNotNull { entry ->
        runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it to entry.text }
    })
    bundle.mealPlanRaw?.let { mealPlanRepo.importRaw(it) }
    groceryCheckedRepo.restoreAll(bundle.groceryCheckedKeys.toSet())
    bundle.biolism?.let { biolismRepo.importForBackup(it) }
    manualGroceryRepo.importAll(bundle.manualGroceryItems)
    loyaltyCardRepo.importAll(bundle.loyaltyCards)
}
