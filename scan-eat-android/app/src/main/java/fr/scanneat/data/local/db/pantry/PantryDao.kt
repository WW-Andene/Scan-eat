package fr.scanneat.data.local.db.pantry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    // Nulls-last so undated items don't scatter randomly among dated ones -
    // SQLite sorts NULL first by default, which would otherwise push every
    // never-dated item (a very common case: most staples) above genuinely
    // soon-to-expire ones.
    @Query("SELECT * FROM pantry WHERE profileId = :profileId ORDER BY (expiryDate IS NULL), expiryDate ASC, name ASC")
    fun observeAll(profileId: String = "default"): Flow<List<PantryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PantryEntity)

    @Query("UPDATE pantry SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Double)

    @Query("DELETE FROM pantry WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM pantry WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PantryEntity?

    @Query("SELECT * FROM pantry WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<PantryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PantryEntity>)
}
