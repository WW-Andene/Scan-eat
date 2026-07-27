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
        prefs.saveProfile(Profile(
            name = p.name,
            sex = runCatching { Sex.valueOf(p.sex) }.getOrDefault(Sex.NOT_SPECIFIED),
            ageYears = p.ageYears,
            heightCm = p.heightCm,
            weightKg = p.weightKg,
            goalWeightKg = p.goalWeightKg,
            activityLevel = runCatching { ActivityLevel.valueOf(p.activityLevel) }.getOrDefault(ActivityLevel.MODERATELY_ACTIVE),
            goal = runCatching { Goal.valueOf(p.goal) }.getOrDefault(Goal.MAINTAIN),
            diet = DietKey.fromKey(p.diet),
            allergens = p.allergens.toSet(),
            isMenstruating = p.isMenstruating,
            healthConditions = p.healthConditions.toSet(),
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
}
