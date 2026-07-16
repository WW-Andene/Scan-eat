package fr.scanneat.data.local.db.medication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE profileId = :profileId ORDER BY active DESC, name ASC")
    fun observeAll(profileId: String = "default"): Flow<List<MedicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MedicationEntity)

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun findById(id: String): MedicationEntity?

    @Query("SELECT * FROM medications WHERE profileId = :profileId AND barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String, profileId: String = "default"): MedicationEntity?

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM medications WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<MedicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MedicationEntity>)

    /**
     * Atomically resolves [explicitId] (a known edit target), else an existing
     * row's id via a [barcode] match, else a fresh UUID, then upserts [build]'s
     * result — same "prefer a real product identity over creating a duplicate"
     * pattern as CustomFoodDao.upsertFood/ScanHistoryDao.upsertByBarcode.
     *
     * Scanning the same medication box a second time previously always created
     * a brand-new row (save() only reused an id when the caller explicitly
     * passed one, which ScanViewModel.saveDetectedMedication() never did) —
     * each duplicate got its own ReminderWorker-scheduled daily reminder, so a
     * rescanned medication silently doubled (or more) the user's reminders for
     * it. No name-based fallback here (unlike custom foods): two genuinely
     * different medications sharing a display name is plausible enough
     * (generic/brand name overlap) that guessing identity from name alone
     * would risk merging them, so only an unambiguous barcode match resolves
     * to an existing row.
     *
     * The find-then-insert runs inside this @Transaction so two concurrent
     * saves for the same barcode can't both read "no existing row" and both
     * insert a duplicate.
     */
    @Transaction
    suspend fun upsertMedication(
        explicitId: String?,
        barcode: String?,
        profileId: String,
        build: (resolvedId: String, createdAt: Long) -> MedicationEntity,
    ): MedicationEntity {
        val existing = explicitId?.let { findById(it) } ?: barcode?.let { findByBarcode(it, profileId) }
        val entity = build(existing?.id ?: explicitId ?: java.util.UUID.randomUUID().toString(), existing?.createdAt ?: System.currentTimeMillis())
        upsert(entity)
        return entity
    }
}
