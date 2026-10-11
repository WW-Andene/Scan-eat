package fr.scanneat.data.local.db.price

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One "I paid X for this" entry, logged manually from the Result screen after a
 * scan (no OCR price-tag detection - unreliable to build honestly, so this is
 * scoped as manual entry only, same disclosure standard already applied to
 * MicronutrientEstimator's category-typical fallbacks). pricePerKg is stored
 * pre-computed (not derived at read time) since weightG can be null for
 * products with no declared net weight, in which case pricePerKg is also null
 * and the value-score comparison is simply skipped for that entry.
 */
@Entity(
    tableName = "price_log",
    indices = [
        Index(value = ["date", "profileId"]),
        Index(value = ["barcode", "profileId"]),
    ],
)
data class PriceEntity(
    @PrimaryKey val id: String,
    val date: String,              // ISO yyyy-MM-dd
    val productName: String,
    val barcode: String?,
    val category: String,          // ProductCategory.key
    val priceEuros: Double,
    val weightG: Double?,
    val pricePerKg: Double?,       // null when weightG is null/zero
    val loggedAt: Long,            // epoch millis
    val profileId: String = "default",
    // User-requested stock tracking: starts equal to weightG (null if weightG
    // is null - no weight means no stock concept), decremented by
    // PriceRepository.deductStock as ConsumptionRepository.log() draws
    // portions from this lot. Only this purchase itself counts toward
    // Dépenses - a diary entry logged against a lot with remaining stock
    // deducts silently instead of creating a second price_log row.
    val remainingG: Double? = null,
)
