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

    @Query("UPDATE pantry SET quantity = :quantity, unit = :unit, expiryDate = :expiryDate, category = :category WHERE id = :id")
    suspend fun updateDetails(id: String, quantity: Double, unit: String, expiryDate: String?, category: String)

    @Query("DELETE FROM pantry WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM pantry WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PantryEntity?

    @Query("SELECT * FROM pantry WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<PantryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PantryEntity>)

    // Targeted lookups for addOrUpdate/deductStock - both used to scan the
    // whole table (getAllForBackup, meant for bulk export) on every single
    // write, which meant every meal log or re-scan paid an O(pantry size)
    // cost just to find one matching row.
    @Query("SELECT * FROM pantry WHERE profileId = :profileId AND barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String, profileId: String = "default"): PantryEntity?

    @Query("SELECT * FROM pantry WHERE profileId = :profileId AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String, profileId: String = "default"): PantryEntity?

    // Barcode-less rows only - used as the name-fallback when the incoming
    // item DOES have a barcode, so a barcoded row that just happens to share
    // a name never wins over an exact barcode match (see addOrUpdate).
    @Query("SELECT * FROM pantry WHERE profileId = :profileId AND barcode IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByNameNoBarcode(name: String, profileId: String = "default"): PantryEntity?
}
