package fr.scanneat.data.local.db.reaction

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Roadmap N10: allergic-reaction log ("réaction le 12/10 après X"). The
 * product's ingredient list is snapshotted at logging time so the
 * common-ingredient analysis keeps working if the scan is later deleted.
 */
@Entity(tableName = "allergic_reactions", indices = [Index(value = ["profileId", "date"])])
data class AllergicReactionEntity(
    @PrimaryKey val id: String,
    val date: String,              // ISO yyyy-MM-dd
    val productName: String,
    val barcode: String?,
    val ingredientsText: String,   // comma-separated ingredient names at logging time
    val symptoms: String,
    val severity: Int,             // 1 mild, 2 moderate, 3 severe
    val epinephrineUsed: Boolean,
    val notes: String,
    val loggedAt: Long,
    val profileId: String = "default",
)

@Dao
interface AllergicReactionDao {
    @Query("SELECT * FROM allergic_reactions WHERE profileId = :profileId ORDER BY date DESC, loggedAt DESC")
    fun observeAll(profileId: String = "default"): Flow<List<AllergicReactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AllergicReactionEntity)

    @Query("DELETE FROM allergic_reactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM allergic_reactions")
    suspend fun getAllForBackup(): List<AllergicReactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AllergicReactionEntity>)
}
