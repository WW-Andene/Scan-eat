package fr.scanneat.domain.engine.nonconsumable

// ============================================================================
// NON-CONSUMABLE PRODUCT LOOKUP DB — a small, hand-verified starter set
// sourced from Open Products Facts (world.openproductsfacts.org, same
// non-profit org as Open Food Facts; community-maintained barcode database
// covering household/chemical/non-food products). Verified against the live
// OPF API on 2026-07-13.
//
// Deliberately small (identification only: name/brand/category) rather than
// importing OPF's full ~40k-product catalogue in one go, and deliberately
// carries NO per-product toxicology/treatment data - OPF doesn't provide
// verified poison-control-grade safety information, and fabricating "if
// ingested, do X" guidance for a specific chemical would be a real harm risk.
// Exposure guidance in the UI (see ScanViewModel/ScanScreen) instead always
// defers to real emergency services, the only medically-honest answer here.
// ============================================================================

enum class NonConsumableCategory { BLEACH, CLEANING_PRODUCT, LAUNDRY, OTHER }

data class NonConsumableDbEntry(
    val barcode: String,
    val name: String,
    val brand: String,
    val category: NonConsumableCategory,
)

private val ENTRIES: List<NonConsumableDbEntry> = listOf(
    NonConsumableDbEntry("3450970004930", "Eau de javel", "Eco +", NonConsumableCategory.BLEACH),
    NonConsumableDbEntry("3596710556526", "Pastilles javel eucalyptus x 48", "Auchan", NonConsumableCategory.BLEACH),
    NonConsumableDbEntry("3596710556533", "Pastilles de Javel", "Auchan", NonConsumableCategory.BLEACH),
    NonConsumableDbEntry("0065333523626", "Oxy Clean Détachant", "Oxi clean", NonConsumableCategory.LAUNDRY),
    NonConsumableDbEntry("4015200037325", "Spee Color Caps", "Henkel", NonConsumableCategory.LAUNDRY),
    NonConsumableDbEntry("3450970178068", "Lessive poudre tous textiles", "Eco+", NonConsumableCategory.LAUNDRY),
    NonConsumableDbEntry("3350033834538", "Lessive main poudre lavage express sans frotter", "Monoprix", NonConsumableCategory.LAUNDRY),
    NonConsumableDbEntry("8720181248221", "Lessive Capsules Tout-en-1 Fraîcheur Intense", "Skip", NonConsumableCategory.LAUNDRY),
)

private val BY_BARCODE: Map<String, NonConsumableDbEntry> = ENTRIES.associateBy { it.barcode }

fun findNonConsumableByBarcode(barcode: String): NonConsumableDbEntry? = BY_BARCODE[barcode.filter { it.isDigit() }]
