package fr.scanneat.data.local.db.offline

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Roadmap N9: offline pack - the most scanned French products from Open Food
 * Facts (Search-a-licious), stored as the same JSON shape as a v2 product so
 * the existing mapper scores them unchanged when the network is unavailable.
 */
@Entity(tableName = "offline_products")
data class OfflineProductEntity(
    @PrimaryKey val barcode: String,
    val json: String,
    val downloadedAt: Long,
)

@Dao
interface OfflineProductDao {
    @Query("SELECT * FROM offline_products WHERE barcode IN (:barcodes) LIMIT 1")
    suspend fun findAny(barcodes: List<String>): OfflineProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<OfflineProductEntity>)

    @Query("SELECT COUNT(*) FROM offline_products")
    suspend fun count(): Int

    @Query("DELETE FROM offline_products")
    suspend fun clear()
}
