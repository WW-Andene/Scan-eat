package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.scoring.OFF_ALLERGEN_TAG_MAP
import fr.scanneat.domain.engine.scoring.detectAllergens
import fr.scanneat.domain.engine.scoring.inferNovaClassWithConfidence
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.util.hasValidGs1CheckDigit
import java.text.Normalizer

// ============================================================================
// LABEL TEXT PARSER — on-device replacement for the former vision-LLM label
// reading. Input is the text lines ML Kit recognised on a packaging photo,
// with their bounding boxes; output is a draft Product the regular scoring
// engine then scores. Pure and deterministic, so it's unit-testable.
//
// What it reads:
//  - ingredient list: text after "Ingrédients"/"Ingredients" up to the next
//    section (nutrition table, storage, net weight...), split by the same
//    parseIngredients() barcode scans use;
//  - "peut contenir / may contain" → OFF-style traces tags;
//  - per-100 g nutrition: each nutrient label is matched to the first number
//    on its own line, or else the nearest number to its right on the same row
//    (nutrition tables are usually OCR'd as separate label/value columns);
//  - a GS1-valid EAN/UPC printed under the barcode;
//  - the product name: the tallest non-table line in the top part.
// ============================================================================

/** One recognised text line, coordinates in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

data class LabelParse(
    val product: Product,
    /** Nutrient fields (English keys) that were found on the label. */
    val nutrientsRead: Set<String>,
    val ingredientsRead: Boolean,
    val barcode: String?,
)

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private val NUMBER = Regex("""(?<![\d.,])[<≤]?\s*(\d{1,4}(?:[.,]\d{1,3})?)\s*(mg|g|kcal|kj)?(?![\d])""", RegexOption.IGNORE_CASE)

private data class NutrientRule(val key: String, val label: Regex)

// Order matters: the more specific label wins when a line could match several
// ("acides gras saturés" before "matières grasses", "sucres" before "glucides").
private val NUTRIENT_RULES = listOf(
    NutrientRule("saturatedFat", Regex("""satur""")),
    NutrientRule("sugars", Regex("""\bsucres?\b|\bsugars?\b""")),
    NutrientRule("fiber", Regex("""\bfibres?\b|\bfibers?\b""")),
    NutrientRule("protein", Regex("""\bproteines?\b|\bproteins?\b""")),
    NutrientRule("salt", Regex("""\bsel\b|\bsalt\b""")),
    NutrientRule("sodium", Regex("""\bsodium\b""")),
    NutrientRule("carbs", Regex("""\bglucides?\b|carbohydrate""")),
    NutrientRule("fat", Regex("""matieres? grasses|\blipides?\b|\bgraisses\b|\bfat\b|\btotal fat\b""")),
)

private val INGREDIENTS_HEADER = Regex("""ingr[eé]dients?\s*[:：]?""", RegexOption.IGNORE_CASE)
// Accent-tolerant: matched against the original lower-cased text so ingredient
// names keep their accents ("lait écrémé", not "lait ecreme").
private val INGREDIENTS_END = Regex(
    """valeurs? nutritionnelles|informations? nutritionnelles|d[ée]claration nutritionnelle|nutrition|""" +
        """[àa] conserver|conservation|[àa] consommer|poids net|net weight|fabriqu[ée]|distribu[ée]|""" +
        """ddm|dluo|pour 100|per 100|store in|best before|[ée]nergie|energy|valeur [ée]nerg[ée]tique""",
)
private val TRACES = Regex("""(?:peut contenir|traces? (?:[ée]ventuelles? )?de|may contain|traces of)\s*[:：]?\s*([^.]*)""")
private val TABLE_WORDS = Regex("""energie|energy|kcal|kj|pour 100|per 100|portion|valeurs|nutrition|ingredients?|%""")

