package fr.scanneat.data.local.db.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OnlineSearchCacheDao {
    @Query("SELECT * FROM online_search_cache")
    suspend fun getAll(): List<OnlineSearchCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OnlineSearchCacheEntity>)

    // Keeps the cache bounded - see OnlineSearchCacheEntity's own doc comment
    // on why this table is fine to trim, unlike scan_history.
    @Query("DELETE FROM online_search_cache WHERE barcode NOT IN (SELECT barcode FROM online_search_cache ORDER BY cachedAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
