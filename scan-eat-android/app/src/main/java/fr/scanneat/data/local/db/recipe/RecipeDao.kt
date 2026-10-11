package fr.scanneat.data.local.db.recipe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RecipeEntity?

    /** Case-insensitive name lookup - ResultSaveDestinations uses this to detect an
     *  existing recipe by name instead of loading+filtering the whole table. */
    @Query("SELECT * FROM recipes WHERE profileId = :profileId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String, profileId: String = "default"): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecipeEntity)

    @Query("UPDATE recipes SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    // Same retention cap/pattern as ScanHistoryDao.trimNonFavorites - favorites
    // are exempt (a user's explicitly-kept recipes shouldn't silently vanish),
    // only the non-favorite tail beyond keepCount is pruned. Previously this
    // table had no cap at all despite carrying a componentsJson blob per row.
    @Query("""
        DELETE FROM recipes
        WHERE profileId = :profileId AND favorite = 0 AND id NOT IN (
            SELECT id FROM recipes WHERE profileId = :profileId AND favorite = 0
            ORDER BY createdAt DESC LIMIT :keepCount
        )
    """)
    suspend fun trimNonFavorites(keepCount: Int, profileId: String = "default")

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM recipes WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecipeEntity>)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)
}
