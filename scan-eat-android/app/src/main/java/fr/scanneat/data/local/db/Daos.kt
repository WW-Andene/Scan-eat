package fr.scanneat.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ============================================================================
// DAOS
// ============================================================================

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history WHERE profileId = :profileId ORDER BY scannedAt DESC LIMIT :limit")
    fun observeRecent(profileId: String = "default", limit: Int = 50): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ScanHistoryEntity?

    @Query("SELECT * FROM scan_history WHERE barcode = :barcode AND profileId = :profileId ORDER BY scannedAt DESC LIMIT 1")
    suspend fun findByBarcode(barcode: String, profileId: String = "default"): ScanHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanHistoryEntity): Long

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scan_history WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String = "default")
}

@Dao
interface ConsumptionDao {
    @Query("SELECT * FROM consumption_log WHERE date = :date AND profileId = :profileId ORDER BY loggedAt ASC")
    fun observeByDate(date: String, profileId: String = "default"): Flow<List<ConsumptionEntity>>

    @Query("SELECT * FROM consumption_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC, loggedAt ASC")
    suspend fun getRange(from: String, to: String, profileId: String = "default"): List<ConsumptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConsumptionEntity): Long

    @Update
    suspend fun update(entity: ConsumptionEntity)

    @Query("DELETE FROM consumption_log WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_log WHERE profileId = :profileId ORDER BY date ASC")
    fun observeAll(profileId: String = "default"): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight_log WHERE date = :date AND profileId = :profileId LIMIT 1")
    suspend fun findByDate(date: String, profileId: String = "default"): WeightEntity?

    /** Upsert: Room INSERT OR REPLACE on unique date index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeightEntity)

    @Query("DELETE FROM weight_log WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM weight_log WHERE profileId = :profileId ORDER BY date DESC LIMIT :n")
    suspend fun getRecent(n: Int, profileId: String = "default"): List<WeightEntity>
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_log WHERE date = :date AND profileId = :profileId ORDER BY loggedAt ASC")
    fun observeByDate(date: String, profileId: String = "default"): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC")
    suspend fun getRange(from: String, to: String, profileId: String = "default"): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityEntity): Long

    @Query("DELETE FROM activity_log WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MealTemplateDao {
    @Query("SELECT * FROM meal_templates WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<MealTemplateEntity>>

    @Query("SELECT * FROM meal_templates WHERE id = :id")
    suspend fun findById(id: String): MealTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MealTemplateEntity)

    @Query("DELETE FROM meal_templates WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_foods WHERE profileId = :profileId ORDER BY name ASC")
    fun observeAll(profileId: String = "default"): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_foods WHERE id = :id")
    suspend fun findById(id: String): CustomFoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomFoodEntity)

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)
}
