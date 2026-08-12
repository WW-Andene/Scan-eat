package fr.scanneat.data.local.db.nonfood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NonFoodScanDao {
    @Query("SELECT * FROM nonfood_scans WHERE profileId = :profileId ORDER BY scannedAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<NonFoodScanEntity>>

    @Query("SELECT * FROM nonfood_scans WHERE profileId = :profileId AND favorite = 1 ORDER BY scannedAt DESC")
    fun observeFavorites(profileId: String = "default"): Flow<List<NonFoodScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NonFoodScanEntity): Long

    @Query("UPDATE nonfood_scans SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM nonfood_scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM nonfood_scans WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<NonFoodScanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NonFoodScanEntity>)

    // Same non-favorite-only trim discipline as ScanHistoryDao.trimNonFavorites -
    // see its own doc comment.
    @Query("""
        DELETE FROM nonfood_scans
        WHERE profileId = :profileId AND favorite = 0 AND id NOT IN (
            SELECT id FROM nonfood_scans WHERE profileId = :profileId AND favorite = 0
            ORDER BY scannedAt DESC LIMIT :keepCount
        )
    """)
    suspend fun trimNonFavorites(keepCount: Int, profileId: String = "default")
}
