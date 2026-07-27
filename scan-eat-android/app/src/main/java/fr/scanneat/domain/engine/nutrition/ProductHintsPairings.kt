package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Product

// ============================================================================
// Pair well with / Avoid pairing with — two different senses of "pairing"
// folded into one panel: flavor-network co-occurrence (Ahn et al., the same
// PairingsDb ResultScreen's separate always-visible PairingsCard already
// draws on) and nutrient-absorption enhancer/inhibitor interactions — well-
// established dietetics, not product-specific medical advice (e.g. non-heme
// iron absorption enhanced ~3x by vitamin C, inhibited by tannins/calcium — a
// mechanism, not a claim about this exact product's effect on any one person).
// ============================================================================

/** Builds the pairWell/avoidPairing line pairs. [containsCaffeineSource] is
 *  computed once by [appendPersonalizedHints] (same \b-bounded ingredient
 *  match ANSES pregnancy guidance already uses) and passed in here rather
 *  than recomputed, so both callers agree on exactly one caffeine-source
 *  definition. */
internal fun buildPairings(product: Product, lang: String, containsCaffeineSource: Boolean): Pair<List<String>, List<String>> {
    val en = lang == "en"
    val n = product.nutrition
    val pairWell = mutableListOf<String>()
    val avoidPairing = mutableListOf<String>()

    val flavorPairs = fr.scanneat.domain.engine.planning.findPairings(product.name, limit = 4)
    if (flavorPairs.isNotEmpty()) {
        pairWell += if (en) "Goes well with: ${flavorPairs.joinToString(", ")} (flavor-pairing data)"
                    else "Se marie bien avec : ${flavorPairs.joinToString(", ")} (données d'accords culinaires)"
    }

    // NRV threshold for "iron source" = 15% of 14 mg (EU Reg 1169/2011 Annex XIII),
    // same percentage convention as the "high in" benefit checks above, just at
    // the lower "source of" tier since even a moderate iron contribution is
    // worth pairing correctly.
    val isIronSource = (n.ironMg ?: 0.0) >= 2.1
    val isCalciumSource = (n.calciumMg ?: 0.0) >= 120.0
    if (isIronSource) {
        pairWell += if (en) "Pair with a vitamin C source (citrus, peppers, kiwi) in the same meal — vitamin C can enhance non-heme iron absorption up to 3-fold"
                    else "Associez à une source de vitamine C (agrumes, poivron, kiwi) dans le même repas — la vitamine C peut multiplier jusqu'à 3 fois l'absorption du fer non héminique"
        avoidPairing += if (en) "Avoid pairing with tea, coffee, or a high-calcium dairy product in the same meal — tannins and calcium both significantly reduce iron absorption"
                        else "Évitez d'associer thé, café ou un produit laitier riche en calcium dans le même repas — tanins et calcium réduisent tous deux nettement l'absorption du fer"
    } else if (isCalciumSource) {
        // Only fires when the product isn't already the iron source itself,
        // so a food that's rich in both doesn't warn about pairing with itself.
        avoidPairing += if (en) "Avoid taking at the same time as an iron-rich food or supplement — calcium competes with iron for intestinal absorption (space by about 2 hours if both matter to you)"
                        else "Évitez de le prendre en même temps qu'un aliment ou complément riche en fer — le calcium entre en compétition avec le fer pour l'absorption intestinale (espacez d'environ 2 heures si les deux vous concernent)"
    }
    if (containsCaffeineSource && !isIronSource) {
        avoidPairing += if (en) "Avoid pairing with iron-rich meals — the tannins in coffee/tea/cocoa can cut iron absorption by up to 60%"
                        else "Évitez de l'associer à un repas riche en fer — les tanins du café/thé/cacao peuvent réduire l'absorption du fer jusqu'à 60 %"
    }
    n.vitDUg?.let { if (it >= 0.5) pairWell += if (en) "Best absorbed with a source of dietary fat in the same meal — vitamin D is fat-soluble"
                                                else "Mieux absorbée avec une source de matière grasse dans le même repas — la vitamine D est liposoluble" }
    n.zincMg?.let { if (it >= 1.5) pairWell += if (en) "Pairs well with animal protein in the same meal — zinc from animal sources is absorbed more efficiently than from plant sources alone"
                                                else "S'associe bien avec une protéine animale dans le même repas — le zinc d'origine animale est mieux absorbé que celui des seules sources végétales" }
    if (n.fiberG >= 6.0) {
        avoidPairing += if (en) "Very high-fiber foods can reduce the absorption of some minerals and oral medications if eaten at the exact same time — space by 1-2 hours from a supplement or medication dose"
                        else "Les aliments très riches en fibres peuvent réduire l'absorption de certains minéraux et médicaments oraux en cas de prise simultanée — espacez de 1 à 2 heures la prise d'un complément ou d'un médicament"
    }

    return Pair(pairWell, avoidPairing)
}
