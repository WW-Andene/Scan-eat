package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ============================================================================
// FOOD SCORING ENGINE v2.3.0 — Kotlin port of scoring-engine.ts
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

private fun gradeVerdict(grade: Grade, lang: String = "en"): String = if (lang == "en") when (grade) {
    Grade.A_PLUS -> "Excellent — daily staple potential"
    Grade.A      -> "Good — regular consumption fine"
    Grade.B      -> "Acceptable — moderate frequency"
    Grade.C      -> "Mediocre — occasional only"
    Grade.D      -> "Poor — avoid regular use"
    Grade.F      -> "Very poor — avoid"
} else when (grade) {
    Grade.A_PLUS -> "Excellent — potentiel de consommation quotidienne"
    Grade.A      -> "Bon — consommation régulière adaptée"
    Grade.B      -> "Acceptable — fréquence modérée"
    Grade.C      -> "Médiocre — occasionnel uniquement"
    Grade.D      -> "Mauvais — à éviter en usage régulier"
    Grade.F      -> "Très mauvais — à éviter"
}

private fun computeGlobalBonuses(product: Product, lang: String = "en"): List<Deduction> {
    val en = lang == "en"
    val bonuses = mutableListOf<Deduction>()
    if (product.organic) bonuses += Deduction("global_bonus", if (en) "Organic certification" else "Certification biologique", 2.0, Severity.INFO)
    if (product.wholeGrainPrimary) bonuses += Deduction("global_bonus", if (en) "Whole grain as primary grain" else "Céréale complète en ingrédient principal", 3.0, Severity.INFO)
    if (product.fermented) bonuses += Deduction("global_bonus", if (en) "Contains fermented / probiotic content" else "Contient des éléments fermentés / probiotiques", 2.0, Severity.INFO)
    // Omega-3 bonus lives solely in NutritionalDensityPillar.scoreNutritionalDensity()
    // (+3, using the same ingredient regex plus a nutrition-value check) - it was
    // duplicated here too, double-counting the same signal for +5 total on any
    // product containing e.g. flaxseed or salmon.
    return bonuses
}

