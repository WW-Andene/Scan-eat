package fr.scanneat.data.backup

import fr.scanneat.data.local.db.activity.ActivityEntity
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.data.local.db.recipe.RecipeEntity
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.db.template.MealTemplateEntity
import fr.scanneat.data.local.db.weight.WeightEntity
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.data.repository.reminders.ReminderSettings

// ============================================================================
// BACKUP BUNDLE — full local export/import of the user data.
//
// Covers the 7 @Database entities (scan history, diary, custom foods, weight,
// activity, meal templates, recipes) plus, since format v2, the
// DataStore-backed data a restore-on-a-new-device previously silently lost:
// profile, app settings, reminder settings, fasting (active session +
// history), hydration log, day notes, and the meal plan.
//
// Deliberately excludes the Groq API key from SettingsBackup — a backup file
// shared for debugging or support must not leak a credential.
//
// version bumps whenever a field is added/removed/retyped, so importFromJson
// can refuse (rather than silently corrupt) a bundle from an incompatible
// future or ancient export. All v2 fields default to null/empty so a v1 file
// (which has none of them) still parses cleanly.
// ============================================================================

const val BACKUP_FORMAT_VERSION = 2

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
)

data class BackupSummary(
    val scanHistory: Int,
    val consumption: Int,
    val customFoods: Int,
    val weights: Int,
    val activities: Int,
    val mealTemplates: Int,
    val recipes: Int,
) {
    val total: Int get() = scanHistory + consumption + customFoods + weights + activities + mealTemplates + recipes

    companion object {
        fun from(bundle: BackupBundle) = BackupSummary(
            scanHistory   = bundle.scanHistory.size,
            consumption   = bundle.consumption.size,
            customFoods   = bundle.customFoods.size,
            weights       = bundle.weights.size,
            activities    = bundle.activities.size,
            mealTemplates = bundle.mealTemplates.size,
            recipes       = bundle.recipes.size,
        )
    }
}

sealed class BackupImportError : Exception() {
    /** The file's formatVersion is newer than this app build knows how to read. */
    data class UnsupportedVersion(val found: Int, val supported: Int) : BackupImportError()
    /** The file isn't valid JSON, or doesn't match the expected BackupBundle shape. */
    data class Malformed(val underlyingCause: Throwable) : BackupImportError()
}