fun parseLabelText(lines: List<OcrLine>, lang: String = "fr"): LabelParse {
    val ordered = lines.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.top }, { it.left }))
    val fullText = ordered.joinToString("\n") { it.text }
    val normText = norm(fullText)

    // ---- Ingredients ----
    var ingredientsText: String? = null
    var tracesText: String? = null
    val lowText = fullText.lowercase()
    INGREDIENTS_HEADER.find(lowText)?.let { m ->
        val after = lowText.substring(m.range.last + 1)
        val endIdx = INGREDIENTS_END.find(after)?.range?.first ?: after.length
        var block = after.substring(0, endIdx)
        TRACES.find(block)?.let { t -> tracesText = t.groupValues[1]; block = block.substring(0, t.range.first) }
        // Re-join words hyphenated across line breaks, then flatten lines.
        ingredientsText = block.replace(Regex("-\\s*\n\\s*"), "").replace('\n', ' ').trim().trimEnd('.', ' ')
    }
    if (tracesText == null) TRACES.find(lowText)?.let { tracesText = it.groupValues[1] }
    val ingredients = parseIngredients(ingredientsText)

    // ---- Nutrition ----
    val values = mutableMapOf<String, Double>()
    for (line in ordered) {
        val n = norm(line.text)
        val rule = NUTRIENT_RULES.firstOrNull { it.label.containsMatchIn(n) } ?: continue
        if (rule.key in values) continue
        val labelEnd = rule.label.find(n)!!.range.last + 1
        val inLine = firstNumber(n.substring(labelEnd))
        val value = inLine ?: sameRowNumber(line, ordered)
        if (value != null) values[rule.key] = value
    }
    energyKcal(normText)?.let { values["energy"] = it }
    if ("salt" !in values) values["sodium"]?.let { values["salt"] = it * 2.5 }

    val nutrition = NutritionPer100g(
        energyKcal = (values["energy"] ?: 0.0).coerceIn(0.0, 900.0),
        fatG = (values["fat"] ?: 0.0).coerceIn(0.0, 100.0),
        saturatedFatG = (values["saturatedFat"] ?: 0.0).coerceIn(0.0, 100.0),
        carbsG = (values["carbs"] ?: 0.0).coerceIn(0.0, 100.0),
        sugarsG = (values["sugars"] ?: 0.0).coerceIn(0.0, 100.0),
        fiberG = (values["fiber"] ?: 0.0).coerceIn(0.0, 100.0),
        proteinG = (values["protein"] ?: 0.0).coerceIn(0.0, 100.0),
        saltG = (values["salt"] ?: 0.0).coerceIn(0.0, 100.0),
    )

    // ---- Barcode / name ----
    val barcode = Regex("""(?<!\d)(\d{13}|\d{12}|\d{8})(?!\d)""").findAll(fullText.replace(" ", ""))
        .map { it.value }.firstOrNull { hasValidGs1CheckDigit(it) }
    val name = productName(ordered) ?: if (lang == "en") "Photographed product" else "Produit photographié"

    // ---- Traces → OFF-style tags (only allergens the detector recognises) ----
    val tracesTags = tracesText?.let { t ->
        val keys = detectAllergens(draft(name, listOf(Ingredient(name = t, category = IngredientCategory.FOOD)), nutrition)).map { it.key }.toSet()
        OFF_ALLERGEN_TAG_MAP.filterValues { it in keys }.keys.toList()
    } ?: emptyList()

    val base = draft(name, ingredients, nutrition).copy(declaredTracesTags = tracesTags)
    val product = base.copy(novaClass = if (ingredients.isEmpty()) NovaClass.PROCESSED else inferNovaClassWithConfidence(base).nova)
    return LabelParse(
        product = product,
        nutrientsRead = values.keys - "sodium",
        ingredientsRead = ingredients.isNotEmpty(),
        barcode = barcode,
    )
}

private fun draft(name: String, ingredients: List<Ingredient>, nutrition: NutritionPer100g) = Product(
    name = name,
    category = resolveCategory(null, name).takeIf { it != ProductCategory.OTHER } ?: ProductCategory.OTHER,
    novaClass = NovaClass.PROCESSED,
    ingredients = ingredients,
    nutrition = nutrition,
)

private fun firstNumber(s: String): Double? =
    NUMBER.findAll(s).firstOrNull { it.groupValues[2].lowercase() !in setOf("kcal", "kj") }
        ?.let { m -> m.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { v -> if (m.groupValues[2].lowercase() == "mg") v / 1000.0 else v } }

/** Nutrition tables are often OCR'd as a label column and value columns: take
 *  the leftmost number to the right of the label whose row overlaps it. */
private fun sameRowNumber(label: OcrLine, all: List<OcrLine>): Double? {
    val tolerance = (label.height * 0.6).toInt().coerceAtLeast(4)
    return all.asSequence()
        .filter { it !== label && it.left >= label.right - tolerance && kotlin.math.abs(it.centerY - label.centerY) <= tolerance }
        .sortedBy { it.left }
        .mapNotNull { firstNumber(norm(it.text)) }
        .firstOrNull()
}

private fun energyKcal(normText: String): Double? {
    Regex("""(\d{1,4}(?:[.,]\d)?)\s*kcal""").find(normText)?.let { return it.groupValues[1].replace(',', '.').toDoubleOrNull() }
    Regex("""(\d{1,4}(?:[.,]\d)?)\s*kj""").find(normText)?.let { return it.groupValues[1].replace(',', '.').toDoubleOrNull()?.div(4.184) }
    return null
}

private fun productName(ordered: List<OcrLine>): String? {
    if (ordered.isEmpty()) return null
    val maxBottom = ordered.maxOf { it.bottom }
    return ordered
        .filter { it.top <= maxBottom * 0.5 }
        .filter { l -> val n = norm(l.text); n.count { it.isLetter() } >= 3 && !TABLE_WORDS.containsMatchIn(n) && NUTRIENT_RULES.none { it.label.containsMatchIn(n) } }
        .maxByOrNull { it.height }
        ?.text?.trim()?.trimEnd(':', '.')
}