private fun computeGlobalPenalties(product: Product, lang: String = "en"): List<Deduction> {
    val en = lang == "en"
    val penalties = mutableListOf<Deduction>()
    if (product.hasMisleadingMarketing) penalties += Deduction("global_penalty", if (en) "Misleading marketing claims" else "Allégations marketing trompeuses", -2.0, Severity.MODERATE)
    if (product.hasHealthClaims) penalties += Deduction("global_penalty", if (en) "Health claims present — verify vs composition" else "Allégations de santé présentes — à vérifier vs composition", -3.0, Severity.MODERATE)
    val palm = product.ingredients.find { ing ->
        Regex("""huile de palme|huile de palmiste|graisse de palme|st[eé]arine de palme|ol[eé]ine de palme|palm oil|palm kernel|coprah""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name)
    }
    if (palm != null) penalties += Deduction("global_penalty", (if (en) "Palm oil or derivative: " else "Huile de palme ou dérivé : ") + palm.name, -3.0, Severity.MODERATE)
    return penalties
}

private fun checkVeto(product: Product, lang: String = "en"): VetoCondition {
    val en = lang == "en"
    val n = product.nutrition

    if ((n.transFatG ?: 0.0) > 0.1)
        return VetoCondition(true, if (en) "Contains industrial trans fats — no safe level" else "Contient des graisses trans industrielles — aucun seuil sûr", 40)

    if (countTier1Additives(product) > 3)
        return VetoCondition(true, (if (en) "${countTier1Additives(product)} Tier-1 additives — cumulative risk too high" else "${countTier1Additives(product)} additifs de niveau 1 — risque cumulé trop élevé"), 40)

    val hasNitrites = product.ingredients.any { ing ->
        val eNum = (ing.eNumber ?: "").uppercase().replace("\\s".toRegex(), "")
        eNum == "E249" || eNum == "E250" || ing.name.lowercase().let { it.contains("nitrite") || it.contains("e249") || it.contains("e250") }
    }
    val highSalt = n.saltG > 1.5
    val refined = product.ingredients.any { Regex("""farine de blé|farine raffinée|amidon|dextrose""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasNitrites && highSalt && refined && product.category == ProductCategory.PROCESSED_MEAT)
        return VetoCondition(true, if (en) "Processed meat with nitrites + high salt + refined starch" else "Viande transformée avec nitrites + sel élevé + amidon raffiné", 40)

    // Beverage-specific rule checked first and it's the stricter cap (30 vs 40) —
    // otherwise a very sugary soda hit the generic >30g rule first and got the
    // looser cap, while a merely moderately sugary one got the stricter one.
    val sugars = n.addedSugarsG ?: n.sugarsG
    if (product.category == ProductCategory.BEVERAGE_SOFT && sugars > 5 && n.proteinG < 1 && n.fiberG < 1)
        return VetoCondition(true, if (en) "Sugar-sweetened beverage with no nutritional contribution" else "Boisson sucrée sans apport nutritionnel", 30)

    if (product.category != ProductCategory.SNACK_SWEET && sugars > 30)
        return VetoCondition(true, if (en) "Added sugar >30g/100g in non-confectionery" else "Sucre ajouté >30g/100g dans un produit non-confiserie", 40)

    val hasMSM = product.ingredients.any { Regex("""séparée mécaniquement|mechanically separated|msm""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
    if (hasMSM && product.novaClass == NovaClass.ULTRA_PROCESSED)
        return VetoCondition(true, if (en) "Mechanically separated meat in NOVA 4 product" else "Viande séparée mécaniquement dans un produit NOVA 4", 45)

    return VetoCondition(false, "", 100)
}

private fun buildFlags(audit: ScoreAudit, lang: String = "en"): Pair<List<String>, List<String>> {
    val en = lang == "en"
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
            "a", "b" -> green += (if (en) "Eco-score ${eco.grade.uppercase()} — low environmental impact" else "Éco-score ${eco.grade.uppercase()} — faible impact environnemental")
            "d", "e" -> red += (if (en) "Eco-score ${eco.grade.uppercase()} — high environmental impact" else "Éco-score ${eco.grade.uppercase()} — impact environnemental élevé")
        }
    }

    if (audit.veto.triggered) red.add(0, (if (en) "VETO: " else "VETO : ") + audit.veto.reason)
    return Pair(red, green)
}

private fun collectWarnings(product: Product, lang: String = "en"): List<String> {
    val en = lang == "en"
    val warnings = mutableListOf<String>()
    if (product.nutrition.transFatG == null) warnings += (if (en) "trans_fat_g not declared — assumed 0" else "trans_fat_g non déclaré — supposé 0")
    if (product.nutrition.addedSugarsG == null) warnings += (if (en) "added_sugars_g not declared — using total sugars as proxy" else "added_sugars_g non déclaré — sucres totaux utilisés en approximation")
    return warnings
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================

/**
 * Score a product. Pure synchronous function.
 * Mirrors scoreProduct() from scoring-engine.ts exactly.
 */
fun scoreProduct(input: Product, lang: String = "en"): ScoreAudit {
    val en = lang == "en"
    // Final fallback: if both OFF and LLM landed on 'other', infer from name
    val product = if (input.category == ProductCategory.OTHER) {
        val inferred = inferCategoryFromName(input.name)
        if (inferred != ProductCategory.OTHER) input.copy(category = inferred) else input
    } else input

    val processing          = scoreProcessing(product, lang)
    val nutritionalDensity  = scoreNutritionalDensity(product, lang)
    val negativeNutrients   = scoreNegativeNutrients(product, lang)
    val additiveRisk        = scoreAdditiveRisk(product, lang)
    val ingredientIntegrity = scoreIngredientIntegrity(product, lang)

    val baseScore = processing.score + nutritionalDensity.score + negativeNutrients.score +
                    additiveRisk.score + ingredientIntegrity.score

    val globalBonuses   = computeGlobalBonuses(product, lang)
    val globalPenalties = computeGlobalPenalties(product, lang)
    val bonusTotal      = minOf(10.0, globalBonuses.sumOf { it.points })
    val penaltyTotal    = globalPenalties.sumOf { it.points }

    var score = (baseScore + bonusTotal + penaltyTotal).coerceIn(0.0, 100.0)
    val veto = checkVeto(product, lang)
    if (veto.triggered && score > veto.cap) score = veto.cap.toDouble()
    val finalScore = score.roundToInt()
    val grade = scoreToGrade(finalScore)

    val pillars = ScoreAudit.Pillars(processing, nutritionalDensity, negativeNutrients, additiveRisk, ingredientIntegrity)

    val warnings = collectWarnings(product, lang) +
        if (product.category != input.category) listOf(if (en) "Category inferred from name as \"${product.category.key}\"" else "Catégorie déduite du nom : \"${product.category.key}\"") else emptyList()

    val preAudit = ScoreAudit(
        productName     = product.name,
        category        = product.category,
        score           = finalScore,
        grade           = grade,
        verdict         = gradeVerdict(grade, lang),
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

    val (red, green) = buildFlags(preAudit, lang)
    return preAudit.copy(redFlags = red, greenFlags = green)
}
