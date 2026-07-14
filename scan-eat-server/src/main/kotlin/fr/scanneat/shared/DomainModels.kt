package fr.scanneat.shared

// ============================================================================
// DOMAIN MODELS — port of scoring-engine.ts §SECTION 1: TYPES
// All nullability mirrors the original TypeScript optional fields.
// ============================================================================

/** NOVA processing class 1–4 (Monteiro et al., Public Health Nutrition 22:936–941, 2019). */
enum class NovaClass(val value: Int) {
    UNPROCESSED(1),
    CULINARY(2),
    PROCESSED(3),
    ULTRA_PROCESSED(4);

    companion object {
        fun fromInt(v: Int): NovaClass = entries.firstOrNull { it.value == v } ?: ULTRA_PROCESSED
    }
}

enum class ProductCategory(val key: String) {
    SANDWICH("sandwich"),
    READY_MEAL("ready_meal"),
    BREAD("bread"),
    BREAKFAST_CEREAL("breakfast_cereal"),
    YOGURT("yogurt"),
    CHEESE("cheese"),
    PROCESSED_MEAT("processed_meat"),
    FRESH_MEAT("fresh_meat"),
    FISH("fish"),
    SNACK_SWEET("snack_sweet"),
    SNACK_SALTY("snack_salty"),
    BEVERAGE_SOFT("beverage_soft"),
    BEVERAGE_JUICE("beverage_juice"),
    BEVERAGE_WATER("beverage_water"),
    CONDIMENT("condiment"),
    OIL_FAT("oil_fat"),
    OTHER("other");

    companion object {
        fun fromKey(k: String): ProductCategory =
            entries.firstOrNull { it.key == k } ?: OTHER
    }
}

/**
 * Per-100 g nutrition values.
 * Null means "not declared" — distinct from 0 (declared as zero).
 */
data class NutritionPer100g(
    val energyKcal: Double,
    val fatG: Double,
    val saturatedFatG: Double,
    val carbsG: Double,
    val sugarsG: Double,
    val addedSugarsG: Double? = null,
    val fiberG: Double,
    val proteinG: Double,
    val saltG: Double,
    val transFatG: Double? = null,
    // Minerals
    val ironMg: Double? = null,
    val calciumMg: Double? = null,
    val magnesiumMg: Double? = null,
    val potassiumMg: Double? = null,
    val zincMg: Double? = null,
    val sodiumMg: Double? = null,
    // Vitamins
    val vitAUg: Double? = null,
    val vitCMg: Double? = null,
    val vitDUg: Double? = null,
    val vitEMg: Double? = null,
    val vitKUg: Double? = null,
    val b1Mg: Double? = null,
    val b2Mg: Double? = null,
    val b3Mg: Double? = null,
    val b6Mg: Double? = null,
    val b9Ug: Double? = null,
    val b12Ug: Double? = null,
    // Macro subdivisions
    val polyunsaturatedFatG: Double? = null,
    val monounsaturatedFatG: Double? = null,
    val omega3G: Double? = null,
    val omega6G: Double? = null,
    val cholesterolMg: Double? = null,
) {
    companion object {
        val EMPTY = NutritionPer100g(
            energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0,
            carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0,
            proteinG = 0.0, saltG = 0.0,
        )
    }
}

/**
 * Which of [n]'s micronutrient fields OFF/LLM actually declared a value for -
 * a non-null field means the source's own nutriments table explicitly carried
 * that micronutrient for this product, i.e. it really is declared on the
 * label. Mirrors the Android app's identical helper (OffMapper.kt) - used by
 * both ServerOffMapper.mapOffProduct and LlmLabelParser.mapToProduct, which
 * previously left Product.declaredMicronutrients permanently empty.
 */
