package fr.scanneat.data.repository.pantry

import fr.scanneat.data.local.db.pantry.PantryDao
import fr.scanneat.data.local.db.pantry.PantryEntity
import fr.scanneat.data.local.db.toIsoString
import fr.scanneat.data.local.db.toLocalDate
import fr.scanneat.domain.model.ProductCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class PantryUnit(val key: String) {
    GRAMS("g"), MILLILITERS("mL"), UNITS("unit");
    companion object {
        fun fromKey(k: String): PantryUnit = entries.firstOrNull { it.key == k } ?: UNITS
    }
}

data class PantryItem(
    val id: String,
    val name: String,
    val barcode: String?,
    val category: ProductCategory,
    val quantity: Double,
    val unit: PantryUnit,
    val expiryDate: LocalDate?,
    val addedAt: Long,
)

/**
 * User-requested: a real persisted pantry inventory - see PantryEntity's own
 * doc comment. Deliberately simple (add/adjust-quantity/delete, no lot/FIFO
 * concept like PriceRepository's cost-attribution stock) since this is about
 * "what do I have and is it about to expire", not financial accounting.
 */
@Singleton
class PantryRepository @Inject constructor(
    private val dao: PantryDao,
) {
    fun observeAll(profileId: String = "default"): Flow<List<PantryItem>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

    suspend fun add(
        name: String,
        barcode: String?,
        category: ProductCategory,
        quantity: Double,
        unit: PantryUnit,
        expiryDate: LocalDate?,
        profileId: String = "default",
    ): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            PantryEntity(
                id = id,
                name = name,
                barcode = barcode,
                category = category.key,
                quantity = quantity,
                unit = unit.key,
                expiryDate = expiryDate?.toIsoString(),
                addedAt = System.currentTimeMillis(),
                profileId = profileId,
            )
        )
        return id
    }

    /**
     * addOrUpdate mirrors ManualGroceryRepository's own convenience method -
     * re-scanning a product you already have on hand should top up its
     * existing row (matched by barcode when present, else by name within the
     * same profile) rather than create a visually-duplicate second entry for
     * the identical product.
     */
    suspend fun addOrUpdate(
        name: String,
        barcode: String?,
        category: ProductCategory,
        quantity: Double,
        unit: PantryUnit,
        expiryDate: LocalDate?,
        profileId: String = "default",
    ) {
        val current = dao.getAllForBackup(profileId).firstOrNull { entry ->
            if (barcode != null) entry.barcode == barcode else entry.name.equals(name, ignoreCase = true)
        }
        if (current != null) {
            dao.updateQuantity(current.id, current.quantity + quantity)
        } else {
            add(name, barcode, category, quantity, unit, expiryDate, profileId)
        }
    }

    suspend fun updateQuantity(id: String, quantity: Double) = dao.updateQuantity(id, quantity.coerceAtLeast(0.0))

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun getById(id: String): PantryItem? = dao.getById(id)?.toDomain()

    // ---- Backup export/import ----
    suspend fun exportAll(profileId: String = "default"): List<PantryEntity> = dao.getAllForBackup(profileId)
    suspend fun importAll(entities: List<PantryEntity>) {
        if (entities.isEmpty()) return
        dao.insertAll(entities)
    }
}

private fun PantryEntity.toDomain() = PantryItem(
    id = id,
    name = name,
    barcode = barcode,
    category = ProductCategory.fromKey(category),
    quantity = quantity,
    unit = PantryUnit.fromKey(unit),
    expiryDate = expiryDate?.toLocalDate(),
    addedAt = addedAt,
)
