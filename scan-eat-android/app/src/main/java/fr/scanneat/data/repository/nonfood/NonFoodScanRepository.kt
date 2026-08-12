package fr.scanneat.data.repository.nonfood

import fr.scanneat.data.local.db.nonfood.NonFoodScanDao
import fr.scanneat.data.local.db.nonfood.NonFoodScanEntity
import fr.scanneat.domain.engine.nonconsumable.CosingMatch
import fr.scanneat.domain.engine.nonconsumable.CosmeticTransparencyResult
import fr.scanneat.domain.engine.nonconsumable.FormulaComplexity
import fr.scanneat.domain.engine.nonconsumable.NonConsumableCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class NonFoodScanItem(
    val id: Long,
    val barcode: String?,
    val name: String,
    val brand: String,
    val category: NonConsumableCategory,
    val ingredientCount: Int?,
    val complexity: FormulaComplexity?,
    val allergenCount: Int,
    val prohibitedCount: Int,
    val restrictedCount: Int,
    val scannedAt: Long,
    val favorite: Boolean,
    val ingredientsText: String?,
)

@Singleton
class NonFoodScanRepository @Inject constructor(
    private val dao: NonFoodScanDao,
) {
    /** Same cap every other log table in this app already trims to (see e.g. PriceRepository/ScanHistoryDao). */
    private companion object {
        const val MAX_HISTORY_ROWS = 500
    }

    fun observeAll(profileId: String = "default"): Flow<List<NonFoodScanItem>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

    fun observeFavorites(profileId: String = "default"): Flow<List<NonFoodScanItem>> =
        dao.observeFavorites(profileId).map { list -> list.map { it.toDomain() } }

    /**
     * Logs a scanned non-food product - see NonFoodScanEntity's own doc
     * comment on why this is a summary row (computed complexity/allergen/
     * regulatory counts), not re-parsed ingredientsText, mirroring how
     * ScanHistoryEntity stores score/grade rather than re-deriving them
     * every read.
     */
    suspend fun log(
        barcode: String?,
        name: String,
        brand: String,
        category: NonConsumableCategory,
        transparency: CosmeticTransparencyResult?,
        prohibited: List<CosingMatch>,
        restricted: List<CosingMatch>,
        profileId: String = "default",
        ingredientsText: String? = null,
    ) {
        dao.insert(
            NonFoodScanEntity(
                barcode = barcode,
                name = name,
                brand = brand,
                category = category.name,
                ingredientCount = transparency?.ingredientCount,
                complexity = transparency?.complexity?.name,
                allergenCount = transparency?.detectedAllergens?.size ?: 0,
                prohibitedCount = prohibited.size,
                restrictedCount = restricted.size,
                scannedAt = System.currentTimeMillis(),
                profileId = profileId,
                ingredientsText = ingredientsText,
            )
        )
        dao.trimNonFavorites(MAX_HISTORY_ROWS, profileId)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun exportAll(profileId: String = "default"): List<NonFoodScanEntity> = dao.getAllForBackup(profileId)
    suspend fun importAll(entities: List<NonFoodScanEntity>) {
        if (entities.isEmpty()) return
        dao.insertAll(entities)
    }
}

private fun NonFoodScanEntity.toDomain() = NonFoodScanItem(
    id = id,
    barcode = barcode,
    name = name,
    brand = brand,
    category = runCatching { NonConsumableCategory.valueOf(category) }.getOrDefault(NonConsumableCategory.OTHER),
    ingredientCount = ingredientCount,
    complexity = complexity?.let { runCatching { FormulaComplexity.valueOf(it) }.getOrNull() },
    allergenCount = allergenCount,
    prohibitedCount = prohibitedCount,
    restrictedCount = restrictedCount,
    scannedAt = scannedAt,
    favorite = favorite,
    ingredientsText = ingredientsText,
)
