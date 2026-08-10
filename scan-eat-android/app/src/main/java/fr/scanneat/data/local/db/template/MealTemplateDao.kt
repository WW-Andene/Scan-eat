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

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM meal_templates WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<MealTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MealTemplateEntity>)

    @Query("DELETE FROM meal_templates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE meal_templates SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    // Same retention cap/pattern as RecipeDao.trimNonFavorites - previously
    // this table had no cap at all despite carrying an itemsJson blob per row.
    @Query("""
        DELETE FROM meal_templates
        WHERE profileId = :profileId AND favorite = 0 AND id NOT IN (
            SELECT id FROM meal_templates WHERE profileId = :profileId AND favorite = 0
            ORDER BY createdAt DESC LIMIT :keepCount
        )
    """)
    suspend fun trimNonFavorites(keepCount: Int, profileId: String = "default")
}
