package fr.scanneat.data.backup

import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import fr.scanneat.BuildConfig
import fr.scanneat.data.local.db.AppDatabase
import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.customfood.CustomFoodDao
import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.template.MealTemplateDao
import fr.scanneat.data.local.db.weight.WeightDao
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// BACKUP REPOSITORY — local JSON export/import of the Room-backed data.
//
// No cloud/account infra exists (or is planned for this pass) — this is the
// on-device equivalent: a single JSON file the user saves via the system
// file picker (Storage Access Framework) and can restore from later, on this
// device or a new one after reinstalling. Addresses the app's biggest single
// data-loss risk: everything currently lives only in this app's private
// storage, gone the moment the app is uninstalled or the device is lost.
// ============================================================================

@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val scanHistoryDao: ScanHistoryDao,
    private val consumptionDao: ConsumptionDao,
    private val customFoodDao: CustomFoodDao,
    private val weightDao: WeightDao,
    private val activityDao: ActivityDao,
    private val mealTemplateDao: MealTemplateDao,
    private val recipeDao: RecipeDao,
    private val moshi: Moshi,
) {
    private val bundleAdapter = moshi.adapter(BackupBundle::class.java)

    /** Reads every table and serializes to a pretty-printed JSON string. */
    suspend fun exportToJson(): String {
        val bundle = BackupBundle(
            exportedAtMs  = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            scanHistory   = scanHistoryDao.getAllForBackup(),
            consumption   = consumptionDao.getAllForBackup(),
            customFoods   = customFoodDao.getAllForBackup(),
            weights       = weightDao.getAllForBackup(),
            activities    = activityDao.getAllForBackup(),
            mealTemplates = mealTemplateDao.getAllForBackup(),
            recipes       = recipeDao.getAllForBackup(),
        )
        return bundleAdapter.indent("  ").toJson(bundle)
    }

    /**
     * Restores every table from [json] inside a single transaction — either
     * the whole import lands or none of it does, so a malformed file or a
     * crash mid-import can never leave the DB half-restored.
     *
     * Every table except scan_history/consumption_log keys off a stable
     * UUID/slug, so REPLACE-by-id is a safe merge for those. scan_history
     * and consumption_log use autoGenerate Long ids instead — restoring a
     * backup taken on a different device (or after this device already
     * logged new rows since the backup) with the file's original ids would
     * silently REPLACE whatever local row happens to share that same
     * autoincrement number, destroying unrelated local data. This is a real
     * path: the backup hint text explicitly promises restoring "on this
     * device or another." Resetting id=0 makes Room assign fresh,
     * non-colliding ids for these two tables on every import.
     */
    suspend fun importFromJson(json: String): Result<BackupSummary> = runCatching {
        val bundle = parseBundle(json)
        var importedScans = 0
        var importedConsumption = 0
        db.withTransaction {
            // scan_history/consumption_log reset id=0 above (see class doc), so the
            // same file imported twice would otherwise insert every row a second
            // time — dedup against what's already in the DB by a natural key
            // before inserting, inside the same transaction as the rest of the import.
            val existingScanKeys = scanHistoryDao.getAllForBackup()
                .map { (it.barcode ?: it.productName) to it.scannedAt }.toSet()
            val newScans = bundle.scanHistory.filter { (it.barcode ?: it.productName) to it.scannedAt !in existingScanKeys }
            scanHistoryDao.insertAll(newScans.map { it.copy(id = 0) })
            importedScans = newScans.size

            val existingConsumptionKeys = consumptionDao.getAllForBackup()
                .map { listOf(it.date, it.mealSlot, it.productName, it.portionG, it.loggedAt) }.toSet()
            val newConsumption = bundle.consumption.filter {
                listOf(it.date, it.mealSlot, it.productName, it.portionG, it.loggedAt) !in existingConsumptionKeys
            }
            consumptionDao.insertAll(newConsumption.map { it.copy(id = 0) })
            importedConsumption = newConsumption.size

            customFoodDao.insertAll(bundle.customFoods)
            weightDao.insertAll(bundle.weights)
            activityDao.insertAll(bundle.activities)
            mealTemplateDao.insertAll(bundle.mealTemplates)
            recipeDao.insertAll(bundle.recipes)
        }
        // scan/consumption counts reflect rows actually inserted (post-dedup);
        // the other tables key off a stable id/slug and fully apply every time.
        BackupSummary.from(bundle).copy(scanHistory = importedScans, consumption = importedConsumption)
    }

    private fun parseBundle(json: String): BackupBundle {
        val bundle = try {
            bundleAdapter.fromJson(json)
        } catch (e: Exception) {
            throw BackupImportError.Malformed(e)
        } ?: throw BackupImportError.Malformed(IllegalArgumentException("empty JSON body"))
        if (bundle.formatVersion > BACKUP_FORMAT_VERSION) {
            throw BackupImportError.UnsupportedVersion(bundle.formatVersion, BACKUP_FORMAT_VERSION)
        }
        return bundle
    }
}
