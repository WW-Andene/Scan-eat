package fr.scanneat.data.repository.scan

import fr.scanneat.domain.engine.nutrition.declaredMicronutrientsOf
import fr.scanneat.domain.engine.scoring.ANNEX_II_KEY_TO_OFF_TAG
import fr.scanneat.domain.model.*

// ============================================================================
// Mapping LLM DTO → domain Product, JSON extraction, and warnings/messages.
//
// Split out of OcrParser.kt (pure structural move, no behavior change).
// ============================================================================

// Named thresholds shared by every call site in this file, so the per-100g
// energy cap and the barcode-plausibility check are each defined once instead
// of repeated as bare literals. Keep these numerically in sync with the
// equivalent constants in the server's LlmLabelParser.kt (NutritionLimits).
internal object NutritionLimits {
    const val MAX_ENERGY_KCAL_PER_100G = 900.0
    val BARCODE_DIGITS_REGEX = Regex("\\d{8,14}")
}

// Floors at 0 but had no ceiling - a hallucinated/misread per-100g value
// (e.g. energy_kcal: 5000, a plausible OCR misread of "500") was accepted as
// ground truth with no upper bound at all, silently corrupting downstream
// diary/scoring math. 900 kcal/100g covers pure fat/oil, the densest real
// food; other macros/salt can't physically exceed 100g per 100g of food.
internal fun coerceDouble(v: Any?, max: Double = 100.0): Double {
    val raw = when (v) {
        is Number -> v.toDouble()
        is String -> v.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    return raw.coerceIn(0.0, max)
}

// coerceDouble above only ever covered the macros (energy/fat/carbs/protein/
// salt) - every micronutrient/trans-fat/caffeine field below was passed
// straight through as a nullable Double with no ceiling at all, unlike its
// macro siblings. A hallucinated/misread per-100g figure here (e.g.
// "iron_mg: 999999999" from a misread decimal, or a mg/µg unit confusion)
// would silently corrupt DiaryEntry's running micronutrient totals and any
// score/warning derived from them - null still means "not detected" and is
// preserved, only a present value is clamped. Ceilings are generous per-100g
// real-world maxima (e.g. liver for iron/vitA/B12, acerola for vitC, natto
// for vitK, cod liver oil for vitD) so no legitimate extreme food is clipped.
private fun coerceNullableDouble(v: Double?, max: Double): Double? = v?.coerceIn(0.0, max)

internal fun mapLlmToProduct(dto: LlmProductDto): Product {
    val n = dto.nutrition
    val nutrition = NutritionPer100g(
        energyKcal    = coerceDouble(n?.energy_kcal, max = NutritionLimits.MAX_ENERGY_KCAL_PER_100G),
        fatG          = coerceDouble(n?.fat_g),
        saturatedFatG = coerceDouble(n?.saturated_fat_g),
        carbsG        = coerceDouble(n?.carbs_g),
        sugarsG       = coerceDouble(n?.sugars_g),
        addedSugarsG  = coerceNullableDouble(n?.added_sugars_g, max = 100.0),
        fiberG        = coerceDouble(n?.fiber_g),
        proteinG      = coerceDouble(n?.protein_g),
        saltG         = coerceDouble(n?.salt_g),
        transFatG     = coerceNullableDouble(n?.trans_fat_g, max = 100.0),
        ironMg        = coerceNullableDouble(n?.iron_mg, max = 100.0),
        calciumMg     = coerceNullableDouble(n?.calcium_mg, max = 3000.0),
        magnesiumMg   = coerceNullableDouble(n?.magnesium_mg, max = 1000.0),
        potassiumMg   = coerceNullableDouble(n?.potassium_mg, max = 5000.0),
        zincMg        = coerceNullableDouble(n?.zinc_mg, max = 50.0),
        vitAUg        = coerceNullableDouble(n?.vit_a_ug, max = 30000.0),
        vitCMg        = coerceNullableDouble(n?.vit_c_mg, max = 2000.0),
        vitDUg        = coerceNullableDouble(n?.vit_d_ug, max = 250.0),
        vitEMg        = coerceNullableDouble(n?.vit_e_mg, max = 150.0),
        vitKUg        = coerceNullableDouble(n?.vit_k_ug, max = 1000.0),
        b12Ug         = coerceNullableDouble(n?.b12_ug, max = 100.0),
        // Photo/OCR-identified products previously never got a caffeine value
        // at all (unlike barcode-scanned ones, which read it straight from
        // OFF's own nutriments map) - the hypertension caffeine check could
        // only ever fire for a product found via barcode, silently missing
        // a caffeinated drink identified purely from its label photo.
        caffeineMg    = coerceNullableDouble(n?.caffeine_mg, max = 1000.0),
    )
    return Product(
        name      = dto.name?.trim() ?: "(produit sans nom)",
        category  = ProductCategory.fromKey(dto.category ?: "other"),
        novaClass = NovaClass.fromInt(dto.nova_class ?: 4),
        ingredients = dto.ingredients?.mapNotNull { ing ->
            val name = ing.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Ingredient(
                name        = name,
                percentage  = ing.percentage,
                eNumber     = ing.e_number?.uppercase()?.takeIf { Regex("^E\\d{3}").containsMatchIn(it) },
                category    = when (ing.category?.lowercase()) {
                    "additive"      -> IngredientCategory.ADDITIVE
                    "processing_aid" -> IngredientCategory.PROCESSING_AID
                    else            -> IngredientCategory.FOOD
                },
                isWholeFood = ing.is_whole_food,
            )
        } ?: emptyList(),
        nutrition = nutrition,
        organic               = dto.organic ?: false,
        wholeGrainPrimary     = dto.whole_grain_primary ?: false,
        fermented             = dto.fermented ?: false,
        hasHealthClaims       = dto.has_health_claims ?: false,
        hasMisleadingMarketing = dto.has_misleading_marketing ?: false,
        namedOils             = dto.named_oils,
        origin                = dto.origin?.takeIf { it.isNotBlank() },
        weightG               = dto.weight_g,
        // See OffMapper.mapOffProduct's identical fix - previously always empty
        // for every real scan (barcode or photo), making the SEX/iron personal-
        // score bonus and ProductHints' "Declared micronutrients" line dead code.
        declaredMicronutrients = declaredMicronutrientsOf(nutrition),
        // declaredAllergenTags was always empty for every LLM/photo-sourced scan
        // (unlike OFF-sourced ones) - the label prompt never asked for the
        // printed allergen statement at all, so AllergenDetector.detectAllergens()'s
        // "declared by OFF" augmentation had no LLM-path equivalent even though
        // French labels commonly print a boxed "Peut contenir des traces de..."
        // warning the ingredients-regex pass alone can't catch. Mapped through the
        // same "en:xxx" vocabulary OFF itself uses so detectAllergens needs no
        // separate code path per source.
        declaredAllergenTags = dto.allergen_declarations.orEmpty().mapNotNull { ANNEX_II_KEY_TO_OFF_TAG[it] },
    )
}

// ============================================================================
// JSON extraction helper (strips markdown fences if the model ignores rules)
// ============================================================================

internal fun extractJson(raw: String): String {
    val stripped = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = stripped.indexOf('{')
    val end   = stripped.lastIndexOf('}')
    return if (start >= 0 && end > start) stripped.substring(start, end + 1) else stripped
}

internal fun buildWarnings(product: Product, dto: LlmProductDto, lang: String): List<String> {
    val w = mutableListOf<String>()
    if (product.nutrition.energyKcal == 0.0 && product.nutrition.proteinG == 0.0)
        w += if (lang == "en") "Nutrition values could not be read from image"
             else "Les valeurs nutritionnelles n'ont pas pu être lues sur l'image"
    if (product.ingredients.isEmpty())
        w += if (lang == "en") "Ingredients list could not be parsed"
             else "La liste des ingrédients n'a pas pu être lue"
    if (dto.barcode != null && !dto.barcode.matches(NutritionLimits.BARCODE_DIGITS_REGEX))
        w += if (lang == "en") "Barcode '${dto.barcode}' found on label may be incorrect"
             else "Le code-barres « ${dto.barcode} » lu sur l'étiquette est peut-être incorrect"
    // coerceDouble clamps physically-implausible values (a hallucinated or
    // misread per-100g figure) rather than silently trusting them - flag it
    // so the clamp is visible to the user instead of masquerading as a
    // clean read.
    if (dto.nutrition?.energy_kcal != null && dto.nutrition.energy_kcal > NutritionLimits.MAX_ENERGY_KCAL_PER_100G)
        w += if (lang == "en") "Energy value on label seems implausibly high and was capped"
             else "La valeur énergétique de l'étiquette semble anormalement élevée et a été plafonnée"
    return w
}

// These reach the user as ScanResult.warnings entries (rendered verbatim by
// WarningsSection) - previously hardcoded English regardless of the [lang]
// this OcrParser was already threading through parseLabel/identifyFood for
// every other user-facing string.
internal fun unparseableJsonMessage(lang: String) =
    if (lang == "en") "LLM returned unparseable JSON" else "Réponse de l'IA illisible (JSON invalide)"

internal fun unparseableIdentificationMessage(lang: String) =
    if (lang == "en") "LLM identification returned unparseable JSON" else "Identification par l'IA illisible (JSON invalide)"

internal fun aiEstimatedMessage(lang: String) =
    if (lang == "en") "Nutrition estimated by AI — not from label" else "Nutrition estimée par l'IA — pas issue d'une étiquette"
