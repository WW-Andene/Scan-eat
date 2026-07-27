package fr.scanneat.domain.engine.scoring

// ============================================================================
// ADDITIVES DATABASE — models
// Split out of AdditivesDb.kt: enums/data class/derived-set shared by every
// ADDITIVES_DB tier file and by findAdditive()'s lookup logic.
// ============================================================================

enum class AdditiveTier(val value: Int) { ONE(1), TWO(2), THREE(3) }

enum class AdditiveCategory(val key: String) {
    PRESERVATIVE("preservative"),
    EMULSIFIER("emulsifier"),
    ACIDULANT("acidulant"),
    STABILIZER("stabilizer"),
    COLORANT("colorant"),
    ANTIOXIDANT("antioxidant"),
    SWEETENER("sweetener"),
    FLAVOR_ENHANCER("flavor_enhancer"),
    THICKENER("thickener"),
    HUMECTANT("humectant"),
    GLAZING("glazing"),
    GLAZING_AGENT("glazing_agent"),
    ACIDITY_REGULATOR("acidity_regulator"),
    ANTICAKING("anticaking"),
    SOLVENT("solvent"),
    FLOUR_TREATMENT("flour_treatment"),
    SEQUESTRANT("sequestrant"),
    RAISING_AGENT("raising_agent"),
    PACKAGING_GAS("packaging_gas"),
}

data class AdditiveInfo(
    val eNumber: String,
    val names: List<String>,
    val tier: AdditiveTier,
    val category: AdditiveCategory,
    val concern: String,
    val source: String,
)

/** Additive categories that trigger cosmetic-processing penalty in Pillar 1. */
val COSMETIC_ADDITIVE_CATEGORIES = setOf(
    AdditiveCategory.COLORANT,
    AdditiveCategory.FLAVOR_ENHANCER,
    AdditiveCategory.SWEETENER,
)
