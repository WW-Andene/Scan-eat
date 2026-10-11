package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 5: PILLAR 1 — PROCESSING LEVEL (max 20)
// ============================================================================

enum class NovaConfidence { HIGH, MEDIUM, LOW }
data class NovaInference(val nova: NovaClass, val confidence: NovaConfidence)

// Match against a lowercased copy, not ingredient.name itself -
// RegexOption.IGNORE_CASE maps to Pattern.CASE_INSENSITIVE alone (no
// UNICODE_CASE), which only case-folds ASCII a-z/A-Z, not accented letters.
// UPF_MARKER_PATTERNS' own accented character classes ([oô], [eé]) never
// matched an all-caps ingredient like OFF/OCR commonly produces (e.g.
// "ARÔMES NATURELS", "PROTÉINE ISOLÉE DE SOJA"), silently losing the UPF-
// marker penalty and pulling inferNovaClassWithConfidence toward a falsely
// favorable NOVA class. Same bug class already fixed in AllergenDetector.kt
// and DietChecker.kt, just not swept here.
private fun detectUPFMarkers(ingredients: List<Ingredient>): List<String> =
    UPF_MARKER_PATTERNS.mapNotNull { (regex, label) ->
        if (ingredients.any { regex.containsMatchIn(it.name.lowercase()) }) label else null
    }

fun inferNovaClassWithConfidence(product: Product): NovaInference {
    val ings = product.ingredients
    if (ings.isEmpty()) {
        return if (FRESH_PRODUCE_NAME.containsMatchIn(product.name.trim()))
            NovaInference(NovaClass.UNPROCESSED, NovaConfidence.HIGH)
        else
            NovaInference(NovaClass.ULTRA_PROCESSED, NovaConfidence.LOW)
    }

    val additives = ings.filter {
        it.category == IngredientCategory.ADDITIVE || it.eNumber != null
    }
    val cosmetics = additives.mapNotNull { ing ->
        findAdditive(ing.eNumber, ing.name, ing.category)
    }.filter { it.category in COSMETIC_ADDITIVE_CATEGORIES }
    val upfMarkers = detectUPFMarkers(ings)

    if (ings.size == 1 && additives.isEmpty() && upfMarkers.isEmpty())
        return NovaInference(NovaClass.UNPROCESSED, NovaConfidence.HIGH)

    if (ings.size <= 3 && additives.isEmpty() && upfMarkers.isEmpty()) {
        val onlyCulinary = ings.all { Regex("""^(sucre|sel|huile|beurre|graisse|miel|vinaigre|eau)""", RegexOption.IGNORE_CASE).containsMatchIn(it.name.trim()) }
        if (onlyCulinary) return NovaInference(NovaClass.CULINARY, NovaConfidence.HIGH)
    }

    if (cosmetics.isEmpty() && upfMarkers.isEmpty() && additives.size <= 2 && ings.size <= 10)
        return NovaInference(NovaClass.PROCESSED, NovaConfidence.MEDIUM)

    // Previously fell straight to ULTRA_PROCESSED/LOW past 10 ingredients even
    // with zero additive/cosmetic/UPF evidence - a trail mix, mixed-vegetable
    // soup, or muesli with 11+ whole-food ingredients and nothing else
    // suspicious got NOVA-4-penalized purely for ingredient count, with no
    // actual evidence of ultra-processing. A long whole-food list is weak
    // evidence either way, not proof of ultra-processing, so keep it at
    // PROCESSED (not UNPROCESSED/CULINARY - it's still a composite product)
    // with LOW confidence rather than jumping two full NOVA classes.
    //
    // additives.size <= 2, not additives.isEmpty() - the zero-additive fix
    // above was never generalized to the >10-ingredient product with 1-2
    // harmless non-cosmetic, non-UPF-marker additives (e.g. citric acid E330
    // as an antioxidant in a 12-ingredient muesli), which fell through both
    // this and the line-42 <=10-ingredient branch straight to ULTRA_PROCESSED/
    // LOW - the exact two-class jump on weak evidence this section exists to
    // prevent, just for a slightly wider input shape.
    if (cosmetics.isEmpty() && upfMarkers.isEmpty() && additives.size <= 2)
        return NovaInference(NovaClass.PROCESSED, NovaConfidence.LOW)

    val hasPositiveEvidence = cosmetics.isNotEmpty() || upfMarkers.isNotEmpty()
    return NovaInference(NovaClass.ULTRA_PROCESSED, if (hasPositiveEvidence) NovaConfidence.MEDIUM else NovaConfidence.LOW)
}

