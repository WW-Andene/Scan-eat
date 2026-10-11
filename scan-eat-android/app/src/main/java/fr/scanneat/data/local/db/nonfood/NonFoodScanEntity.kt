package fr.scanneat.data.local.db.nonfood

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-requested: non-food products (shampoo, gel douche, cosmétiques...)
 * previously had no history at all - the NonConsumableFound dialog was
 * dismiss-only, unlike every food scan which always persists to
 * scan_history. Deliberately a separate table, not a repurposed
 * ScanHistoryEntity row - that entity's productJson/auditJson/score/grade
 * columns are shaped around a food Product+ScoreAudit, and a shampoo has
 * neither; forcing one into the other would mean fabricating a fake
 * Product just to satisfy the schema. This instead stores exactly what
 * CosmeticTransparencyScore/CosingRegulatoryDb already compute, as plain
 * summary columns (not re-parsed JSON), same "store the computed result,
 * not just raw inputs to re-derive it every read" choice ScanHistoryEntity
 * itself makes for score/grade.
 */
@Entity(
    tableName = "nonfood_scans",
    indices = [Index(value = ["profileId", "scannedAt"])],
)
data class NonFoodScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val brand: String,
    val category: String,       // NonConsumableCategory.name
    val ingredientCount: Int?,  // null = no ingredient data was available
    val complexity: String?,    // FormulaComplexity.name, null alongside ingredientCount
    val allergenCount: Int,
    val prohibitedCount: Int,   // Annex II matches - see CosingRegulatoryDb
    val restrictedCount: Int,   // Annex III matches
    val scannedAt: Long,        // epoch millis
    val profileId: String = "default",
    val favorite: Boolean = false,
    // Added 13/08/2026: the per-category functional scores built this
    // session (ShampooQualityScore, ShowerGelQualityScore, etc.) all
    // recompute from raw ingredientsText, unlike the summary columns above
    // which were pre-computed at scan time - this table didn't store that
    // raw text at all, so a shampoo's functional profile (cleansing base,
    // silicone content...) was visible in ScanStateOverlay's dialog at scan
    // time but permanently unavailable the moment the user opened History,
    // even though the exact same product/ingredients were sitting right
    // there in this row. Nullable, same "no data was available" meaning as
    // ingredientCount/complexity above, for rows scanned before this column
    // existed or where OPF genuinely had no ingredient text.
    val ingredientsText: String? = null,
)
