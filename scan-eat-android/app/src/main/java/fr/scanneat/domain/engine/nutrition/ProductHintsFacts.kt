package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Product

// ============================================================================
// Key info (NOVA class, energy) + Facts — the unconditional baseline-context
// section and the curated ingredient trivia section, both shown ahead of the
// risk/benefit lines rather than folded into them.
// ============================================================================

/** NOVA processing class + energy density — shown in their own section ahead
 *  of risks/benefits, not folded into [buildFacts]: these two apply to every
 *  product unconditionally and read as baseline context the user should see
 *  before the risk/benefit lines below, not curiosity-tier trivia alongside
 *  origin/cholesterol. */
internal fun buildKeyInfo(product: Product, lang: String): List<String> {
    val en = lang == "en"
    val n = product.nutrition
    val keyInfo = mutableListOf<String>()
    keyInfo += if (en) "NOVA processing class: ${product.novaClass.value}/4" else "Classe de transformation NOVA : ${product.novaClass.value}/4"
    keyInfo += if (en) "Energy: ${n.energyKcal} kcal/100 g" else "Énergie : ${n.energyKcal} kcal/100 g"
    return keyInfo
}

/** Origin, cholesterol, and curated ingredient trivia (see IngredientFactsDb
 *  for why this is a hand-curated substring match rather than LLM-generated). */
internal fun buildFacts(product: Product, lang: String): List<String> {
    val en = lang == "en"
    val n = product.nutrition
    val facts = mutableListOf<String>()
    if (product.origin != null) facts += if (en) "Origin: ${product.origin}" else "Origine : ${product.origin}"
    n.cholesterolMg?.let { facts += if (en) "Cholesterol: ${it} mg/100 g" else "Cholestérol : ${it} mg/100 g" }
    facts += findIngredientFacts(product.ingredients, lang)
    return facts
}
