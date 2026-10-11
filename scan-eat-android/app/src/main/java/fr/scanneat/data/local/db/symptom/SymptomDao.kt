package fr.scanneat.data.local.db.symptom

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptoms WHERE profileId = :profileId ORDER BY date DESC, loggedAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<SymptomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SymptomEntity)

    @Query("DELETE FROM symptoms WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM symptoms WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<SymptomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SymptomEntity>)
}
