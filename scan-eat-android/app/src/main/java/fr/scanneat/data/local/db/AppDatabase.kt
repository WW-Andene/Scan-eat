package fr.scanneat.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 3,
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
        // v2 → v3: added RecipeEntity
        // Already handled via fallbackToDestructiveMigration during development.
        // Populate with real ALTER TABLE / CREATE TABLE statements for production.
    }
}
