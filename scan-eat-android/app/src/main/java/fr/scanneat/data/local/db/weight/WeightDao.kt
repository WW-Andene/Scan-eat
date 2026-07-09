package fr.scanneat.data.local.db.weight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_log WHERE profileId = :profileId ORDER BY date ASC")
    fun observeAll(profileId: String = "default"): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight_log WHERE date = :date AND profileId = :profileId LIMIT 1")
    suspend fun findByDate(date: String, profileId: String = "default"): WeightEntity?

    /** Upsert: Room INSERT OR REPLACE on unique date index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeightEntity)

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM weight_log WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<WeightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WeightEntity>)

    @Query("DELETE FROM weight_log WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM weight_log WHERE profileId = :profileId ORDER BY date DESC LIMIT :n")
    suspend fun getRecent(n: Int, profileId: String = "default"): List<WeightEntity>
}
