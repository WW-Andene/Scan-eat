package fr.scanneat.data.local.db.recall

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecallDao {
    @Query("SELECT * FROM recall_cache WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): RecallEntity?

    /** Barcodes already confirmed recalled in the local cache - reactive, and
     *  deliberately cache-only (never triggers a network check): a pantry
     *  item only gets flagged here once some scan of it has already checked
     *  RappelConso, same "never proactively bulk-query an external API for a
     *  list view" discipline as everywhere else this cache is read from. */
    @Query("SELECT barcode FROM recall_cache WHERE found = 1")
    fun observeRecalledBarcodes(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecallEntity)

    // Keeps the negative-result side of the cache bounded and fresh - a
    // positive hit (found=true) is a real safety record, kept forever like
    // scan_history; only the "checked, nothing found" rows are pruned.
    @Query("DELETE FROM recall_cache WHERE found = 0 AND checkedAt < :olderThan")
    suspend fun trimStaleNegatives(olderThan: Long)
}
