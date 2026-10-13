package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 9: PILLAR 5 — INGREDIENT INTEGRITY (max 15)
// ============================================================================

private val REFINED_MARKER = Regex("""sirop|huile|farine raffinée|amidon modifié|isolat|concentré""", RegexOption.IGNORE_CASE)
private val ISOLATE_MARKER = Regex("""isolat|hydrolysat|concentré|modifié|extrait sec""", RegexOption.IGNORE_CASE)
private val SUGAR_PREFIX = Regex("""^sucre""", RegexOption.IGNORE_CASE)

// Word-boundary match rather than raw `.contains` - plain substring search lets
// e.g. "riz" match inside "chorizo" or "eau" match inside "gâteau"/"veau",
// wrongly counting processed ingredients as whole foods. Optional trailing
// s/x so plural label forms ("carottes", "lentilles", "choux") still match
// the singular keyword list.
private fun containsWord(text: String, word: String): Boolean =
    Regex("(?<![a-zà-ÿ0-9])${Regex.escape(word)}[sx]?(?![a-zà-ÿ0-9])", RegexOption.IGNORE_CASE).containsMatchIn(text)

private fun isWholeFood(ingredient: Ingredient, category: ProductCategory): Boolean {
    if (ingredient.isWholeFood == true) return true
    val lower = ingredient.name.lowercase()
    if (REFINED_MARKER.containsMatchIn(lower)) return false
    // User-reported: "eau" matched WHOLE_FOOD_KEYWORDS unconditionally, so a
    // ready meal/sauce/soup listing "Eau" as ingredient #1 by weight (EU FIC
    // 1169/2011 descending-weight ordering makes this common - water is the
    // base of most manufactured foods) earned the same "whole food" credit
    // as a genuinely raw ingredient like "Tomate", rewarding the product for
    // containing the one substance present in nearly every processed food,
    // not for actually being minimally processed. Water only IS the product
    // (deserving full whole-food credit) in BEVERAGE_WATER; everywhere else
    // it's a context-free diluent this axis shouldn't reward - it still
    // counts toward the separate "recognizable ingredients" bonus below
    // (plain, non-additive, legible name), just not toward whole-food
    // composition outside the one category where it genuinely is one.
    if (category == ProductCategory.BEVERAGE_WATER && containsWord(lower, "eau")) return true
    return WHOLE_FOOD_KEYWORDS.any { containsWord(lower, it) }
}

/** Points of this pillar that depend on the ingredient list (all but the 2 origin points). */
internal const val INGREDIENT_ONLY_INTEGRITY_POINTS = 13.0

