package fr.scanneat.domain.model

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
    // Previously beer/wine/spirits fell into BEVERAGE_SOFT or OTHER and were
    // scored against soda-shaped sugar/kcal reference ranges - see
    // NegativeNutrientsPillar.kt's alcohol penalty doc comment for the bug
    // this was found alongside (a beer scoring "A" with no alcohol awareness
    // at all). This category exists so CategoryThresholds.kt can give
    // alcoholic drinks their own kcal/sugar reference bands instead of
    // inheriting soft-drink norms.
    ALCOHOLIC_BEVERAGE("alcoholic_beverage"),
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
    val caffeineMg: Double? = null,
    /** OFF's alcohol_100g, already expressed as %vol (not grams) - null means
     *  not declared, not "alcohol-free". See NegativeNutrientsPillar.kt for
     *  why this exists: the base score previously had zero awareness that a
     *  product was alcoholic at all, so a beer with unremarkable sugar/fat/
     *  salt scored as a healthy "A" beverage. */
    val alcoholPercentVol: Double? = null,
    // True when one or more of the mineral/vitamin fields above were filled in by
    // MicronutrientEstimator.kt (category-based typical values) rather than
    // actually declared by the source (OFF/LLM) - most barcode products simply
    // never list iron/calcium/zinc/etc, which previously meant a user who scanned
    // and logged real food (e.g. beef) saw their iron intake silently stay at 0g
    // for the day. Surfaced in MicronutrientCard so an estimate is never presented
    // as a measured fact.
    val micronutrientsEstimated: Boolean = false,
    // Persisted verbatim (via ConsumptionRepository's nutritionAdapter, and
    // nested inside Product's own productJson) to DB columns. New fields must
    // have a default value, or bump schemaVersion and add a migration branch
    // in the parser.
    val schemaVersion: Int = 1,
) {
    companion object {
        val EMPTY = NutritionPer100g(
            energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0,
            carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0,
            proteinG = 0.0, saltG = 0.0,
        )
    }
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
    // Persisted verbatim (via ScanRepository's productAdapter) to scan_history's
    // productJson column. New fields must have a default value, or bump
    // schemaVersion and add a migration branch in the parser.
    val schemaVersion: Int = 1,
)

// Score output types
enum class Grade(val label: String) {
    A_PLUS("A+"), A("A"), B("B"), C("C"), D("D"), F("F");

    companion object {
        fun fromLabel(s: String): Grade = entries.firstOrNull { it.label == s } ?: F
    }
}

// Real-world evidentiary rubric, not per-pillar judgment calls - this is the
// generalization the alcohol-veto audit called for: alcohol wasn't a one-off
// bug, it was one instance of a pattern (severity assigned by which pillar
// happened to notice something, not by the actual weight of evidence) that
// recurred independently in additives (round 3: a single Tier-1 additive
// outranking dangerous salt), and would keep recurring in every future
// addition to this engine without a shared standard to check new deductions
// against. Every Deduction's Severity should be justifiable against this
// scale, not chosen to "feel right" for its own pillar in isolation:
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
    // Persisted verbatim (via ScanRepository's auditAdapter) to scan_history's
    // auditJson column. New fields must have a default value, or bump
    // schemaVersion and add a migration branch in the parser.
    val schemaVersion: Int = 1,
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
    val dbId: Long = 0,   // Row id from scan_history; 0 when not yet persisted
    val favorite: Boolean = false,
    val scannedAt: Long = 0,   // epoch millis from scan_history; 0 when not yet persisted
)

enum class ScanSource { OPEN_FOOD_FACTS, LLM, MERGED, MANUAL }
