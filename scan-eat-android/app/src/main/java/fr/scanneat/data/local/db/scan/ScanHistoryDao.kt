package fr.scanneat.data.local.db.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Row shape for [ScanHistoryDao.topScanned] - see that query's own doc comment. */
data class TopScannedRow(val productName: String, val cnt: Int, val dbId: Long)

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history WHERE profileId = :profileId ORDER BY scannedAt DESC LIMIT :limit")
    fun observeRecent(profileId: String = "default", limit: Int = 50): Flow<List<ScanHistoryEntity>>

    /**
     * Same as [observeRecent] but excludes a legacy pre-classifyNonFood() row the
     * user hasn't personally rescanned yet - for CustomFoodViewModel.latestScan,
     * whose "import this scan's nutrition as a custom food" affordance would
     * otherwise happily turn a stale lubricant/cosmetic entry's meaningless
     * "nutrition" into a permanent custom_foods row. Unlike observeRecent (the
     * plain History feed, which should keep showing everything scanned,
     * verified or not), this call site actively creates new data FROM the row,
     * so it needs the same guard findBetterInCategory already has.
     */
    @Query("SELECT * FROM scan_history WHERE profileId = :profileId AND nonFoodChecked = 1 ORDER BY scannedAt DESC LIMIT :limit")
    fun observeRecentChecked(profileId: String = "default", limit: Int = 50): Flow<List<ScanHistoryEntity>>

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
     * Up to 5 best-scoring products previously scanned in the same category
     * that beat the current score, best first. Only Scan'eat's own history is
     * queried — not a live product-database search — so the suggestion is
     * always something the user has actually already found, never a
     * fabricated "somewhere near you" claim.
     *
     * Returns a small pool rather than the single best match (LIMIT 1, the
     * previous shape) — ScanRepository.findBetterAlternative() needs to skip
     * over any candidate that itself conflicts with the user's declared
     * allergens/diet, which this query has no way to check (it doesn't decode
     * productJson at all). Surfacing only the single top score meant a
     * "better alternative" could — and did — recommend a product containing
     * the user's own allergen, since nothing downstream ever re-checked it.
     *
     * nonFoodChecked = 1 excludes a legacy pre-classifyNonFood() row the user
     * hasn't personally rescanned yet - otherwise a stale lubricant/cosmetic
     * entry sitting in someone's history (score irrelevant, since it was never
     * really "scored" as food) could get recommended as the healthy pick over
     * a real product, right up until its own barcode happens to get rescanned.
     */
    @Query("""
        SELECT * FROM scan_history
        WHERE profileId = :profileId AND category = :category AND score > :minScore
          AND (:excludeBarcode IS NULL OR barcode IS NULL OR barcode != :excludeBarcode)
          AND nonFoodChecked = 1
        ORDER BY score DESC LIMIT 5
    """)
    suspend fun findBetterInCategory(
        category: String,
        minScore: Int,
        excludeBarcode: String?,
        profileId: String = "default",
    ): List<ScanHistoryEntity>

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

    /**
     * Purges a barcode confirmed non-food by a re-verification lookup - see
     * ScanRepository.scoreBarcode's cache-hit path. Unlike a normal rescan (which
     * upserts in place), this row should never have existed as a scored food item
     * at all, so it's removed outright rather than kept around for
     * findBetterInCategory/observeTopScanned to keep surfacing.
     */
    @Query("DELETE FROM scan_history WHERE barcode = :barcode AND profileId = :profileId")
    suspend fun deleteByBarcode(barcode: String, profileId: String = "default")

    @Query("DELETE FROM scan_history WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String = "default")

    @Query("SELECT COUNT(*) FROM scan_history WHERE profileId = :profileId AND scannedAt >= :fromMillis")
    fun observeCountSince(fromMillis: Long, profileId: String = "default"): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_history WHERE profileId = :profileId")
    fun observeTotalCount(profileId: String = "default"): Flow<Int>

    /**
     * Top-N most-frequently-scanned products, counted from the append-only
     * scan_score_history log (one row per persist() call, never REPLACEd) —
     * previously "Top Scanned" grouped the barcode-upserted scan_history table
     * itself, where a rescan REPLACEs the same row in place, so any barcoded
     * product's count could never exceed 1 and the tile only ever surfaced
     * barcode-less (photo-identified) products.
     *
     * The count is aggregated from scan_score_history *first*, in its own
     * subquery, before scan_history is touched at all. Joining the two tables
     * directly (the previous approach) fanned out for every barcode-less
     * product: unlike scan_history's barcode path (upsertByBarcode reuses the
     * one existing row), a barcode-less scan always inserts a brand-new
     * scan_history row, so N repeat scans of the same unbarcoded product left
     * N matching scan_history rows and N matching scan_score_history rows
     * sharing one matchKey — a direct join's cross product reported N² instead
     * of N. The correlated subquery below picks exactly one scan_history row
     * (the most recent) per matchKey purely to resolve a display name/dbId for
     * the tap target, so the pre-aggregated cnt is never multiplied.
     */
    @Query("""
        SELECT display.productName as productName, agg.cnt as cnt, display.id as dbId
        FROM (
            SELECT matchKey, COUNT(*) as cnt
            FROM scan_score_history
            WHERE profileId = :profileId
            GROUP BY matchKey
        ) agg
        JOIN scan_history display
          ON display.id = (
              SELECT sh.id FROM scan_history sh
              WHERE sh.profileId = :profileId
                AND ((sh.barcode IS NOT NULL AND sh.barcode = agg.matchKey) OR (sh.barcode IS NULL AND LOWER(sh.productName) = agg.matchKey))
              ORDER BY sh.scannedAt DESC LIMIT 1
          )
        ORDER BY agg.cnt DESC
        LIMIT :limit
    """)
    fun observeTopScanned(profileId: String = "default", limit: Int = 3): Flow<List<TopScannedRow>>

    /**
     * Name/barcode search over the *entire* history, not just whatever window
     * ScanHistoryViewModel happens to have loaded via observeRecent's LIMIT -
     * previously "search" only ever filtered the already-loaded 200-row (or
     * loadMore-expanded) window client-side, so searching for a genuinely old
     * scan not yet loaded found nothing until the user kept hitting "load more"
     * enough times to reach it. Bounded at 300 since this is a user-triggered
     * lookup, not the main feed - generous for a search result list.
     */
    @Query("""
        SELECT * FROM scan_history
        WHERE profileId = :profileId AND (productName LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%')
        ORDER BY scannedAt DESC LIMIT 300
    """)
    fun searchByName(query: String, profileId: String = "default"): Flow<List<ScanHistoryEntity>>

    /**
     * Trims non-favorite rows for [profileId] down to the most recent [keepCount],
     * oldest first - scan_history grows unbounded forever otherwise (persist()
     * upserts by barcode so *repeat* scans of the same product don't grow the
     * table, but distinct products do, with no age/count-based maintenance
     * previously available besides the full per-profile clearAll wipe).
     * Favorites are never trimmed - they're a user's deliberately curated subset.
     */
    @Query("""
        DELETE FROM scan_history
        WHERE profileId = :profileId AND favorite = 0 AND id NOT IN (
            SELECT id FROM scan_history WHERE profileId = :profileId AND favorite = 0
            ORDER BY scannedAt DESC LIMIT :keepCount
        )
    """)
    suspend fun trimNonFavorites(keepCount: Int, profileId: String = "default")
}
