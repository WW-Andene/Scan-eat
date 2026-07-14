package fr.scanneat.data.local.db.customfood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_foods WHERE profileId = :profileId ORDER BY name ASC")
    fun observeAll(profileId: String = "default"): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_foods WHERE id = :id")
    suspend fun findById(id: String): CustomFoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomFoodEntity)

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM custom_foods WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<CustomFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CustomFoodEntity>)

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: String)
}
