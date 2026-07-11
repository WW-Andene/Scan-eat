package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ============================================================================
// FOOD SCORING ENGINE v2.2.0 — Kotlin port of scoring-engine.ts
//
// Authoritative vs Editorial boundary preserved from original.
// See scoring-engine.ts header for full provenance notes.
//
// Main entry: scoreProduct(product: Product): ScoreAudit
// Pure function — no I/O, no side effects.
//
// Orchestrator only — each pillar's scoring logic lives in its own file
// (ProcessingPillar.kt, NutritionalDensityPillar.kt, NegativeNutrientsPillar.kt,
// AdditiveRiskPillar.kt, IngredientIntegrityPillar.kt), category thresholds in
// CategoryThresholds.kt, shared keyword constants in ScoringKeywords.kt. Was
// previously a single 724-line file with all of the above inline.
// ============================================================================

const val ENGINE_VERSION = "2.3.0"

// ============================================================================
// SECTION 10: GLOBAL MODIFIERS, VETOES & ORCHESTRATOR
// ============================================================================

private fun scoreToGrade(score: Int): Grade = when {
    score >= 85 -> Grade.A_PLUS
    score >= 70 -> Grade.A
    score >= 55 -> Grade.B
    score >= 40 -> Grade.C
    score >= 25 -> Grade.D
    else        -> Grade.F
}

private fun gradeVerdict(grade: Grade): String = when (grade) {
    Grade.A_PLUS -> "Excellent — daily staple potential"
    Grade.A      -> "Good — regular consumption fine"
    Grade.B      -> "Acceptable — moderate frequency"
    Grade.C      -> "Mediocre — occasional only"
    Grade.D      -> "Poor — avoid regular use"
    Grade.F      -> "Very poor — avoid"
}

private fun computeGlobalBonuses(product: Product): List<Deduction> {
    val bonuses = mutableListOf<Deduction>()
    if (product.organic) bonuses += Deduction("global_bonus", "Organic certification", 2.0, Severity.INFO)
    if (product.wholeGrainPrimary) bonuses += Deduction("global_bonus", "Whole grain as primary grain", 3.0, Severity.INFO)
    if (product.fermented) bonuses += Deduction("global_bonus", "Contains fermented / probiotic content", 2.0, Severity.INFO)
    // Omega-3 bonus lives solely in NutritionalDensityPillar.scoreNutritionalDensity()
    // (+3, using the same ingredient regex plus a nutrition-value check) - it was
    // duplicated here too, double-counting the same signal for +5 total on any
    // product containing e.g. flaxseed or salmon.
    return bonuses
}

