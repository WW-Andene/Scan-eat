package fr.scanneat.data.local.db.medication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE profileId = :profileId ORDER BY active DESC, name ASC")
    fun observeAll(profileId: String = "default"): Flow<List<MedicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MedicationEntity)

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun findById(id: String): MedicationEntity?

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM medications WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<MedicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MedicationEntity>)
}
