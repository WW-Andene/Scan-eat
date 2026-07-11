package fr.scanneat.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.activity.ActivityEntity
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.customfood.CustomFoodDao
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.recipe.RecipeEntity
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
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
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun consumptionDao(): ConsumptionDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weightDao(): WeightDao
    abstract fun activityDao(): ActivityDao
    abstract fun mealTemplateDao(): MealTemplateDao
    abstract fun recipeDao(): RecipeDao
}

// ── Room migrations ────────────────────────────────────────────────────────────
// Add a new Migration object for each schema version bump before shipping.
// Schema versions are defined on @Database(version = N).
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