fun scoreProcessing(product: Product, lang: String = "en"): PillarScore {
    val en = lang == "en"
    val MAX = 20
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()

    val inferredResult = inferNovaClassWithConfidence(product)
    val effectiveNova = when {
        product.novaClass == NovaClass.ULTRA_PROCESSED && inferredResult.nova.value < 4 -> inferredResult.nova
        else -> product.novaClass
    }

    if (effectiveNova != product.novaClass) {
        deductions += Deduction("processing", if (en) "NOVA auto-adjusted ${product.novaClass.value}→${effectiveNova.value} based on ingredients" else "NOVA ajusté automatiquement ${product.novaClass.value}→${effectiveNova.value} d'après les ingrédients", 0.0, Severity.INFO)
    }

    val novaWasInferred = effectiveNova != product.novaClass
    if (novaWasInferred && inferredResult.confidence != NovaConfidence.HIGH) {
        val note = if (inferredResult.confidence == NovaConfidence.LOW)
            (if (en) "NOVA heuristic confidence: LOW — ingredient list missing or too short" else "Confiance heuristique NOVA : FAIBLE — liste d'ingrédients manquante ou trop courte")
        else
            (if (en) "NOVA heuristic confidence: MEDIUM — inferred from absence of known additives" else "Confiance heuristique NOVA : MOYENNE — déduite de l'absence d'additifs connus")
        deductions += Deduction("processing", note, 0.0, Severity.INFO)
    }

    val base = when (effectiveNova) {
        NovaClass.UNPROCESSED   -> 20.0
        NovaClass.CULINARY      -> 17.0
        NovaClass.PROCESSED     -> 13.0
        NovaClass.ULTRA_PROCESSED -> 6.0
    }

    deductions += Deduction("processing", if (en) "NOVA class ${effectiveNova.value} base score" else "Score de base classe NOVA ${effectiveNova.value}", base - MAX, when (effectiveNova) {
        NovaClass.ULTRA_PROCESSED -> Severity.MAJOR
        NovaClass.PROCESSED -> Severity.MODERATE
        else -> Severity.INFO
    })

    var score = base

    if (product.ingredients.size > 10) {
        score -= 2
        deductions += Deduction("processing", if (en) "${product.ingredients.size} ingredients (>10 threshold)" else "${product.ingredients.size} ingrédients (seuil >10)", -2.0, Severity.MINOR)
    }

    val cosmeticAdditives = product.ingredients
        .mapNotNull { findAdditive(it.eNumber, it.name, it.category) }
        .filter { it.category in COSMETIC_ADDITIVE_CATEGORIES }

    val upfMarkers = detectUPFMarkers(product.ingredients)
    if (upfMarkers.isNotEmpty()) {
        val penalty = minOf(4.0, upfMarkers.size * 2.0)
        score -= penalty
        deductions += Deduction("processing", (if (en) "${upfMarkers.size} UPF marker(s): " else "${upfMarkers.size} marqueur(s) d'ultra-transformation : ") + upfMarkers.joinToString(), -penalty, Severity.MINOR)
    }

    if (cosmeticAdditives.isNotEmpty()) {
        score -= 2
        deductions += Deduction("processing", if (en) "Contains cosmetic additives" else "Contient des additifs cosmétiques", -2.0, Severity.MINOR,
            cosmeticAdditives.joinToString { "${it.eNumber} (${it.category.key})" })
    }

    val first = product.ingredients.firstOrNull()
    if (first != null) {
        val match = FIRST_INGREDIENT_PENALTY_PATTERNS.find { (re, _) -> re.containsMatchIn(first.name.trim()) }
        if (match != null) {
            score -= 3
            deductions += Deduction("processing", (if (en) "Primary ingredient is ${match.second}: " else "L'ingrédient principal est ${match.second} : ") + "\"${first.name}\"", -3.0, Severity.MODERATE)
        }
    }

    // Acrylamide — IARC Group 2A (probable human carcinogen), formed by the
    // Maillard reaction in fried/baked starchy foods (chips, crisps, frites,
    // crackers). Deliberately narrow and low-weight: there is no reliable way
    // to detect it from a declared ingredient list alone (it's a byproduct of
    // cooking method + temperature, not an ingredient), so this only flags
    // the one case where both the category (starchy, commonly fried) and an
    // explicit frying/cooking-method keyword are present — a heuristic
    // caution, not a certainty, unlike the additive/nutrient checks above.
    val friedStarchy = product.category == ProductCategory.SNACK_SALTY &&
        Regex("""\bfrit(?:e|es|s)?\b|\bfried\b|friture|deep[-\s]?fried""", RegexOption.IGNORE_CASE)
            .let { re -> re.containsMatchIn(product.name) || product.ingredients.any { re.containsMatchIn(it.name) } }
    if (friedStarchy) {
        score -= 1
        deductions += Deduction("processing", if (en) "Fried starchy food — possible acrylamide formation (IARC Group 2A)" else "Aliment amylacé frit — formation possible d'acrylamide (IARC groupe 2A)", -1.0, Severity.MINOR)
    }

    // bonuses was declared but never populated - every other pillar can earn
    // a visible green flag for its best outcome (buildFlags surfaces any
    // bonus worth >=2 points), but a genuinely NOVA-1 unprocessed, additive-
    // free product with a clean first ingredient - the best possible outcome
    // in this 20-point pillar - produced zero user-facing positive reasons,
    // while a product merely clearing NutritionalDensity's 5-point fiber
    // bonus got one. `points` here is deliberately NOT added to `score` (base
    // already sits at MAX for this outcome, and PillarScore.score is passed
    // explicitly rather than derived by summing deductions/bonuses) - it only
    // exists so buildFlags' >=2-point bar surfaces this as a green flag the
    // same way the other pillars' clean outcomes already are.
    if (score >= base && (effectiveNova == NovaClass.UNPROCESSED || effectiveNova == NovaClass.CULINARY)) {
        bonuses += Deduction("processing", if (en) "Minimally processed, no processing markers" else "Peu transformé, aucun marqueur de transformation", 2.0, Severity.INFO)
    }

    return PillarScore(if (en) "Processing Level" else "Niveau de transformation", MAX, maxOf(0.0, minOf(MAX.toDouble(), score)), deductions, bonuses)
}