private fun computeGlobalPenalties(product: Product): List<Deduction> {
    val penalties = mutableListOf<Deduction>()
    if (product.hasMisleadingMarketing) penalties += Deduction("global_penalty", "Misleading marketing claims", -2.0, Severity.MODERATE)
    if (product.hasHealthClaims) penalties += Deduction("global_penalty", "Health claims present — verify vs composition", -3.0, Severity.MODERATE)
    val palm = product.ingredients.find { ing ->
        Regex("""huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name)
    }
    if (palm != null) penalties += Deduction("global_penalty", "Palm oil or derivative: ${palm.name}", -3.0, Severity.MODERATE)
    return penalties
}

private fun checkVeto(product: Product): VetoCondition {
    val n = product.nutrition

    if ((n.transFatG ?: 0.0) > 0.1)
        return VetoCondition(true, "Contains industrial trans fats — no safe level", 40)

    if (countTier1Additives(product) > 3)
        return VetoCondition(true, "${countTier1Additives(product)} Tier-1 additives — cumulative risk too high", 40)

    val hasNitrites = product.ingredients.any { ing ->
        val eNum = (ing.eNumber ?: "").uppercase().replace("\\s".toRegex(), "")
        eNum == "E249" || eNum == "E250" || ing.name.lowercase().let { it.contains("nitrite") || it.contains("e249") || it.contains("e250") }
    }
    val highSalt = n.saltG > 1.5
    val refined = product.ingredients.any { Regex("""farine de blé|farine raffinée|amidon|dextrose""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasNitrites && highSalt && refined && product.category == ProductCategory.PROCESSED_MEAT)
        return VetoCondition(true, "Processed meat with nitrites + high salt + refined starch", 40)

    // Beverage-specific rule checked first and it's the stricter cap (30 vs 40) —
    // otherwise a very sugary soda hit the generic >30g rule first and got the
    // looser cap, while a merely moderately sugary one got the stricter one.
    val sugars = n.addedSugarsG ?: n.sugarsG
    if (product.category == ProductCategory.BEVERAGE_SOFT && sugars > 5 && n.proteinG < 1 && n.fiberG < 1)
        return VetoCondition(true, "Sugar-sweetened beverage with no nutritional contribution", 30)

    if (product.category != ProductCategory.SNACK_SWEET && sugars > 30)
        return VetoCondition(true, "Added sugar >30g/100g in non-confectionery", 40)

    val hasMSM = product.ingredients.any { Regex("""séparée mécaniquement|mechanically separated|msm""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasMSM && product.novaClass == NovaClass.ULTRA_PROCESSED)
        return VetoCondition(true, "Mechanically separated meat in NOVA 4 product", 45)

    return VetoCondition(false, "", 100)
}

private fun buildFlags(audit: ScoreAudit): Pair<List<String>, List<String>> {
    val red = mutableListOf<String>()
    val green = mutableListOf<String>()

    val allDeductions = with(audit.pillars) {
        processing.deductions + nutritionalDensity.deductions + negativeNutrients.deductions +
        additiveRisk.deductions + ingredientIntegrity.deductions
    } + audit.globalPenalties

    val allBonuses = with(audit.pillars) {
        processing.bonuses + nutritionalDensity.bonuses + negativeNutrients.bonuses +
        additiveRisk.bonuses + ingredientIntegrity.bonuses
    } + audit.globalBonuses

    for (d in allDeductions) {
        if (d.severity == Severity.CRITICAL || d.severity == Severity.MAJOR) red += d.reason
    }
    for (b in allBonuses) {
        if (b.points >= 2) green += b.reason
    }

    val eco = audit.eco
    if (eco?.grade != null) {
        when (eco.grade.lowercase()) {
            "a", "b" -> green += "Eco-score ${eco.grade.uppercase()} — low environmental impact"
            "d", "e" -> red += "Eco-score ${eco.grade.uppercase()} — high environmental impact"
        }
    }

    if (audit.veto.triggered) red.add(0, "VETO: ${audit.veto.reason}")
    return Pair(red, green)
}

private fun collectWarnings(product: Product): List<String> {
    val warnings = mutableListOf<String>()
    if (product.nutrition.transFatG == null) warnings += "trans_fat_g not declared — assumed 0"
    if (product.nutrition.addedSugarsG == null) warnings += "added_sugars_g not declared — using total sugars as proxy"
    return warnings
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================

/**
 * Score a product. Pure synchronous function.
 * Mirrors scoreProduct() from scoring-engine.ts exactly.
 */
fun scoreProduct(input: Product): ScoreAudit {
    // Final fallback: if both OFF and LLM landed on 'other', infer from name
    val product = if (input.category == ProductCategory.OTHER) {
        val inferred = inferCategoryFromName(input.name)
        if (inferred != ProductCategory.OTHER) input.copy(category = inferred) else input
    } else input

    val processing          = scoreProcessing(product)
    val nutritionalDensity  = scoreNutritionalDensity(product)
    val negativeNutrients   = scoreNegativeNutrients(product)
    val additiveRisk        = scoreAdditiveRisk(product)
    val ingredientIntegrity = scoreIngredientIntegrity(product)

    val baseScore = processing.score + nutritionalDensity.score + negativeNutrients.score +
                    additiveRisk.score + ingredientIntegrity.score

    val globalBonuses   = computeGlobalBonuses(product)
    val globalPenalties = computeGlobalPenalties(product)
    val bonusTotal      = minOf(10.0, globalBonuses.sumOf { it.points })
    val penaltyTotal    = globalPenalties.sumOf { it.points }

    var score = (baseScore + bonusTotal + penaltyTotal).coerceIn(0.0, 100.0)
    val veto = checkVeto(product)
    if (veto.triggered && score > veto.cap) score = veto.cap.toDouble()
    val finalScore = score.roundToInt()
    val grade = scoreToGrade(finalScore)

    val pillars = ScoreAudit.Pillars(processing, nutritionalDensity, negativeNutrients, additiveRisk, ingredientIntegrity)

    val warnings = collectWarnings(product) +
        if (product.category != input.category) listOf("Category inferred from name as \"${product.category.key}\"") else emptyList()

    val preAudit = ScoreAudit(
        productName     = product.name,
        category        = product.category,
        score           = finalScore,
        grade           = grade,
        verdict         = gradeVerdict(grade),
        pillars         = pillars,
        globalBonuses   = globalBonuses,
        globalPenalties = globalPenalties,
        veto            = veto,
        redFlags        = emptyList(),
        greenFlags      = emptyList(),
        eco             = product.ecoscoreGrade?.let { ScoreAudit.EcoInfo(it, product.ecoscoreValue) },
        nutriscoreGrade = product.nutriscoreGrade,
        engineVersion   = ENGINE_VERSION,
        warnings        = warnings,
    )

    val (red, green) = buildFlags(preAudit)
    return preAudit.copy(redFlags = red, greenFlags = green)
}
