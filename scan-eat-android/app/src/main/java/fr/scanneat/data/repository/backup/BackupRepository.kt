package fr.scanneat.data.repository.backup

import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import fr.scanneat.BuildConfig
import fr.scanneat.data.backup.BACKUP_FORMAT_VERSION
import fr.scanneat.data.backup.BackupBundle
import fr.scanneat.data.backup.BackupImportError
import fr.scanneat.data.backup.BackupMetadata
import fr.scanneat.data.backup.BackupSummary
import fr.scanneat.data.backup.DayNoteBackup
import fr.scanneat.data.backup.HydrationEntryBackup
import fr.scanneat.data.backup.ProfileBackup
import fr.scanneat.data.backup.SettingsBackup
import fr.scanneat.data.local.db.AppDatabase
import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.customfood.CustomFoodDao
import fr.scanneat.data.local.db.medication.MedicationDao
import fr.scanneat.data.local.db.medication.MedicationLogDao
import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.scan.ScanScoreHistoryDao
import fr.scanneat.data.local.db.template.MealTemplateDao
import fr.scanneat.data.local.db.weight.WeightDao
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.nutrition.DayNotesRepository
import fr.scanneat.data.repository.planning.GroceryCheckedRepository
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.data.repository.planning.MealPlanRepository
import fr.scanneat.data.repository.reminders.RemindersRepository
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import fr.scanneat.util.ioCatching
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// BACKUP REPOSITORY — local JSON export/import of the user's data.
//
// No cloud/account infra exists (or is planned for this pass) — this is the
// on-device equivalent: a single JSON file the user saves via the system
// file picker (Storage Access Framework) and can restore from later, on this
// device or a new one after reinstalling. Addresses the app's biggest single
// data-loss risk: everything currently lives only in this app's private
// storage, gone the moment the app is uninstalled or the device is lost.
//
// Lives in data/repository/backup/ (moved from the previously standalone
// data/backup/ package) to match the data/repository/<feature>/ convention
// every other repository follows - only this class moved; BackupModels.kt's
// plain data/DTO types (BackupBundle, BackupSummary, etc.) stay in
// data/backup/, imported above like any other cross-package model.
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
    private val medicationDao: MedicationDao,
    private val medicationLogDao: MedicationLogDao,
    private val scanScoreHistoryDao: ScanScoreHistoryDao,
    private val prefs: UserPreferences,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
    private val dayNotesRepo: DayNotesRepository,
    private val mealPlanRepo: MealPlanRepository,
    private val remindersRepo: RemindersRepository,
    private val groceryCheckedRepo: GroceryCheckedRepository,
    private val manualGroceryRepo: ManualGroceryRepository,
    private val biolismRepo: BiolismRepository,
    private val moshi: Moshi,
) {
    private val bundleAdapter = moshi.adapter(BackupBundle::class.java)

    /** Reads every table plus DataStore-backed data and serializes to a pretty-printed JSON string. */
    suspend fun exportToJson(): String {
        val profile = prefs.profile.first()
        val (fastingStartMs, fastingTargetHours, fastingHistory) = fastingRepo.exportForBackup()

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
            medications   = medicationDao.getAllForBackup(),
            medicationLog = medicationLogDao.getAllForBackup(),
            scanScoreHistory = scanScoreHistoryDao.getAllForBackup(),
            profile = ProfileBackup(
                name = profile.name,
                sex = profile.sex.name,
                ageYears = profile.ageYears,
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                goalWeightKg = profile.goalWeightKg,
                activityLevel = profile.activityLevel.name,
                goal = profile.goal.name,
                diet = profile.diet.key,
                allergens = profile.allergens.toList(),
                isMenstruating = profile.isMenstruating,
                healthConditions = profile.healthConditions.toList(),
            ),
            // Deliberately excludes the Groq API key — see BackupModels.kt.
            settings = SettingsBackup(
                apiMode = prefs.apiMode.first().key,
                serverUrl = prefs.serverUrl.first(),
                language = prefs.language.first(),
                theme = prefs.theme.first(),
                dyslexicFont = prefs.dyslexicFont.first(),
                colorblindMode = prefs.colorblindMode.first(),
                useImperialWeight = prefs.useImperialWeight.first(),
            ),
            reminderSettings = remindersRepo.settings.first(),
            fastingActiveStartMs = fastingStartMs,
            fastingActiveTargetHours = fastingTargetHours,
            fastingHistory = fastingHistory,
            hydration = hydrationRepo.exportAll().map { (date, ml) -> HydrationEntryBackup(date.toString(), ml) },
            dayNotes = dayNotesRepo.exportAll().map { (date, text) -> DayNoteBackup(date.toString(), text) },
            mealPlanRaw = mealPlanRepo.exportRaw(),
            groceryCheckedKeys = groceryCheckedRepo.checkedKeys.first().toList(),
            biolism = biolismRepo.exportForBackup(),
            manualGroceryItems = manualGroceryRepo.exportAll(),
        )
        return bundleAdapter.indent("  ").toJson(bundle)
    }

    /**
     * Restores every table plus DataStore-backed data (including, since v4,
     * Biolism's own "biolism_prefs" DataStore) from [json]. The Room
     * tables land inside a single transaction — either the whole DB side
     * lands or none of it does, so a malformed file or a crash mid-import
     * can never leave the DB half-restored. The DataStore-backed data
     * (profile/settings/reminders/fasting/hydration/notes/meal plan) is
     * separate storage and applies right after, best-effort per field.
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
    suspend fun importFromJson(json: String): Result<BackupSummary> = ioCatching {
        val bundle = parseBundle(json)
        var importedScans = 0
        var importedConsumption = 0
        db.withTransaction {
            // scan_history/consumption_log reset id=0 above (see class doc), so the
            // same file imported twice would otherwise insert every row a second
            // time — dedup against what's already in the DB by a natural key
            // before inserting, inside the same transaction as the rest of the import.
            //
            // A barcoded row can't be deduped by (barcode, scannedAt) the way a
            // no-barcode row can: persist()/ScanHistoryDao.upsertByBarcode mutates
            // scannedAt in place on every rescan (one row per barcode, by design -
            // see upsertByBarcode's own doc comment), so a barcode rescanned locally
            // since the backup was taken has a different scannedAt than the file's
            // copy, and the old (barcode, scannedAt) key failed to recognize it as
            // the same row - inserting a duplicate and silently reintroducing the
            // exact "same product -> duplicate entries" bug that upsert scheme
            // exists to prevent. Dedupe barcoded rows by barcode alone instead,
            // matching upsertByBarcode's own "one row per barcode" invariant; a
            // running mutable set (not just the pre-existing rows) also guards
            // against two barcode-sharing rows within the same import batch.
            val existingScans = scanHistoryDao.getAllForBackup()
            val seenBarcodes = existingScans.mapNotNullTo(mutableSetOf()) { it.barcode }
            val existingNoBarcodeKeys = existingScans.filter { it.barcode == null }
                .mapTo(mutableSetOf()) { it.productName to it.scannedAt }
            val newScans = bundle.scanHistory.filter { row ->
                if (row.barcode != null) seenBarcodes.add(row.barcode)
                else (row.productName to row.scannedAt) !in existingNoBarcodeKeys
            }
            scanHistoryDao.insertAll(newScans.map { it.copy(id = 0) })
            importedScans = newScans.size

            val existingConsumptionKeys = consumptionDao.getAllForBackup()
                .map { listOf(it.date, it.mealSlot, it.productName, it.portionG, it.loggedAt) }.toSet()
            val newConsumption = bundle.consumption.filter {
                listOf(it.date, it.mealSlot, it.productName, it.portionG, it.loggedAt) !in existingConsumptionKeys
            }
            consumptionDao.insertAll(newConsumption.map { it.copy(id = 0) })
            importedConsumption = newConsumption.size

            // custom_foods/medications have no unique constraint on barcode (only
            // on id, which insertAll's REPLACE conflicts on) - restoring a backup
            // taken before this device re-saved the same barcoded item under a
            // fresh id would otherwise reintroduce the exact "two rows sharing a
            // barcode" duplicate CustomFoodDao.upsertFood/MedicationDao.
            // upsertMedication exist to prevent on the live save path. Skip any
            // backup row whose barcode already has a local row - the existing
            // local state (possibly edited/rescanned since the backup was taken)
            // wins, matching scan_history's dedup-favors-existing approach above.
            val existingCustomFoodRows = customFoodDao.getAllForBackup()
            val existingCustomFoodBarcodes = existingCustomFoodRows.mapNotNullTo(mutableSetOf()) { it.barcode }
            // Barcode-less rows (every food ever added through AddFoodDialog) had no
            // dedup guard at all here - CustomFoodDao.upsertFood()/renameIfNoCollision()
            // guarantee no two live-saved custom foods share a name, but restoring a
            // backup bypassed that entirely via a raw insertAll(). Restoring the same
            // backup twice, or restoring onto a device that already has manually-typed
            // foods, could silently create two rows with an identical name - which then
            // broke CustomFoodScreen's own name-based delete/rename resolution (tapping
            // one row could mutate/delete the other).
            val existingCustomFoodNames = existingCustomFoodRows.mapTo(mutableSetOf()) { it.name.lowercase() }
            val newCustomFoods = bundle.customFoods.filter { row ->
                if (row.barcode != null) existingCustomFoodBarcodes.add(row.barcode)
                else existingCustomFoodNames.add(row.name.lowercase())
            }
            customFoodDao.insertAll(newCustomFoods)

            weightDao.insertAll(bundle.weights)
            activityDao.insertAll(bundle.activities)
            mealTemplateDao.insertAll(bundle.mealTemplates)
            recipeDao.insertAll(bundle.recipes)

            val existingMedicationBarcodes = medicationDao.getAllForBackup()
                .mapNotNullTo(mutableSetOf()) { it.barcode }
            val newMedications = bundle.medications.filter { row ->
                row.barcode == null || existingMedicationBarcodes.add(row.barcode)
            }
            medicationDao.insertAll(newMedications)

            // medication_log has a DB-level UNIQUE index on (medicationId, date,
            // profileId) since MIGRATION_23_24 - inserting via insertAll's REPLACE
            // (MedicationLogDao.insertAll) with no dedup here, unlike every sibling
            // table above, meant a backup row colliding with an existing local row
            // on that key (different id - e.g. after a correct-and-relog) would
            // silently REPLACE (delete + reinsert) the existing local row with the
            // backup's possibly-stale one, losing the corrected adherence record.
            // Skip any backup row whose key already has a local row - existing
            // local state wins, matching every other table's dedup approach here.
            val existingLogKeys = medicationLogDao.getAllForBackup()
                .mapTo(mutableSetOf()) { Triple(it.medicationId, it.date, it.profileId) }
            val newMedicationLog = bundle.medicationLog.filter { row ->
                existingLogKeys.add(Triple(row.medicationId, row.date, row.profileId))
            }
            medicationLogDao.insertAll(newMedicationLog)

            // scan_score_history uses an autoGenerate Long id like scan_history/
            // consumption_log above, for the same reason - reset id=0 and dedup by
            // natural key (matchKey+scannedAt already uniquely identifies a real
            // persist() call) before inserting.
            val existingScoreKeys = scanScoreHistoryDao.getAllForBackup()
                .map { it.matchKey to it.scannedAt }.toSet()
            val newScores = bundle.scanScoreHistory.filter { it.matchKey to it.scannedAt !in existingScoreKeys }
            scanScoreHistoryDao.insertAll(newScores.map { it.copy(id = 0) })
        }

        restoreDataStoreData(bundle)

        // scan/consumption counts reflect rows actually inserted (post-dedup);
        // the other tables key off a stable id/slug and fully apply every time.
        BackupSummary.from(bundle).copy(scanHistory = importedScans, consumption = importedConsumption)
    }

    private suspend fun restoreDataStoreData(bundle: BackupBundle) {
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

    /**
     * Reads just the header/summary info from a backup file — same validation
     * (version check, malformed JSON) as [importFromJson] but without touching
     * the DB or DataStore, so the UI can preview a file ("taken on 2026-07-01,
     * 42 items") before the user commits to overwriting local data with it.
     */
    fun peekMetadata(json: String): Result<BackupMetadata> = runCatching {
        BackupMetadata.from(parseBundle(json))
    }

    /**
     * Clears the scan history table (keeps all other data intact) - also clears
     * scan_score_history, since leaving it behind would resurface pre-clear
     * scores as "prior scans" the next time a previously-scanned product is
     * rescanned, contradicting what a user asking to erase their history expects.
     */
    suspend fun clearScanHistory() {
        scanHistoryDao.clearAll()
        scanScoreHistoryDao.clearAll()
    }

    /** Reactive total row counts for the two most user-visible tables — shown in the
     *  Settings backup section so users know what they'd be exporting or resetting. */
    fun observeDataStats(): kotlinx.coroutines.flow.Flow<Pair<Int, Int>> =
        kotlinx.coroutines.flow.combine(
            scanHistoryDao.observeTotalCount(),
            consumptionDao.observeTotalCount(),
        ) { scans, diary -> scans to diary }

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
