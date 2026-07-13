package fr.scanneat.data.local.db.medication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_log WHERE date = :date AND profileId = :profileId ORDER BY takenAt ASC")
    fun observeByDate(date: String, profileId: String = "default"): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC")
    suspend fun getRange(from: String, to: String, profileId: String = "default"): List<MedicationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MedicationLogEntity)

    @Query("DELETE FROM medication_log WHERE id = :id")
    suspend fun delete(id: String)

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM medication_log WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<MedicationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MedicationLogEntity>)
}
