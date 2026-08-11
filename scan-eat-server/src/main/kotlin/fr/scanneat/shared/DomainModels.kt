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
    SOUP("soup"),
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
    ALCOHOLIC_BEVERAGE("alcoholic_beverage"),
    CONDIMENT("condiment"),
    // See Android Product.kt for the full rationale: honey/jam/marmalade
    // were routed into CONDIMENT only to reach its added-sugar veto
    // exemption, but CONDIMENT's sugarThresholds are tuned for savory
    // sauces, not honey/jam's ~55-80g/100g intrinsic sugar.
    SPREAD_SWEET("spread_sweet"),
    OIL_FAT("oil_fat"),
    // Plant-based meat/fish/cheese substitutes (tofu, textured pea protein,
    // vegan cheese) were previously misclassified as FRESH_MEAT/FISH/CHEESE
    // via OFF's own "en:meat-alternatives"/"en:fish-alternatives"/
    // "en:cheese-substitutes" tags containing "meat"/"fish"/"cheese" as a raw
    // substring - scored against real-animal-product protein/fat norms
    // instead of a plant-protein profile. Thresholds tuned for legumes/
    // textured-protein products (moderate protein, no expectation of the high
    // sat-fat real cheese/meat structurally carry).
    PLANT_BASED_ALTERNATIVE("plant_based_alternative"),
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
    val caffeineMg: Double? = null,
    val alcoholPercentVol: Double? = null,
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
    if (n.b1Mg != null) add("vitaminB1")
    if (n.b2Mg != null) add("vitaminB2")
    if (n.b3Mg != null) add("vitaminB3")
    if (n.b9Ug != null) add("vitaminB9")
    if (n.omega3G != null) add("omega3")
    if (n.caffeineMg != null) add("caffeine")
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
    // OFF's own curated traces_tags - manufacturer precautionary "may
    // contain traces of X" cross-contamination labeling, distinct from
    // allergens_tags above. Mirrors the identical field on the Android side
    // (see Scoring Drift Check).
    val declaredTracesTags: List<String> = emptyList(),
)

// Score output types
// A/B/C/D/F skipped E entirely, but Nutri-Score's native A-E badge (shown
// alongside this app's own grade) uses E as its worst tier. E is now a real,
// distinct 7th tier between D and F, not a relabeling. Mirrors the identical
// change on the Android side (see Scoring Drift Check).
enum class Grade(val label: String) {
    A_PLUS("A+"), A("A"), B("B"), C("C"), D("D"), E("E"), F("F");

    companion object {
        fun fromLabel(s: String): Grade = entries.firstOrNull { it.label == s } ?: F
    }
}

// Real-world evidentiary rubric, not per-pillar judgment calls - see the
// identical comment on the Android side (Product.kt, Scoring Drift Check) for
// the full rationale.
//   CRITICAL — established no-safe-level hazard: WHO/IARC Group 1 carcinogen
//     (ethanol, nitrite/nitrate curing agents), an industrial contaminant
//     with no established safe intake (trans fat), or a quantity of a
//     CRITICAL-tier risk factor (e.g. 2+ Tier-1 additives) that compounds a
//     single instance into a materially higher-confidence hazard.
//   MAJOR — strong, specific evidence of a single serious risk factor
//     clearly exceeding its category norm (e.g. one EU-banned/IARC-flagged
//     additive, sugar/salt/sat-fat deep in a category's "critical" band).
//   MODERATE — real but more mixed/preliminary evidence, or a risk factor
//     only moderately exceeding its category norm.
//   MINOR — weak, indirect, or heuristic evidence (a positional proxy, a
//     structural inference rather than a measured value).
//   INFO — no health-risk claim at all; narration, provenance, or a
//     positive/neutral observation.
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
