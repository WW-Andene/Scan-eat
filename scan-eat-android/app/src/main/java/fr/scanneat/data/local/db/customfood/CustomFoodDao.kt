package fr.scanneat.data.local.db.customfood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Atomically resolves [explicitId] (a known rename/update target) or an
     * existing same-name row's id (case-insensitive, matching CustomFoodRepository's
     * prior Kotlin-side check exactly - not SQLite's COLLATE NOCASE, which only
     * folds ASCII and would silently stop recognizing accented-name collisions
     * like "Pâté" vs "pâté"), else a fresh UUID, then inserts [build]'s result.
     * Closes the same TOCTOU race ScanHistoryDao.upsertByBarcode was fixed for:
     * two concurrent calls with the same name and no explicit id previously
     * could both read "no existing row" and both insert, creating the exact
     * duplicate-name row CustomFoodRepository.save()'s own doc comment warns
     * breaks LazyColumn key uniqueness.
     */
    @Transaction
    suspend fun upsertByName(
        name: String,
        profileId: String,
        explicitId: String?,
        build: (resolvedId: String) -> CustomFoodEntity,
    ): CustomFoodEntity {
        val resolvedId = explicitId
            ?: getAllForBackup(profileId).firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            ?: java.util.UUID.randomUUID().toString()
        val entity = build(resolvedId)
        insert(entity)
        return entity
    }

    /**
     * Atomically renames [id] to [newName] unless another row already has that
     * name (case-insensitive) - same TOCTOU race as [upsertByName], and the
     * same "two rows sharing a name" hazard CustomFoodRepository.rename()'s own
     * doc comment describes. Returns false (no-op) on collision or a missing id.
     */
    @Transaction
    suspend fun renameIfNoCollision(id: String, newName: String): Boolean {
        val existing = findById(id) ?: return false
        val collision = getAllForBackup(existing.profileId).any { it.id != id && it.name.equals(newName, ignoreCase = true) }
        if (collision) return false
        insert(existing.copy(name = newName))
        return true
    }
}
