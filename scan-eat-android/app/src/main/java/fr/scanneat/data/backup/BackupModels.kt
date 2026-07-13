package fr.scanneat.data.backup

import fr.scanneat.data.local.db.activity.ActivityEntity
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.data.local.db.medication.MedicationEntity
import fr.scanneat.data.local.db.recipe.RecipeEntity
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.db.template.MealTemplateEntity
import fr.scanneat.data.local.db.weight.WeightEntity
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.data.repository.planning.ManualGroceryItem
import fr.scanneat.data.repository.reminders.ReminderSettings

// ============================================================================
// BACKUP BUNDLE — full local export/import of the user data.
//
// Covers the 7 @Database entities (scan history, diary, custom foods, weight,
// activity, meal templates, recipes) plus, since format v2, the
// DataStore-backed data a restore-on-a-new-device previously silently lost:
// profile, app settings, reminder settings, fasting (active session +
// history), hydration log, day notes, and the meal plan. Since v3, also the
// grocery-list checked-off state (its own DataStore file, added alongside
// the check-off feature — easy to forget since it isn't a @Database entity).
// Since v4, also Biolism's own DataStore (biolism_prefs): its profile override,
// onboarding flag, session timer state, manual HR, and workout session history
// — previously silently lost on restore-to-a-new-device just like the v2 data.
// Since v5, also the "Traitement" (medication) list — an 8th @Database entity
// that had zero presence here despite being active health data (name, dosage,
// reminder schedule) with no other persistence path — and manually-added
// grocery items (e.g. "Save to..." from a scanned product), also previously
// silently lost on backup/restore.
//
// Deliberately excludes the Groq API key from SettingsBackup — a backup file
// shared for debugging or support must not leak a credential.
//
// version bumps whenever a field is added/removed/retyped, so importFromJson
// can refuse (rather than silently corrupt) a bundle from an incompatible
// future or ancient export. All v2+ fields default to null/empty so an older
// file (which has none of them) still parses cleanly.
// ============================================================================

const val BACKUP_FORMAT_VERSION = 5

data class ProfileBackup(
    val name: String,
    val sex: String,
    val ageYears: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val goalWeightKg: Double?,
    val activityLevel: String,
    val goal: String,
    val diet: String,
    val allergens: List<String>,
    val isMenstruating: Boolean,
)

data class SettingsBackup(
    val apiMode: String,
    val serverUrl: String,
    val language: String,
    val theme: String,
    val dyslexicFont: Boolean,
    val colorblindMode: String,
)

data class HydrationEntryBackup(val date: String, val ml: Int)

data class DayNoteBackup(val date: String, val text: String)

data class BackupBundle(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val exportedAtMs: Long,
    val appVersionName: String,
    val scanHistory: List<ScanHistoryEntity>,
    val consumption: List<ConsumptionEntity>,
    val customFoods: List<CustomFoodEntity>,
    val weights: List<WeightEntity>,
    val activities: List<ActivityEntity>,
    val mealTemplates: List<MealTemplateEntity>,
    val recipes: List<RecipeEntity>,
    val profile: ProfileBackup? = null,
    val settings: SettingsBackup? = null,
    val reminderSettings: ReminderSettings? = null,
    val fastingActiveStartMs: Long? = null,
    val fastingActiveTargetHours: Int? = null,
    val fastingHistory: List<FastCompletion> = emptyList(),
    val hydration: List<HydrationEntryBackup> = emptyList(),
    val dayNotes: List<DayNoteBackup> = emptyList(),
    // Meal plan is stored as its own private pipe-delimited format
    // (MealPlanRepository.serialize/deserialize) — kept as a raw string here
    // rather than a structured field, since Moshi has no built-in polymorphic
    // adapter for MealPlanSlot's sealed subclasses.
    val mealPlanRaw: String? = null,
    val groceryCheckedKeys: List<String> = emptyList(),
    val biolism: BiolismRepository.BiolismBackupData? = null,
    val medications: List<MedicationEntity> = emptyList(),
    val manualGroceryItems: List<ManualGroceryItem> = emptyList(),
)

data class BackupSummary(
    val scanHistory: Int,
    val consumption: Int,
    val customFoods: Int,
    val weights: Int,
    val activities: Int,
    val mealTemplates: Int,
    val recipes: Int,
    val medications: Int = 0,
) {
    val total: Int get() = scanHistory + consumption + customFoods + weights + activities + mealTemplates + recipes + medications

    companion object {
        fun from(bundle: BackupBundle) = BackupSummary(
            scanHistory   = bundle.scanHistory.size,
            consumption   = bundle.consumption.size,
            customFoods   = bundle.customFoods.size,
            weights       = bundle.weights.size,
            activities    = bundle.activities.size,
            mealTemplates = bundle.mealTemplates.size,
            recipes       = bundle.recipes.size,
            medications   = bundle.medications.size,
        )
    }
}

/**
 * Lightweight metadata read from a backup file without applying it — lets the
 * import UI show "this backup was taken on <date> by app version <x>,
 * containing <n> items" before the user commits to overwriting local data.
 */
data class BackupMetadata(
    val formatVersion: Int,
    val exportedAtMs: Long,
    val appVersionName: String,
    val summary: BackupSummary,
) {
    companion object {
        fun from(bundle: BackupBundle) = BackupMetadata(
            formatVersion = bundle.formatVersion,
            exportedAtMs = bundle.exportedAtMs,
            appVersionName = bundle.appVersionName,
            summary = BackupSummary.from(bundle),
        )
    }
}

sealed class BackupImportError : Exception() {
    /** The file's formatVersion is newer than this app build knows how to read. */
    data class UnsupportedVersion(val found: Int, val supported: Int) : BackupImportError()
    /** The file isn't valid JSON, or doesn't match the expected BackupBundle shape. */
    data class Malformed(val underlyingCause: Throwable) : BackupImportError()
}
