package fr.scanneat.data.local.db.price

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Query("SELECT * FROM price_log WHERE profileId = :profileId ORDER BY date DESC, loggedAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<PriceEntity>>

    @Query("SELECT * FROM price_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date DESC, loggedAt DESC")
    fun observeRange(from: String, to: String, profileId: String = "default"): Flow<List<PriceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PriceEntity)

    @Query("DELETE FROM price_log WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM price_log WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<PriceEntity>

    @Query("SELECT * FROM price_log WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PriceEntity?

    /** Oldest-first (FIFO) lots for this barcode still carrying stock - see
     *  PriceRepository.deductStock's own doc comment on why consumption draws
     *  from the earliest-bought lot first rather than the most recent. */
    @Query("SELECT * FROM price_log WHERE barcode = :barcode AND profileId = :profileId AND remainingG > 0 ORDER BY loggedAt ASC")
    suspend fun getLotsWithStock(barcode: String, profileId: String = "default"): List<PriceEntity>

    @Query("UPDATE price_log SET remainingG = :remainingG WHERE id = :id")
    suspend fun updateRemaining(id: String, remainingG: Double)

    /**
     * User-reported: changing the currency symbol left every already-logged
     * price at its old numeric value with just a new symbol slapped on it -
     * "10€" silently becoming "10$" instead of a converted equivalent. SQLite
     * propagates NULL through arithmetic automatically, so pricePerKg rows
     * that are NULL (no weight recorded) are left NULL rather than becoming 0.
     */
    @Query("UPDATE price_log SET priceEuros = priceEuros * :factor, pricePerKg = pricePerKg * :factor WHERE profileId = :profileId")
    suspend fun scaleAllPrices(factor: Double, profileId: String = "default")

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PriceEntity>)

    /** Bounds unbounded growth the same way ConsumptionDao.trim does for consumption_log -
     *  price_log was the one log table in the app missing this cap entirely. */
    @Query("""
        DELETE FROM price_log
        WHERE profileId = :profileId AND id NOT IN (
            SELECT id FROM price_log WHERE profileId = :profileId ORDER BY loggedAt DESC LIMIT :keepCount
        )
    """)
    suspend fun trim(keepCount: Int, profileId: String = "default")
}
