package fr.scanneat.data.local.db.activity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_log WHERE date = :date AND profileId = :profileId ORDER BY loggedAt ASC")
    fun observeByDate(date: String, profileId: String = "default"): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC")
    suspend fun getRange(from: String, to: String, profileId: String = "default"): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityEntity): Long

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM activity_log WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ActivityEntity>)

    @Query("DELETE FROM activity_log WHERE id = :id")
    suspend fun delete(id: String)
}
