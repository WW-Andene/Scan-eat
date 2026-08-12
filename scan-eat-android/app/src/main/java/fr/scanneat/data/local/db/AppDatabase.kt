package fr.scanneat.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.activity.ActivityEntity
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.customfood.CustomFoodDao
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.data.local.db.medication.MedicationDao
import fr.scanneat.data.local.db.medication.MedicationEntity
import fr.scanneat.data.local.db.medication.MedicationLogDao
import fr.scanneat.data.local.db.medication.MedicationLogEntity
import fr.scanneat.data.local.db.nonfood.NonFoodScanDao
import fr.scanneat.data.local.db.nonfood.NonFoodScanEntity
import fr.scanneat.data.local.db.pantry.PantryDao
import fr.scanneat.data.local.db.pantry.PantryEntity
import fr.scanneat.data.local.db.price.PriceDao
import fr.scanneat.data.local.db.price.PriceEntity
import fr.scanneat.data.local.db.recall.RecallDao
import fr.scanneat.data.local.db.recall.RecallEntity
import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.recipe.RecipeEntity
import fr.scanneat.data.local.db.scan.OnlineSearchCacheDao
import fr.scanneat.data.local.db.scan.OnlineSearchCacheEntity
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.db.scan.ScanScoreHistoryDao
import fr.scanneat.data.local.db.scan.ScanScoreHistoryEntity
import fr.scanneat.data.local.db.symptom.SymptomDao
import fr.scanneat.data.local.db.symptom.SymptomEntity
import fr.scanneat.data.local.db.template.MealTemplateDao
import fr.scanneat.data.local.db.template.MealTemplateEntity
import fr.scanneat.data.local.db.weight.WeightDao
import fr.scanneat.data.local.db.weight.WeightEntity

@Database(
    entities = [
        ScanHistoryEntity::class,
        ConsumptionEntity::class,
        CustomFoodEntity::class,
        WeightEntity::class,
        ActivityEntity::class,
        MealTemplateEntity::class,
        RecipeEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class,
        ScanScoreHistoryEntity::class,
        PriceEntity::class,
        OnlineSearchCacheEntity::class,
        RecallEntity::class,
        PantryEntity::class,
        SymptomEntity::class,
        NonFoodScanEntity::class,
    ],
    version = 36,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun consumptionDao(): ConsumptionDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weightDao(): WeightDao
    abstract fun activityDao(): ActivityDao
    abstract fun mealTemplateDao(): MealTemplateDao
    abstract fun recipeDao(): RecipeDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun scanScoreHistoryDao(): ScanScoreHistoryDao
    abstract fun priceDao(): PriceDao
    abstract fun onlineSearchCacheDao(): OnlineSearchCacheDao
    abstract fun recallDao(): RecallDao
    abstract fun pantryDao(): PantryDao
    abstract fun symptomDao(): SymptomDao
    abstract fun nonFoodScanDao(): NonFoodScanDao
}