fun declaredMicronutrientsOf(n: NutritionPer100g): List<String> = buildList {
    if (n.ironMg != null) add("iron")
    if (n.calciumMg != null) add("calcium")
    if (n.magnesiumMg != null) add("magnesium")
    if (n.potassiumMg != null) add("potassium")
    if (n.zincMg != null) add("zinc")
    if (n.vitAUg != null) add("vitaminA")
    if (n.vitCMg != null) add("vitaminC")
    if (n.vitDUg != null) add("vitaminD")
    if (n.vitEMg != null) add("vitaminE")
    if (n.vitKUg != null) add("vitaminK")
    if (n.b12Ug != null) add("vitaminB12")
    if (n.b6Mg != null) add("vitaminB6")
}

data class Ingredient(
    val name: String,
    val percentage: Double? = null,
    val isWholeFood: Boolean? = null,
    val eNumber: String? = null,
    val category: IngredientCategory? = null,
)

enum class IngredientCategory { FOOD, ADDITIVE, PROCESSING_AID }

data class Product(
    val name: String,
    val category: ProductCategory,
    val novaClass: NovaClass,
    val ingredients: List<Ingredient>,
    val nutrition: NutritionPer100g,
    // Optional metadata
    val weightG: Double? = null,
    val origin: String? = null,
    val organic: Boolean = false,
    val wholeGrainPrimary: Boolean = false,
    val fermented: Boolean = false,
    val hasHealthClaims: Boolean = false,
    val hasMisleadingMarketing: Boolean = false,
    val namedOils: Boolean? = null,
    val originTransparent: Boolean = false,
    val declaredMicronutrients: List<String> = emptyList(),
    val ecoscoreGrade: String? = null,
    val ecoscoreValue: Double? = null,
    val nutriscoreGrade: String? = null,
    // OFF's own curated allergen tags (e.g. "en:gluten", "en:milk") - verified
    // against the manufacturer's declaration, more reliable than regexing the
    // free-text ingredient list. Empty when OFF has none declared or the
    // product came from the LLM/photo fallback path instead.
    val declaredAllergenTags: List<String> = emptyList(),
)

// Score output types
enum class Grade(val label: String) {
    A_PLUS("A+"), A("A"), B("B"), C("C"), D("D"), F("F");

    companion object {
        fun fromLabel(s: String): Grade = entries.firstOrNull { it.label == s } ?: F
    }
}

enum class Severity { INFO, MINOR, MODERATE, MAJOR, CRITICAL }

data class Deduction(
    val pillar: String,
    val reason: String,
    val points: Double,        // negative = deduction, positive = bonus
    val severity: Severity,
    val evidence: String? = null,
)

data class PillarScore(
    val name: String,
    val max: Int,
    val score: Double,
    val deductions: List<Deduction>,
    val bonuses: List<Deduction>,
)

data class VetoCondition(
    val triggered: Boolean,
    val reason: String,
    val cap: Int,
)

data class ScoreAudit(
    val productName: String,
    val category: ProductCategory,
    val score: Int,
    val grade: Grade,
    val verdict: String,
    val pillars: Pillars,
    val globalBonuses: List<Deduction>,
    val globalPenalties: List<Deduction>,
    val veto: VetoCondition,
    val redFlags: List<String>,
    val greenFlags: List<String>,
    val eco: EcoInfo? = null,
    val nutriscoreGrade: String? = null,
    val engineVersion: String,
    val warnings: List<String>,
) {
    data class Pillars(
        val processing: PillarScore,
        val nutritionalDensity: PillarScore,
        val negativeNutrients: PillarScore,
        val additiveRisk: PillarScore,
        val ingredientIntegrity: PillarScore,
    )

    data class EcoInfo(val grade: String?, val value: Double?)
}

/** Result returned by the full scan pipeline. */
data class ScanResult(
    val product: Product,
    val audit: ScoreAudit,
    val warnings: List<String>,
    val source: ScanSource,
    val barcode: String? = null,
)

enum class ScanSource { OPEN_FOOD_FACTS, LLM, MERGED }