fun scoreIngredientIntegrity(product: Product, lang: String = "en"): PillarScore {
    val en = lang == "en"
    val MAX = 15
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    var score = 0.0
    // S3: without an ingredient list, none of the composition criteria below
    // can be assessed - previously the empty list scored 3/3 "recognizable"
    // and "+2 No hidden sugars". Only the origin criterion remains; the other
    // 13 points are excluded from the score (INGREDIENT_ONLY_INTEGRITY_POINTS).
    if (product.ingredients.isEmpty()) {
        val hasOriginInfo = product.originTransparent || !product.origin.isNullOrBlank()
        if (hasOriginInfo) {
            score += 2
            bonuses += Deduction("ingredient_integrity", (if (en) "Origin declared: " else "Origine déclarée : ") + (product.origin ?: ""), 2.0, Severity.INFO)
        }
        deductions += Deduction("ingredient_integrity",
            if (en) "No ingredient list — composition criteria not assessed (left out of the score)"
            else "Liste d'ingrédients absente — critères de composition non évalués (exclus du calcul)", 0.0, Severity.INFO)
        return PillarScore(if (en) "Ingredient Integrity" else "Intégrité des ingrédients", MAX, score, deductions, bonuses)
    }

    // 1. First 3 whole foods (+5) — divide by the actual count taken (up to 3),
    // not a fixed 3: a single-ingredient whole food (e.g. "Pommes") is 100%
    // whole-food and deserves the full 5, not 1/3 of it just for having a
    // short ingredient list.
    //
    // Deliberately a different axis from ProcessingPillar's NOVA score, not
    // the same fact paid out twice: NOVA measures how much the product as a
    // whole was industrially TRANSFORMED (additives, texturizers, process
    // markers), while this measures whether the DECLARED INGREDIENT LIST is
    // itself transparent/recognizable — a product can be NOVA-unprocessed
    // (an apple) and trivially score both, but a NOVA-ultra-processed product
    // can still have a short, plainly-worded first-3-ingredients list (or
    // not), so the two scores genuinely diverge on real products rather than
    // being redundant restatements of one signal.
    val first3 = product.ingredients.take(3)
    val first3Whole = first3.count { isWholeFood(it, product.category) }
    val first3Score = if (first3.isEmpty()) 0.0
        else ((first3Whole.toDouble() / first3.size) * 5.0).let { kotlin.math.round(it).toDouble() }
    score += first3Score
    if (first3Score < 5) deductions += Deduction("ingredient_integrity", if (en) "Only $first3Whole/3 first ingredients are whole foods (${first3Score.toInt()}/5)" else "Seulement $first3Whole/3 premiers ingrédients sont des aliments bruts (${first3Score.toInt()}/5)", first3Score - 5, Severity.MODERATE)
    else bonuses += Deduction("ingredient_integrity", if (en) "First 3 ingredients are all whole foods" else "Les 3 premiers ingrédients sont des aliments bruts", 5.0, Severity.INFO)

    // 2. Overall recognizability (+3)
    val nonAdditive = product.ingredients.filter { findAdditive(it.eNumber, it.name, it.category) == null }
    val recognizable = nonAdditive.count { ing ->
        val n = ing.name.lowercase()
        n.length < 40 && !ISOLATE_MARKER.containsMatchIn(n)
    }
    // nonAdditive.isEmpty() (a product whose entire declared ingredient list
    // is additives, e.g. ["E330", "E331", "E202"]) previously defaulted to
    // 1.0 - a vacuous "0 recognizable out of 0" was read as 100% recognizable
    // and awarded the full +3 bonus, the opposite of what an additive-only
    // ingredient list should signal. 0.0 is the correct signal: there is
    // nothing recognizable to credit.
    val recogRatio = if (nonAdditive.isNotEmpty()) recognizable.toDouble() / nonAdditive.size
        else if (product.ingredients.isNotEmpty()) 0.0 else 1.0
    val recogScore = when {
        recogRatio >= 0.8 -> 3.0
        recogRatio >= 0.6 -> 2.0
        recogRatio >= 0.4 -> 1.0
        else              -> 0.0
    }
    score += recogScore
    if (recogScore < 3) deductions += Deduction("ingredient_integrity", if (en) "${(recogRatio * 100).toInt()}% recognizable ingredients (${recogScore.toInt()}/3)" else "${(recogRatio * 100).toInt()}% d'ingrédients reconnaissables (${recogScore.toInt()}/3)", recogScore - 3, Severity.MINOR)

    // 3. Origin transparency (+2)
    // origin != null alone previously counted a blank-but-non-null string
    // (a real OFF data shape from partially-scraped records) as if it were
    // meaningful origin information - isNullOrBlank() requires actual content.
    val hasOrigin = !product.origin.isNullOrBlank()
    if (product.originTransparent || hasOrigin) {
        score += 2
        bonuses += Deduction("ingredient_integrity", (if (en) "Origin declared: " else "Origine déclarée : ") + (product.origin?.takeIf { hasOrigin } ?: (if (en) "transparent" else "transparente")), 2.0, Severity.INFO)
    } else {
        deductions += Deduction("ingredient_integrity", if (en) "No origin information" else "Aucune information d'origine", -2.0, Severity.MINOR)
    }

    // 4. Hidden sugars (+2)
    val sugarAliases = mutableSetOf<String>()
    for (ing in product.ingredients) {
        // Additive ingredients excluded - AdditivesDb.kt/AdditivesTier2.kt
        // model "caramel" (E150/E150a-d) explicitly as a COLORANT, not a
        // sweetener, but HIDDEN_SUGAR_NAMES' bare "caramel" entry matched it
        // anyway since this loop (unlike the recognizable-ingredient check
        // just above, which filters via findAdditive(...) == null) never
        // excluded additive ingredients. A cola or dark spirit listing
        // "colorant : caramel" was silently counted as a sugar source
        // alongside real syrups, inflating the >=2-sources deduction below
        // for an ingredient that contributes zero sugar.
        if (findAdditive(ing.eNumber, ing.name, ing.category) != null) continue
        val n = ing.name.lowercase()
        // Longest matching alias wins, not every matching alias - HIDDEN_SUGAR_NAMES
        // has nested substrings ("glucose" and "sirop" both sit inside "sirop de
        // glucose"), so a single ingredient like "sirop de glucose" previously
        // added all 3 as independent "sources", inflating one ingredient into a
        // false >=2-sources deduction below. Same longest-synonym-wins principle
        // AdditivesDb.findAdditive already applies for the identical nested-
        // substring shape.
        HIDDEN_SUGAR_NAMES.filter { n.contains(it) }.maxByOrNull { it.length }?.let { sugarAliases += it }
        if (SUGAR_PREFIX.containsMatchIn(ing.name.trim())) sugarAliases += "sucre"
    }
    if (sugarAliases.size >= 2) {
        deductions += Deduction("ingredient_integrity", (if (en) "${sugarAliases.size} distinct sugar sources: " else "${sugarAliases.size} sources de sucre distinctes : ") + sugarAliases.joinToString(), -2.0, Severity.MODERATE)
    } else {
        score += 2
        bonuses += Deduction("ingredient_integrity", if (sugarAliases.size == 1) (if (en) "Single transparent sugar source" else "Source de sucre unique et transparente") else (if (en) "No hidden sugars" else "Aucun sucre caché"), 2.0, Severity.INFO)
    }

    // 5. Named oils (+3)
    val hasGenericOil = product.ingredients.any { ing ->
        val n = ing.name.lowercase()
        GENERIC_OIL_TERMS.any { n.contains(it) }
    }
    // product.namedOils is near-always null for barcode-scanned OFF products
    // (it's only populated on the LLM fresh-food-identification path), so
    // `product.namedOils != false` was true for virtually every product
    // regardless of whether it contained any oil at all - an oil-free product
    // (fish fillet, yogurt, soda) silently collected the same +3 "oils
    // specifically named" bonus as one that genuinely listed olive/sunflower
    // oil by name. Requiring an actual NAMED_OIL_TERMS match closes that gap;
    // a product with no oil ingredient now scores neutral on this sub-check
    // instead of vetoed or rewarded.
    val hasNamedOil = product.ingredients.any { ing ->
        val n = ing.name.lowercase()
        NAMED_OIL_TERMS.any { n.contains(it) }
    }
    if (hasNamedOil && !hasGenericOil) {
        score += 3
        bonuses += Deduction("ingredient_integrity", if (en) "Oils specifically named" else "Huiles nommées précisément", 3.0, Severity.INFO)
    } else if (hasGenericOil) {
        deductions += Deduction("ingredient_integrity", if (en) "Generic \"vegetable oil\" instead of specific named oil" else "Huile végétale générique au lieu d'une huile précisément nommée", -3.0, Severity.MINOR)
    }

    return PillarScore(if (en) "Ingredient Integrity" else "Intégrité des ingrédients", MAX, maxOf(0.0, minOf(MAX.toDouble(), score)), deductions, bonuses)
}
