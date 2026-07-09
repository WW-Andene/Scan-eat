package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 5: PILLAR 1 — PROCESSING LEVEL (max 20)
// ============================================================================

enum class NovaConfidence { HIGH, MEDIUM, LOW }
data class NovaInference(val nova: NovaClass, val confidence: NovaConfidence)

private fun detectUPFMarkers(ingredients: List<Ingredient>): List<String> =
    UPF_MARKER_PATTERNS.mapNotNull { (regex, label) ->
        if (ingredients.any { regex.containsMatchIn(it.name) }) label else null
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

    val hasPositiveEvidence = cosmetics.isNotEmpty() || upfMarkers.isNotEmpty()
    return NovaInference(NovaClass.ULTRA_PROCESSED, if (hasPositiveEvidence) NovaConfidence.MEDIUM else NovaConfidence.LOW)
}

fun scoreProcessing(product: Product): PillarScore {
    val MAX = 20
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()

    val inferredResult = inferNovaClassWithConfidence(product)
    val effectiveNova = when {
        product.novaClass == NovaClass.ULTRA_PROCESSED && inferredResult.nova.value < 4 -> inferredResult.nova
        else -> product.novaClass
    }

    if (effectiveNova != product.novaClass) {
        deductions += Deduction("processing", "NOVA auto-adjusted ${product.novaClass.value}→${effectiveNova.value} based on ingredients", 0.0, Severity.INFO)
    }

    val novaWasInferred = effectiveNova != product.novaClass
    if (novaWasInferred && inferredResult.confidence != NovaConfidence.HIGH) {
        val note = if (inferredResult.confidence == NovaConfidence.LOW)
            "NOVA heuristic confidence: LOW — ingredient list missing or too short"
        else
            "NOVA heuristic confidence: MEDIUM — inferred from absence of known additives"
        deductions += Deduction("processing", note, 0.0, Severity.INFO)
    }

    val base = when (effectiveNova) {
        NovaClass.UNPROCESSED   -> 20.0
        NovaClass.CULINARY      -> 17.0
        NovaClass.PROCESSED     -> 13.0
        NovaClass.ULTRA_PROCESSED -> 6.0
    }

    deductions += Deduction("processing", "NOVA class ${effectiveNova.value} base score", base - MAX, when (effectiveNova) {
        NovaClass.ULTRA_PROCESSED -> Severity.MAJOR
        NovaClass.PROCESSED -> Severity.MODERATE
        else -> Severity.INFO
    })

    var score = base

    if (product.ingredients.size > 10) {
        score -= 2
        deductions += Deduction("processing", "${product.ingredients.size} ingredients (>10 threshold)", -2.0, Severity.MINOR)
    }

    val cosmeticAdditives = product.ingredients
        .mapNotNull { findAdditive(it.eNumber, it.name, it.category) }
        .filter { it.category in COSMETIC_ADDITIVE_CATEGORIES }

    val upfMarkers = detectUPFMarkers(product.ingredients)
    if (upfMarkers.isNotEmpty()) {
        val penalty = minOf(4.0, upfMarkers.size * 2.0)
        score -= penalty
        deductions += Deduction("processing", "${upfMarkers.size} UPF marker(s): ${upfMarkers.joinToString()}", -penalty, Severity.MINOR)
    }

    if (cosmeticAdditives.isNotEmpty()) {
        score -= 2
        deductions += Deduction("processing", "Contains cosmetic additives", -2.0, Severity.MINOR,
            cosmeticAdditives.joinToString { "${it.eNumber} (${it.category.key})" })
    }

    val first = product.ingredients.firstOrNull()
    if (first != null) {
        val match = FIRST_INGREDIENT_PENALTY_PATTERNS.find { (re, _) -> re.containsMatchIn(first.name.trim()) }
        if (match != null) {
            score -= 3
            deductions += Deduction("processing", "Primary ingredient is ${match.second}: \"${first.name}\"", -3.0, Severity.MODERATE)
        }
    }

    return PillarScore("Processing Level", MAX, maxOf(0.0, score), deductions, bonuses)
}
