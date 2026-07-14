package fr.scanneat.data.local.db.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Atomically finds the existing row for [barcode] (if any) and inserts
     * [build]'s result keyed off that row's id/favorite - previously
     * ScanRepository.persist() did the find and the insert as two separate
     * suspend calls with no transaction, so two concurrent scans of the same
     * barcode (e.g. instant-mode double-fire) could both read "no existing
     * row" and both insert, producing a duplicate the upsert-by-barcode
     * scheme was specifically meant to prevent (Room's own
     * OnConflictStrategy.REPLACE only catches PK/unique-constraint conflicts,
     * not this read-then-decide race, since a "new" row's id is always the
     * autogenerate placeholder 0 until this insert actually runs).
     */
    @Transaction
    suspend fun upsertByBarcode(
        barcode: String?,
        profileId: String,
        build: (existingId: Long, existingFavorite: Boolean) -> ScanHistoryEntity,
    ): Long {
        val existing = barcode?.let { findByBarcode(it, profileId) }
        return insert(build(existing?.id ?: 0, existing?.favorite ?: false))
    }

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM scan_history WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<ScanHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScanHistoryEntity>)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scan_history WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String = "default")

    @Query("SELECT COUNT(*) FROM scan_history WHERE profileId = :profileId AND scannedAt >= :fromMillis")
    fun observeCountSince(fromMillis: Long, profileId: String = "default"): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_history WHERE profileId = :profileId")
    fun observeTotalCount(profileId: String = "default"): Flow<Int>
}
