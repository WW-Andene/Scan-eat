package fr.scanneat.data.local.db.medication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_log WHERE date = :date AND profileId = :profileId ORDER BY takenAt ASC")
    fun observeByDate(date: String, profileId: String = "default"): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC")
    suspend fun getRange(from: String, to: String, profileId: String = "default"): List<MedicationLogEntity>

    /**
     * Flow counterpart to [getRange] — MedicationViewModel.weeklyAdherence/
     * adherenceStreak used to derive from a one-shot [getRange] call inside a
     * `.map` over the `medications` StateFlow, which is bound only to the
     * `medications` table: marking/undoing a dose (writes only to
     * `medication_log`) never triggered a re-emit, so both chart/streak went
     * stale until something unrelated touched `medications`. This Flow is
     * bound to `medication_log` itself, so it does invalidate on those writes.
     */
    @Query("SELECT * FROM medication_log WHERE date BETWEEN :from AND :to AND profileId = :profileId ORDER BY date ASC")
    fun observeRange(from: String, to: String, profileId: String = "default"): Flow<List<MedicationLogEntity>>

    @Query("SELECT * FROM medication_log WHERE medicationId = :medicationId AND date = :date AND profileId = :profileId LIMIT 1")
    suspend fun findByMedicationAndDate(medicationId: String, date: String, profileId: String = "default"): MedicationLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MedicationLogEntity)

    /**
     * Atomically inserts [build]'s result only if this medication has no log
     * entry for this day yet - insert() alone had no dedup guard the way
     * WeightDao.insertIfAbsent already has for the identical race class, so a
     * fast double-tap on "mark taken" (before the todayTaken StateFlow
     * recomposes and disables the button) could create two log rows for the
     * same medication/day.
     */
    @Transaction
    suspend fun insertIfAbsent(medicationId: String, date: String, profileId: String, build: () -> MedicationLogEntity): Boolean {
        if (findByMedicationAndDate(medicationId, date, profileId) != null) return false
        insert(build())
        return true
    }

    @Query("DELETE FROM medication_log WHERE id = :id")
    suspend fun delete(id: String)

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM medication_log WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<MedicationLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MedicationLogEntity>)
}
