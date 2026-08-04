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
    /** Same cap ConsumptionRepository/WeightRepository/ScanRepository already use. */
    private companion object {
        const val MAX_HISTORY_ROWS = 5000
    }

    fun observeAll(profileId: String = "default"): Flow<List<PriceEntry>> =
        dao.observeAll(profileId).map { list -> list.toDomainList() }

    fun observeRange(from: LocalDate, to: LocalDate, profileId: String = "default"): Flow<List<PriceEntry>> =
        dao.observeRange(from.toIsoString(), to.toIsoString(), profileId).map { list -> list.toDomainList() }

    /** Logs a purchase. pricePerKg is stored pre-computed; the value-score comparison
     *  itself happens later, at read time (see [toDomainList]). */
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
        // price_log was the one log table in the app with no retention cap at
        // all (app-audit §O1) - every sibling log table (consumption, weight,
        // activity, medication, scan history) already trims to MAX_HISTORY_ROWS.
        dao.trim(MAX_HISTORY_ROWS, profileId)
    }

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Corrects an existing entry in place (name/category/price/weight/date) - previously
     * the only write path after [log] was [delete], so fixing a typo'd price or a
     * mis-scanned category meant deleting the whole entry and re-adding it, losing its
     * original loggedAt ordering. Re-inserts with the same id (Room REPLACE == UPSERT
     * on the primary key) and preserves loggedAt/barcode/profileId from the row being
     * edited so its position in the log (loggedAt DESC) and its scan lineage don't change.
     */
    suspend fun update(
        id: String,
        date: LocalDate,
        productName: String,
        category: ProductCategory,
        priceEuros: Double,
        weightG: Double?,
    ) {
        val existing = dao.getById(id) ?: return
        val pricePerKg = if (weightG != null && weightG > 0.0) priceEuros / (weightG / 1000.0) else null
        dao.insert(
            existing.copy(
                date = date.toIsoString(),
                productName = productName,
                category = category.key,
                priceEuros = priceEuros,
                weightG = weightG,
                pricePerKg = pricePerKg,
            )
        )
    }

    /**
     * Maps a batch of rows to domain entries, computing each one's value score
     * against its category's own median price/kg within THIS SAME batch (≥3
     * same-category entries) rather than the generic EU-retail default -  a
     * real personal baseline beats a category-typical guess. Previously this
     * median comparison was only ever implemented as a separate suspend
     * function with zero callers anywhere in the app: every value-score badge
     * actually shown to a user silently used the generic default only,
     * regardless of how much of their own price history existed for that
     * category. Computed once per emission from the list [observeAll]/
     * [observeRange] already loaded, not a second DB query per category.
     */
    private fun List<PriceEntity>.toDomainList(): List<PriceEntry> {
        val medians = groupBy { it.category }
            .mapValues { (_, rows) -> rows.mapNotNull { it.pricePerKg } }
            .filterValues { it.size >= 3 }
            .mapValues { (_, prices) -> prices.sorted()[prices.size / 2] }
        return map { it.toDomain(medians) }
    }

    private fun PriceEntity.toDomain(medians: Map<String, Double>): PriceEntry {
        val cat = ProductCategory.fromKey(category)
        val reference = medians[category] ?: referencePricePerKg(cat)
        val score = pricePerKg?.let { valueScoreFor(it, reference) }
        return PriceEntry(
            id = id, date = date.toLocalDate(), productName = productName, barcode = barcode,
            category = cat, priceEuros = priceEuros, weightG = weightG, pricePerKg = pricePerKg,
            valueScore = score,
        )
    }
}
