package fr.scanneat.data.local.db.weight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_log WHERE profileId = :profileId ORDER BY date ASC")
    fun observeAll(profileId: String = "default"): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight_log WHERE date = :date AND profileId = :profileId LIMIT 1")
    suspend fun findByDate(date: String, profileId: String = "default"): WeightEntity?

    @Query("SELECT * FROM weight_log WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WeightEntity?

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

    /**
     * Atomically resolves the existing row (if any) for [date]/[profileId] and
     * upserts [build]'s result, returning the pre-write row so the caller can
     * compare against it (e.g. WeightRepository.log()'s "did the value actually
     * change" check before mirroring to Health Connect). Without this
     * @Transaction, log() and syncFromHealthConnect() each did their own
     * un-transactioned find-then-decide over the same unique (date, profileId)
     * index — a manual save landing between another caller's find and insert
     * (concurrent Health Connect sync on app foreground is the realistic case)
     * could have its write silently clobbered by whichever upsert() ran last,
     * with no error and no trace. Same race class ScanHistoryDao.upsertByBarcode/
     * CustomFoodDao.upsertFood were fixed for.
     */
    @Transaction
    suspend fun upsertForDate(date: String, profileId: String, build: (existing: WeightEntity?) -> WeightEntity): WeightEntity? {
        val existing = findByDate(date, profileId)
        upsert(build(existing))
        return existing
    }

    /**
     * Atomically inserts [build]'s result only if [date]/[profileId] has no row
     * yet — closes the same race as [upsertForDate] for
     * WeightRepository.syncFromHealthConnect()'s "only fill genuinely empty
     * days" invariant: its bulk existingDates snapshot is just a fast-path
     * filter now, since the real decision happens inside this transaction.
     */
    @Transaction
    suspend fun insertIfAbsent(date: String, profileId: String, build: () -> WeightEntity): Boolean {
        if (findByDate(date, profileId) != null) return false
        upsert(build())
        return true
    }
}
