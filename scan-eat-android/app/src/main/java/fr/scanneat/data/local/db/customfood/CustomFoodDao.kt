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

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM custom_foods WHERE name = :name AND profileId = :profileId")
    suspend fun deleteByName(name: String, profileId: String = "default")
}