// ── Room migrations ────────────────────────────────────────────────────────────
// Add a new Migration object for each schema version bump before shipping.
// Schema versions are defined on @Database(version = N).
//
// exportSchema = true writes JSON snapshots to app/schemas/ (ksp arg
// room.schemaLocation, app/build.gradle.kts) on every build; the directory IS
// committed to this repo (15.json-24.json, tracked, not gitignored) and every
// migration below is manually cross-checked against its target version's JSON
// snapshot as part of each round's audit. What's still missing is an automated
// Room migration test (androidx.room:room-testing MigrationTestHelper) that
// consumes this same baseline — no such test exists yet in this module, so a
// future migration bug would only be caught by the next round's manual check,
// not by the build itself.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v1 → v2: initial schema (no changes required — baseline)
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v2 → v3: added RecipeEntity. This was previously a no-op comment, which
        // meant Room used this (empty) registered migration instead of falling back
        // to fallbackToDestructiveMigration() — anyone upgrading from a v2 install
        // hit "no such table: recipes" the moment Recipes/Grocery/Templates opened.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipes` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`servings` INTEGER NOT NULL, " +
                "`componentsJson` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v3 → v4: add indices to the two tables that grow largest over time.
        // Without these, observeRecent/findByBarcode (run on every scan) and
        // observeByDate/observeRange (run on every Diary/Dashboard load) do a full
        // table scan — fine with a handful of rows, a real slowdown after months of use.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_history_profileId_scannedAt` ON `scan_history` (`profileId`, `scannedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_history_barcode_profileId` ON `scan_history` (`barcode`, `profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_consumption_log_profileId_date` ON `consumption_log` (`profileId`, `date`)")
    }
}
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v4 → v5: favorite flag on scan_history, so a specific scan can be
        // pinned and found again from a dedicated Favorites filter.
        db.execSQL("ALTER TABLE `scan_history` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v5 → v6: weight_log's unique index was on `date` alone, not
        // `(date, profileId)` — a second profile logging on a date the
        // first already has would silently REPLACE-delete the first
        // profile's row. Not reachable today (no UI ever passes a
        // non-"default" profileId yet) but free to fix before any real
        // dual-profile data exists.
        db.execSQL("DROP INDEX IF EXISTS `index_weight_log_date`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_log_date_profileId` ON `weight_log` (`date`, `profileId`)")
    }
}
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v6 → v7: activity_log was the one remaining table indexed on
        // `date` alone instead of `(date, profileId)` like
        // consumption_log/scan_history already are - a non-unique lookup
        // index, so no data-loss risk like weight_log's v5→v6 fix, just a
        // consistency/query-shape gap that will matter once multi-profile
        // queries are actually reachable.
        db.execSQL("DROP INDEX IF EXISTS `index_activity_log_date`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_log_date_profileId` ON `activity_log` (`date`, `profileId`)")
    }
}
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v7 → v8: observeFavorites' WHERE profileId = ? AND favorite = 1 ORDER BY
        // scannedAt DESC wasn't covered by either existing scan_history index (one
        // leads with scannedAt, the other with barcode) - full table scan on every
        // emission of a Flow that's held live for the whole history screen.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_history_profileId_favorite_scannedAt` ON `scan_history` (`profileId`, `favorite`, `scannedAt`)")
    }
}
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v8 → v9: custom_foods/recipes/meal_templates all filter WHERE
        // profileId = ? but, unlike every other user-content table, had no
        // index backing it - the same full-table-scan gap v3→v4 fixed for
        // scan_history/consumption_log.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_foods_profileId` ON `custom_foods` (`profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipes_profileId` ON `recipes` (`profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_templates_profileId` ON `meal_templates` (`profileId`)")
    }
}
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v9 → v10: activity_log gains an optional sub-type (e.g. "bench_press"
        // under STRENGTH, "trail" under RUNNING) plus free-form training metrics
        // (sets/reps/distance/weight used) - Activité previously had only a flat
        // activity-type + duration, with no way to record what was actually done.
        db.execSQL("ALTER TABLE `activity_log` ADD COLUMN `subType` TEXT")
        db.execSQL("ALTER TABLE `activity_log` ADD COLUMN `sets` INTEGER")
        db.execSQL("ALTER TABLE `activity_log` ADD COLUMN `reps` INTEGER")
        db.execSQL("ALTER TABLE `activity_log` ADD COLUMN `distanceKm` REAL")
        db.execSQL("ALTER TABLE `activity_log` ADD COLUMN `weightUsedKg` REAL")
    }
}
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v10 → v11: new `medications` table backing the Traitement tab -
        // manual medication tracking (name/dosage/schedule note/active flag),
        // same shape as every other user-content table.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `medications` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`dosage` TEXT NOT NULL, " +
                "`scheduleNote` TEXT NOT NULL, " +
                "`barcode` TEXT, " +
                "`active` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_medications_profileId` ON `medications` (`profileId`)")
    }
}
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v11 → v12: medications gain an optional daily reminder — Fasting/
        // Hydration/Weight all fire a notification via ReminderWorker, but a
        // medication's "schedule" was just a display-only free-text note with
        // no way to actually be reminded to take it.
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `reminderOn` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `reminderTime` TEXT NOT NULL DEFAULT '08:00'")
    }
}
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v12 → v13: activity_log gains an optional externalSourceId - Health
        // Connect sync for Activité was write-only (mirrors a logged workout
        // out), same as it was for weight before weight got a read-back sync.
        // A workout logged by an external fitness tracker straight into Health
        // Connect could never be pulled into Scan'eat's own history. This
        // column is the dedup key: without a stable id to check against,
        // re-running the sync would re-import the same external session as a
        // fresh duplicate every time (activity, unlike weight, has no
        // one-entry-per-day convention to dedupe against instead).
        db.execSQL("ALTER TABLE `activity_log` ADD COLUMN `externalSourceId` TEXT")
    }
}
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v13 → v14: new `medication_log` table - a dated "I took this" event.
        // MedicationEntity itself is only the active list + reminder schedule,
        // with no per-day record of whether a dose was actually taken, so
        // Traitement had no dated event of its own for the unified Calendar
        // (or any screen) to show.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `medication_log` (" +
                "`id` TEXT NOT NULL, " +
                "`medicationId` TEXT NOT NULL, " +
                "`medicationName` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`takenAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_log_date_profileId` ON `medication_log` (`date`, `profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_log_medicationId` ON `medication_log` (`medicationId`)")
    }
}
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v14 → v15: recipes gain an optional free-text notes/instructions field -
        // RecipeEntity previously stored only name/servings/ingredient components,
        // with no way to record how to actually make the dish (prep steps, cook
        // time, oven temp) - every other "recipe" concept in the app assumed that
        // existed, but the data model never had anywhere to put it.
        db.execSQL("ALTER TABLE `recipes` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
    }
}
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v15 → v16: findBetterInCategory (ScanHistoryDao) filters WHERE profileId = ?
        // AND category = ? AND score > ?, called on every successful scan via
        // ScanRepository.findBetterAlternative - none of scan_history's existing
        // indices lead with `category`, so this hot-path query scanned every row for
        // the profile. A composite index covering all three filter columns lets
        // SQLite use it directly instead.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_history_profileId_category_score` ON `scan_history` (`profileId`, `category`, `score`)")
    }
}
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v16 → v17: new scan_score_history table - see ScanScoreHistoryEntity's doc
        // comment. scan_history upserts by barcode (one row per barcode, REPLACEd on
        // every rescan) specifically to avoid unbounded row growth from re-scanning
        // the same product - but that meant a barcoded product's previous score was
        // silently overwritten and lost on every rescan, with nothing left for
        // ResultViewModel's scoreDelta/scoreHistory feature to compare against. That
        // feature was therefore structurally dead for every barcoded scan (the vast
        // majority of real scans) and only ever worked for the rarer no-barcode/
        // LLM-identified path. This table is an append-only log written alongside
        // (not instead of) scan_history, purely to give that feature real data.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scan_score_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`matchKey` TEXT NOT NULL, " +
                "`score` INTEGER NOT NULL, " +
                "`scannedAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_score_history_profileId_matchKey_scannedAt` ON `scan_score_history` (`profileId`, `matchKey`, `scannedAt`)")
    }
}
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v17 → v18: custom_foods gains an optional barcode - "Save to Mes Aliments"
        // on a scanned barcoded product previously dropped the barcode entirely, so
        // the only dedup key left was the display name, which two genuinely
        // different barcoded products can share (e.g. two brands both "Yaourt
        // nature") - silently overwriting one's nutrition values with the other's.
        // See CustomFoodDao.upsertFood, which now prefers a barcode match when given.
        db.execSQL("ALTER TABLE `custom_foods` ADD COLUMN `barcode` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_foods_barcode_profileId` ON `custom_foods` (`barcode`, `profileId`)")
    }
}
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v18 → v19: scan_history gains warningsJson - OcrParser.buildWarnings()/
        // detectSourceConflicts() produce real per-scan warning messages
        // (unreadable nutrition values, barcode-vs-label mismatches, OFF/LLM
        // conflicts) that ResultContent's WarningsSection was already built to
        // render, but scan_history had nowhere to store them - every persisted
        // scan silently lost this half of ScanResult.warnings, so the section
        // never showed anything for it. See ScanRepository.persist()/toDomain().
        db.execSQL("ALTER TABLE `scan_history` ADD COLUMN `warningsJson` TEXT NOT NULL DEFAULT '[]'")
    }
}
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v19 → v20: index medications(barcode, profileId) - MedicationRepository.
        // save() never deduped by barcode (unlike CustomFoodDao.upsertFood/
        // ScanHistoryDao.upsertByBarcode), so rescanning an already-added
        // medication's box created a duplicate row every time, each with its own
        // ReminderWorker schedule. See MedicationDao.upsertMedication.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_medications_barcode_profileId` ON `medications` (`barcode`, `profileId`)")
    }
}
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v20 → v21: recipes gains favorite - Recipe had no equivalent to
        // ScanResult's favorite field at all, so a saved recipe could never be
        // pinned to the top of the list the way a favorited scan can.
        db.execSQL("ALTER TABLE `recipes` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v21 → v22: meal_templates gains favorite - same gap as recipes had
        // before MIGRATION_20_21: a saved meal template could never be pinned
        // to the top of the Templates library.
        db.execSQL("ALTER TABLE `meal_templates` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v22 → v23: consumption_log gains ingredientsJson - a logged diary
        // entry only ever stored nutrition, never the ingredient list, so
        // checkUserAllergens()/checkDiet() (already run live against Recipes,
        // Grocery lists and Meal Templates) could never run against a Diary
        // entry at all. Old rows default to "[]" (empty ingredients), which
        // is honest - we don't have the original scan's ingredient list to
        // backfill and can't fabricate one; they just won't show a warning
        // until re-logged.
        db.execSQL("ALTER TABLE `consumption_log` ADD COLUMN `ingredientsJson` TEXT NOT NULL DEFAULT '[]'")
    }
}
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v23 → v24: medication_log had no DB-level uniqueness guard for
        // one-entry-per-medication-per-day the way weight_log has had since
        // MIGRATION_5_6 — MedicationLogDao.insertIfAbsent's transactional
        // check-then-insert closes the concurrent double-tap race for the
        // in-app "mark taken" path, but the DAO's raw insert()/insertAll()
        // (used by backup restore) bypass it entirely, so restoring two
        // devices' logs (or a backup containing an already-duplicated export)
        // could silently create duplicate rows with nothing to stop it.
        // Existing duplicates are collapsed (keep one arbitrary row per group,
        // by rowid) before the unique index is created, since a live duplicate
        // would otherwise make CREATE UNIQUE INDEX itself fail outright.
        db.execSQL("""
            DELETE FROM medication_log WHERE rowid NOT IN (
                SELECT MIN(rowid) FROM medication_log GROUP BY medicationId, date, profileId
            )
        """)
        db.execSQL("DROP INDEX IF EXISTS `index_medication_log_medicationId`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_log_medicationId_date_profileId` ON `medication_log` (`medicationId`, `date`, `profileId`)")

        // activity_log's only index leads with `date`, so
        // ActivityDao.getImportedExternalIds's `WHERE profileId = ? AND
        // externalSourceId IS NOT NULL` (no date predicate, run on every
        // Health Connect sync) couldn't use it and fell back to a full table
        // scan — same index-gap class MIGRATION_8_9 already fixed for
        // custom_foods/recipes/meal_templates.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_log_profileId_externalSourceId` ON `activity_log` (`profileId`, `externalSourceId`)")
    }
}
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v24 → v25: scan_history gains nonFoodChecked - OffMapper.classifyNonFood()
        // (server + Android) now blocks a lubricant/cosmetic/tobacco/etc. barcode
        // from ever being scored as food, but rows written before that check
        // existed are already sitting in scan_history with a bogus nutrition-based
        // grade. Backfilling them to 0 (not-yet-checked) rather than 1 lets
        // ScanRepository.scoreBarcode's cache-hit shortcut tell "verified food" apart
        // from "never checked" and re-run one real lookup per legacy barcode instead
        // of trusting a pre-classifier score forever - see ScanHistoryEntity's own
        // doc comment on the field.
        db.execSQL("ALTER TABLE `scan_history` ADD COLUMN `nonFoodChecked` INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v25 → v26: new `price_log` table backing the "Dépenses" (Expenses) Journal
        // tab - a manual "I paid X for this" entry logged from the Result screen,
        // paired with a per-category price/kg reference (ValueScoreEstimator) to
        // flag whether a purchase was a good or poor value. Manual entry only:
        // automatic price-tag OCR was considered but isn't reliable enough to build
        // honestly, same scoping call already made for MicronutrientEstimator's
        // category-typical fallbacks rather than fabricating precision.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `price_log` (" +
                "`id` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`productName` TEXT NOT NULL, " +
                "`barcode` TEXT, " +
                "`category` TEXT NOT NULL, " +
                "`priceEuros` REAL NOT NULL, " +
                "`weightG` REAL, " +
                "`pricePerKg` REAL, " +
                "`loggedAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_log_date_profileId` ON `price_log` (`date`, `profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_log_barcode_profileId` ON `price_log` (`barcode`, `profileId`)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v26 → v27: stock tracking for price_log - user-reported: logging a
        // portion consumed from an already-purchased product (e.g. 100g of a
        // 1kg pack bought once) had no way to avoid being either double-counted
        // as a second purchase or invisible from "Jour" spend entirely. A price
        // entry with a known weightG is now a "lot" with its own remaining
        // stock, decremented as ConsumptionRepository.log() draws from it
        // (see PriceRepository.deductStock) - only the initial purchase counts
        // toward Dépenses, not each day's consumption of it. Nullable/defaults
        // to weightG at insert time (see PriceRepository.log) - an entry with
        // no weight (e.g. a restaurant bill) has no stock concept at all.
        db.execSQL("ALTER TABLE price_log ADD COLUMN remainingG REAL")
        db.execSQL("UPDATE price_log SET remainingG = weightG")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v27 → v28: user-requested - "was it outdoors" for an activity, so the
        // dashboard can credit a rough vitamin D estimate for sun exposure
        // (see DashboardAggregator's VITD_OUTDOOR_UG). Defaults to 0 (false) so
        // every already-logged activity is treated as indoor, not a retroactive
        // vitD credit for sessions this flag didn't exist to ask about.
        db.execSQL("ALTER TABLE activity_log ADD COLUMN wasOutdoors INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v28 → v29: new `online_search_cache` table - user-requested persisted
        // "typing cache" for Recherche's online (Open Food Facts) search, so
        // instant suggestions from a prior session survive an app restart
        // instead of only living in FoodSearchViewModel's in-memory maps -
        // see OnlineSearchCacheEntity's own doc comment.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `online_search_cache` (" +
                "`barcode` TEXT NOT NULL, " +
                "`productJson` TEXT NOT NULL, " +
                "`auditJson` TEXT NOT NULL, " +
                "`sourceJson` TEXT NOT NULL, " +
                "`warningsJson` TEXT NOT NULL DEFAULT '[]', " +
                "`cachedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`barcode`))"
        )
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v29 → v30: new `recall_cache` table - barcode-keyed local cache for
        // RappelConso (official French government product-recall) lookups, so
        // a scan can be checked against real recall notices even offline once
        // previously fetched, and so repeat scans of the same barcode don't
        // re-hit the network every time - see RecallEntity's own doc comment.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recall_cache` (" +
                "`barcode` TEXT NOT NULL, " +
                "`found` INTEGER NOT NULL, " +
                "`productLabel` TEXT, " +
                "`recallReason` TEXT, " +
                "`risks` TEXT, " +
                "`publicationDate` TEXT, " +
                "`consumerInstructions` TEXT, " +
                "`recallSheetUrl` TEXT, " +
                "`checkedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`barcode`))"
        )
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v30 → v31: consumption_log gains an optional category (ProductCategory
        // enum name) - user-reported: a bottled water product logged via barcode
        // scan or the journal never showed up in the dedicated Hydration tab,
        // because ConsumptionRepository had no way to know a logged row was
        // BEVERAGE_WATER and auto-credit HydrationRepository's separate
        // DataStore-backed total. Existing rows default to NULL (unknown
        // category, same as toCheckProduct()'s old OTHER placeholder) - no
        // retroactive hydration credit is fabricated for already-logged water.
        db.execSQL("ALTER TABLE `consumption_log` ADD COLUMN `category` TEXT")
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v31 → v32: medications gains deactivatedAt - see MedicationEntity's
        // own doc comment. Existing rows default to NULL regardless of their
        // current `active` value (we have no historical record of when an
        // already-inactive row was actually deactivated) - adherence math
        // treats a NULL deactivatedAt on an inactive row as "always was active"
        // for any date at or before today, matching this migration's inability
        // to fabricate a real timestamp for pre-existing data, and matching the
        // pre-fix behavior exactly for rows that don't change state again.
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `deactivatedAt` INTEGER")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v32 → v33: medications gains scheduleDaysMask/extraReminderTimes - see
        // MedicationEntity's own doc comment. Existing rows default to
        // scheduleDaysMask=0 (every day, matching their actual pre-migration
        // behavior exactly) and extraReminderTimes='' (no second/third dose
        // time), so no existing reminder's real-world behavior changes until
        // the user explicitly edits a medication's new schedule fields.
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `scheduleDaysMask` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `medications` ADD COLUMN `extraReminderTimes` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v33 → v34: new `pantry` table - a real persisted "what do I have at
        // home" inventory, distinct from `price_log` (financial/cost-attribution
        // lots, no expiry concept) and the grocery list (what to buy, not what's
        // already owned) - see PantryEntity's own doc comment.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pantry` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`barcode` TEXT, " +
                "`category` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, " +
                "`unit` TEXT NOT NULL, " +
                "`expiryDate` TEXT, " +
                "`addedAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pantry_profileId_expiryDate` ON `pantry` (`profileId`, `expiryDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pantry_barcode_profileId` ON `pantry` (`barcode`, `profileId`)")
    }
}

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v34 → v35: new `symptoms` table - a free-form symptom journal
        // (bloating, energy, sleep...) correlated against the diary, see
        // SymptomEntity's own doc comment.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `symptoms` (" +
                "`id` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`severity` INTEGER NOT NULL, " +
                "`notes` TEXT NOT NULL, " +
                "`loggedAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_symptoms_profileId_date` ON `symptoms` (`profileId`, `date`)")
    }
}

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // v35 → v36: new `nonfood_scans` table - see NonFoodScanEntity's own
        // doc comment. NonConsumableFound was dismiss-only until now.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nonfood_scans` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`barcode` TEXT, " +
                "`name` TEXT NOT NULL, " +
                "`brand` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`ingredientCount` INTEGER, " +
                "`complexity` TEXT, " +
                "`allergenCount` INTEGER NOT NULL, " +
                "`prohibitedCount` INTEGER NOT NULL, " +
                "`restrictedCount` INTEGER NOT NULL, " +
                "`scannedAt` INTEGER NOT NULL, " +
                "`profileId` TEXT NOT NULL, " +
                "`favorite` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_nonfood_scans_profileId_scannedAt` ON `nonfood_scans` (`profileId`, `scannedAt`)")
    }
}
