package fr.scanneat.data.local.db.sleep

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_log WHERE profileId = :profileId ORDER BY date DESC")
    fun observeAll(profileId: String = "default"): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep_log WHERE date = :date AND profileId = :profileId LIMIT 1")
    suspend fun findByDate(date: String, profileId: String = "default"): SleepEntity?

    /** Upsert: Room INSERT OR REPLACE on unique (date, profileId) index - same convention WeightDao.upsert uses. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SleepEntity)

    @Query("DELETE FROM sleep_log WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM sleep_log WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<SleepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SleepEntity>)

    /** Same unbounded-growth cap every other log table in this app already applies. */
    @Query("""
        DELETE FROM sleep_log
        WHERE profileId = :profileId AND id NOT IN (
            SELECT id FROM sleep_log WHERE profileId = :profileId ORDER BY date DESC LIMIT :keepCount
        )
    """)
    suspend fun trim(keepCount: Int, profileId: String = "default")
}
