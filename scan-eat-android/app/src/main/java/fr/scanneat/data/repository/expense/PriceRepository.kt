package fr.scanneat.data.repository.expense

import fr.scanneat.data.local.db.price.PriceDao
import fr.scanneat.data.local.db.price.PriceEntity
import fr.scanneat.data.local.db.toIsoString
import fr.scanneat.data.local.db.toLocalDate
import fr.scanneat.domain.engine.expense.ValueScore
import fr.scanneat.domain.engine.expense.referencePricePerKg
import fr.scanneat.domain.engine.expense.valueScoreFor
import fr.scanneat.domain.model.ProductCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PriceEntry(
    val id: String,
    val date: LocalDate,
    val productName: String,
    val barcode: String?,
    val category: ProductCategory,
    val priceEuros: Double,
    val weightG: Double?,
    val pricePerKg: Double?,
    val valueScore: ValueScore?,
)

@Singleton
class PriceRepository @Inject constructor(
    private val dao: PriceDao,
) {
    fun observeAll(profileId: String = "default"): Flow<List<PriceEntry>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

    fun observeRange(from: LocalDate, to: LocalDate, profileId: String = "default"): Flow<List<PriceEntry>> =
        dao.observeRange(from.toIsoString(), to.toIsoString(), profileId).map { list -> list.map { it.toDomain() } }

    /**
     * Logs a purchase. The value-score comparison prefers the user's own median
     * price/kg for this category once at least 3 prior entries exist (a real
     * personal baseline beats a generic EU-retail estimate) and falls back to
     * [referencePricePerKg] otherwise — same cold-start pattern as
     * MicronutrientEstimator's category defaults.
     */
    suspend fun log(
        date: LocalDate,
        productName: String,
        barcode: String?,
        category: ProductCategory,
        priceEuros: Double,
        weightG: Double?,
        profileId: String = "default",
    ) {
        val pricePerKg = if (weightG != null && weightG > 0.0) priceEuros / (weightG / 1000.0) else null
        dao.insert(
            PriceEntity(
                id = UUID.randomUUID().toString(),
                date = date.toIsoString(),
                productName = productName,
                barcode = barcode,
                category = category.key,
                priceEuros = priceEuros,
                weightG = weightG,
                pricePerKg = pricePerKg,
                loggedAt = System.currentTimeMillis(),
                profileId = profileId,
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Same category-median-if-enough-data-else-category-default policy [log]'s value score uses, exposed for the Result screen's live preview before saving. */
    suspend fun valueScoreFor(category: ProductCategory, pricePerKg: Double, profileId: String = "default"): ValueScore {
        val sameCategory = dao.findByCategory(category.key, profileId).mapNotNull { it.pricePerKg }
        val reference = if (sameCategory.size >= 3) sameCategory.sorted()[sameCategory.size / 2] else referencePricePerKg(category)
        return valueScoreFor(pricePerKg, reference)
    }

    private fun PriceEntity.toDomain(): PriceEntry {
        val cat = ProductCategory.fromKey(category)
        val score = pricePerKg?.let { valueScoreFor(it, referencePricePerKg(cat)) }
        return PriceEntry(
            id = id, date = date.toLocalDate(), productName = productName, barcode = barcode,
            category = cat, priceEuros = priceEuros, weightG = weightG, pricePerKg = pricePerKg,
            valueScore = score,
        )
    }
}
