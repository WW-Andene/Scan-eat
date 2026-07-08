package fr.scanneat.data.local.db.consumption

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumptionDao {
    @Query("SELECT * FROM consumption_log WHERE date = :date AND profileId = :profileId ORDER BY loggedAt ASC")
    fun observeByDate(date: String, profileId: String = "default"): Flow<List<ConsumptionEntity>>

    @Query("SELECT * FROM consumption_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC, loggedAt ASC")
    suspend fun getRange(from: String, to: String, profileId: String = "default"): List<ConsumptionEntity>

    @Query("SELECT * FROM consumption_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC, loggedAt ASC")
    fun observeRange(from: String, to: String, profileId: String = "default"): Flow<List<ConsumptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConsumptionEntity): Long

    @Update
    suspend fun update(entity: ConsumptionEntity)

    @Query("DELETE FROM consumption_log WHERE id = :id")
    suspend fun delete(id: Long)
}
