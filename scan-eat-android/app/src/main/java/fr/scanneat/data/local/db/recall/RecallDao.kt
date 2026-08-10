package fr.scanneat.data.local.db.recall

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecallDao {
    @Query("SELECT * FROM recall_cache WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): RecallEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecallEntity)

    // Keeps the negative-result side of the cache bounded and fresh - a
    // positive hit (found=true) is a real safety record, kept forever like
    // scan_history; only the "checked, nothing found" rows are pruned.
    @Query("DELETE FROM recall_cache WHERE found = 0 AND checkedAt < :olderThan")
    suspend fun trimStaleNegatives(olderThan: Long)
}
