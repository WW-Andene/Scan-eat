package fr.scanneat.data.local.db.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history WHERE profileId = :profileId ORDER BY scannedAt DESC LIMIT :limit")
    fun observeRecent(profileId: String = "default", limit: Int = 50): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ScanHistoryEntity?

    @Query("SELECT * FROM scan_history WHERE barcode = :barcode AND profileId = :profileId ORDER BY scannedAt DESC LIMIT 1")
    suspend fun findByBarcode(barcode: String, profileId: String = "default"): ScanHistoryEntity?

    // Previously unbounded (no LIMIT) unlike observeRecent above — a heavy user
    // who favorites hundreds of products over years re-ran a full, unpaginated
    // query on every single insert/favorite toggle, holding the whole result set
    // in memory. 200 is generous for a "favorites" list (by definition a curated
    // subset) while still bounding the query and the LazyColumn behind it.
    @Query("SELECT * FROM scan_history WHERE profileId = :profileId AND favorite = 1 ORDER BY scannedAt DESC LIMIT 200")
    fun observeFavorites(profileId: String = "default"): Flow<List<ScanHistoryEntity>>

    /**
     * Best-scoring product previously scanned in the same category, if any beats
     * the current score. Only Scan'eat's own history is queried — not a live
     * product-database search — so the suggestion is always something the user
     * has actually already found, never a fabricated "somewhere near you" claim.
     */
    @Query("""
        SELECT * FROM scan_history
        WHERE profileId = :profileId AND category = :category AND score > :minScore
          AND (:excludeBarcode IS NULL OR barcode IS NULL OR barcode != :excludeBarcode)
        ORDER BY score DESC LIMIT 1
    """)
    suspend fun findBetterInCategory(
        category: String,
        minScore: Int,
        excludeBarcode: String?,
        profileId: String = "default",
    ): ScanHistoryEntity?

    @Query("UPDATE scan_history SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanHistoryEntity): Long

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM scan_history WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<ScanHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScanHistoryEntity>)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scan_history WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String = "default")
}
