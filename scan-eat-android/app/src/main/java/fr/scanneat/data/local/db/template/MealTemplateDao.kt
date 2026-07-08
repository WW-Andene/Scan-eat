package fr.scanneat.data.local.db.template

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
