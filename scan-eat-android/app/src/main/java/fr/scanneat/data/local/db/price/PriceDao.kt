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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PriceEntity>)
}
