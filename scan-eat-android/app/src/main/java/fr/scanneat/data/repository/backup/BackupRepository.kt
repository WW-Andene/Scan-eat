package fr.scanneat.data.repository.backup

import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import fr.scanneat.BuildConfig
import fr.scanneat.data.backup.BackupBundle
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
import fr.scanneat.data.local.db.price.PriceDao
import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.scan.ScanScoreHistoryDao
import fr.scanneat.data.local.db.template.MealTemplateDao
import fr.scanneat.data.local.db.weight.WeightDao
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.nutrition.DayNotesRepository
import fr.scanneat.data.repository.planning.GroceryCheckedRepository
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.data.repository.planning.MealPlanRepository
import fr.scanneat.data.repository.reminders.RemindersRepository
import fr.scanneat.util.ioCatching
import kotlinx.coroutines.flow.first
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
    private val priceDao: PriceDao,
    // Widened from private to internal so BackupRestore.kt's restoreDataStoreData()
    // extension function (extracted verbatim into its own sibling file, same
    // pattern as HealthConnectRepository's Ext files) can still reach these.
    internal val prefs: UserPreferences,
    internal val hydrationRepo: HydrationRepository,
    internal val fastingRepo: FastingRepository,
    internal val dayNotesRepo: DayNotesRepository,
    internal val mealPlanRepo: MealPlanRepository,
    internal val remindersRepo: RemindersRepository,
    internal val groceryCheckedRepo: GroceryCheckedRepository,
    internal val manualGroceryRepo: ManualGroceryRepository,
    internal val biolismRepo: BiolismRepository,
    private val moshi: Moshi,
) {
    // Internal (not private) so BackupParsing.kt's parseBundle() extension
    // function can reach it - same reasoning as the constructor properties above.
    internal val bundleAdapter = moshi.adapter(BackupBundle::class.java)

    /**
     * Reads every table plus DataStore-backed data and serializes to a pretty-printed
     * JSON string. [passphrase], when non-blank, encrypts the resulting file (see
     * BackupPassphraseCipher) - opt-in, since a lost/forgotten passphrase makes the
     * backup permanently unreadable, unlike the plaintext default.
     */
    suspend fun exportToJson(passphrase: String? = null): String {
        val profile = prefs.profile.first()
        val (fastingStartMs, fastingTargetHours, fastingHistory) = fastingRepo.exportForBackup()

        val bundle = BackupBundle(
            exportedAtMs  = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            scanHistory   = scanHistoryDao.getAllForBackup(),
            consumption   = consumptionDao.getAllForBackup(),
            customFoods   = customFoodDao.getAllForBackup(),
            weights       = weightDao.getAllForBackup().map { it.decryptedForBackup() },
            activities    = activityDao.getAllForBackup(),
            mealTemplates = mealTemplateDao.getAllForBackup(),
            recipes       = recipeDao.getAllForBackup(),
            medications   = medicationDao.getAllForBackup().map { it.decryptedForBackup() },
            medicationLog = medicationLogDao.getAllForBackup().map { it.decryptedForBackup() },
            scanScoreHistory = scanScoreHistoryDao.getAllForBackup(),
            priceLog      = priceDao.getAllForBackup(),
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
        val plainJson = bundleAdapter.indent("  ").toJson(bundle)
        // Opt-in - see BackupPassphraseCipher's own doc comment for the file
        // format and why a device-bound Keystore key can't be used for a file
        // that's explicitly meant to leave the device.
        return if (passphrase.isNullOrEmpty()) plainJson else BackupPassphraseCipher.encrypt(plainJson, passphrase)
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
    suspend fun importFromJson(json: String, passphrase: String? = null): Result<BackupSummary> = ioCatching {
        val bundle = parseBundle(json, passphrase)
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

            // Bundle rows are plaintext (decryptedForBackup ran on export) - re-encrypt
            // before they land in the DB, same as medications above.
            weightDao.insertAll(bundle.weights.map { it.encryptedFromBackup() })
            activityDao.insertAll(bundle.activities)
            mealTemplateDao.insertAll(bundle.mealTemplates)
            recipeDao.insertAll(bundle.recipes)

            val existingMedications = medicationDao.getAllForBackup()
            val existingMedicationBarcodes = existingMedications.mapNotNullTo(mutableSetOf()) { it.barcode }
            val newMedications = bundle.medications.filter { row ->
                row.barcode == null || existingMedicationBarcodes.add(row.barcode)
            }
            // Bundle rows are plaintext (decryptedForBackup ran on export) -
            // re-encrypt before they land in the DB, same as any live save.
            medicationDao.insertAll(newMedications.map { it.encryptedFromBackup() })

            // medication_log.medicationId has no DB-level FOREIGN KEY to
            // medications.id — deliberately: MedicationLogEntity.medicationName
            // is a denormalized snapshot specifically so a log entry survives
            // its medication being renamed or deleted later (see that entity's
            // own doc comment), which a CASCADE (or a default NO ACTION) FK
            // would break — CASCADE would silently erase adherence history the
            // moment a medication is deleted, and NO ACTION would make
            // MedicationRepository.delete() start throwing for any medication
            // that was ever logged. What a schema-level FK would still be
            // useful for — refusing to create a *new* reference to a
            // medication id that never existed — is worth having without
            // that trade-off, so it's enforced here instead, at the one place
            // new medicationId values can plausibly come from outside the
            // live save path (a hand-edited or partially-restored backup
            // file): a log row whose medicationId isn't one of this import's
            // medications and isn't already local is a dangling reference by
            // construction and is dropped rather than persisted as an orphan.
            val validMedicationIds = existingMedications.mapTo(mutableSetOf()) { it.id } + newMedications.map { it.id }

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
                row.medicationId in validMedicationIds && existingLogKeys.add(Triple(row.medicationId, row.date, row.profileId))
            }
            medicationLogDao.insertAll(newMedicationLog.map { it.encryptedFromBackup() })

            // scan_score_history uses an autoGenerate Long id like scan_history/
            // consumption_log above, for the same reason - reset id=0 and dedup by
            // natural key (matchKey+scannedAt already uniquely identifies a real
            // persist() call) before inserting.
            val existingScoreKeys = scanScoreHistoryDao.getAllForBackup()
                .map { it.matchKey to it.scannedAt }.toSet()
            val newScores = bundle.scanScoreHistory.filter { it.matchKey to it.scannedAt !in existingScoreKeys }
            scanScoreHistoryDao.insertAll(newScores.map { it.copy(id = 0) })

            // price_log rows carry a stable UUID id like weights/medications - a plain
            // REPLACE-on-id insertAll is idempotent for re-importing the same file
            // twice, and two genuinely different entries never collide on a random UUID.
            priceDao.insertAll(bundle.priceLog)
        }

        restoreDataStoreData(bundle)

        // scan/consumption counts reflect rows actually inserted (post-dedup);
        // the other tables key off a stable id/slug and fully apply every time.
        BackupSummary.from(bundle).copy(scanHistory = importedScans, consumption = importedConsumption)
    }

    /**
     * Reads just the header/summary info from a backup file — same validation
     * (version check, malformed JSON) as [importFromJson] but without touching
     * the DB or DataStore, so the UI can preview a file ("taken on 2026-07-01,
     * 42 items") before the user commits to overwriting local data with it.
     */
    fun peekMetadata(json: String, passphrase: String? = null): Result<BackupMetadata> = runCatching {
        BackupMetadata.from(parseBundle(json, passphrase))
    }

    /** True when [json] is a BackupPassphraseCipher-encrypted file, not raw JSON -
     *  the UI checks this before calling peekMetadata() to decide whether to prompt
     *  for a passphrase first, rather than trying blind and reading a generic error. */
    fun isEncryptedBackup(json: String): Boolean = BackupPassphraseCipher.isEncrypted(json)

    /**
     * Clears the scan history table (keeps all other data intact) - also clears
     * scan_score_history, since leaving it behind would resurface pre-clear
     * scores as "prior scans" the next time a previously-scanned product is
     * rescanned, contradicting what a user asking to erase their history expects.
     */
    suspend fun clearScanHistory() = db.withTransaction {
        // Same atomicity gap as ScanRepository.persist() (its inverse: clearing
        // instead of appending) - two separate DAO calls with no shared
        // transaction meant a process death between them could clear scan_history
        // while leaving scan_score_history intact, resurfacing pre-clear scores as
        // "prior scans" the next time a previously-scanned product was rescanned -
        // exactly the outcome this function's own doc comment says it exists to avoid.
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
}
