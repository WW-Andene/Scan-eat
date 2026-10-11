package fr.scanneat.data.local.db.mood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_log WHERE profileId = :profileId ORDER BY date DESC")
    fun observeAll(profileId: String = "default"): Flow<List<MoodEntity>>

    @Query("SELECT * FROM mood_log WHERE date = :date AND profileId = :profileId LIMIT 1")
    suspend fun findByDate(date: String, profileId: String = "default"): MoodEntity?

    /** Upsert: Room INSERT OR REPLACE on unique (date, profileId) index - same convention SleepDao.upsert uses. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MoodEntity)

    @Query("DELETE FROM mood_log WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM mood_log WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<MoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MoodEntity>)

    /** Same unbounded-growth cap every other log table in this app already applies. */
    @Query("""
        DELETE FROM mood_log
        WHERE profileId = :profileId AND id NOT IN (
            SELECT id FROM mood_log WHERE profileId = :profileId ORDER BY date DESC LIMIT :keepCount
        )
    """)
    suspend fun trim(keepCount: Int, profileId: String = "default")
}
