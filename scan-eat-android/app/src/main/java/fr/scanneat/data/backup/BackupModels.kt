package fr.scanneat.data.backup

import fr.scanneat.data.local.db.activity.ActivityEntity
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.data.local.db.recipe.RecipeEntity
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.db.template.MealTemplateEntity
import fr.scanneat.data.local.db.weight.WeightEntity

// ============================================================================
// BACKUP BUNDLE — full local export/import of the Room-backed user data.
//
// Covers the 7 @Database entities (scan history, diary, custom foods, weight,
// activity, meal templates, recipes). Does NOT cover DataStore-backed data
// (Biolism profile/sessions, hydration, fasting, reminders, meal plan, day
// notes, app settings) — that's a separate, smaller surface that can be
// folded in later; this bundle targets the highest-volume, hardest-to-lose
// user-generated content first.
//
// version bumps whenever a field is added/removed/retyped, so importFromJson
// can refuse (rather than silently corrupt) a bundle from an incompatible
// future or ancient export.
// ============================================================================

const val BACKUP_FORMAT_VERSION = 1

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
