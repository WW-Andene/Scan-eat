package fr.scanneat.data.local.db.report

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MisclassificationReportDao {
    @Query("SELECT * FROM misclassification_reports WHERE profileId = :profileId ORDER BY reportedAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<MisclassificationReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MisclassificationReportEntity)

    @Query("DELETE FROM misclassification_reports WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM misclassification_reports WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<MisclassificationReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MisclassificationReportEntity>)
}
