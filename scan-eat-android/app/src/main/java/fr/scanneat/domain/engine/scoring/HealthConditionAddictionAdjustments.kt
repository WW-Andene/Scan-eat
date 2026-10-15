package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Product

/** Products sold as "alcohol-free": still up to 1.2 % vol in France. */
private val ALCOHOL_FREE_CLAIM = Regex("""\b(?:sans alcool|alcohol[- ]free|non[- ]alcoholic|0[,.]0 ?%)""", RegexOption.IGNORE_CASE)

/** Beta-carotene as a nutrient (supplement / provitamin A), not the E160a colour. */
private val BETA_CAROTENE = Regex("""\b(?:beta[- ]?carotene|provitamine a)\b""")
private val COLOUR_CONTEXT = Regex("""\b(?:colorants?|colou?rs?)\b""")
private val SUPPLEMENT_CONTEXT = Regex("""\b(?:complement alimentaire|food supplement|gelules?|capsules?|comprimes?|tablets?)\b""")

/**
 * Smoker, quitting tobacco and quitting alcohol. Returns the veto reason when
 * the product must be avoided (alcohol while quitting alcohol), null otherwise;
 * cautions are added to [adjustments].
 */
internal fun checkAddictionConditions(
    product: Product,
    conditions: Set<String>,
    lang: String,
    alcoholHit: Boolean,
    adjustments: MutableList<PersonalAdjustment>,
): String? {
    val en = lang == "en"
    var vetoReason: String? = null
    if ("alcohol_quit" in conditions) {
        if (alcoholHit) {
            val reason = if (en) "Contains alcohol — avoid it while quitting alcohol, even in small amounts (cooking, desserts, liqueur chocolates)"
                else "Contient de l'alcool — à éviter pendant l'arrêt de l'alcool, même en petite quantité (cuisine, desserts, chocolats à la liqueur)"
            vetoReason = reason
            adjustments += PersonalAdjustment(0.0, reason, AdjustmentCategory.CONDITION, veto = true)
        } else if (ALCOHOL_FREE_CLAIM.containsMatchIn(product.name)) {
            // Code de la santé publique, art. L3321-1: drinks up to 1.2 % vol are not
            // legally "alcoholic", so an "alcohol-free" beer or wine can still contain some.
            adjustments += PersonalAdjustment(-3.0,
                if (en) "\"Alcohol-free\" drink: in France it can still contain up to 1.2 % vol (Public Health Code L3321-1) and keep the habit going — discuss it with your doctor or addiction specialist"
                else "Boisson « sans alcool » : elle peut contenir jusqu'à 1,2 % vol (Code de la santé publique, L3321-1) et entretenir l'habitude — à discuter avec votre médecin ou addictologue",
                AdjustmentCategory.CONDITION)
        }
    }
    if ("tobacco_quit" in conditions) {
        val caffeine = product.nutrition.caffeineMg?.let { it >= 20.0 }
            ?: product.ingredients.any { CAFFEINE_SOURCE_PATTERN.containsMatchIn(normalizeForMatching(it.name)) }
        // Tobacco smoke induces CYP1A2, the enzyme that clears caffeine: after
        // quitting, the same coffee gives higher caffeine levels (Tabac Info Service).
        if (caffeine) adjustments += PersonalAdjustment(-2.0,
            if (en) "Contains caffeine — after quitting smoking, caffeine is cleared more slowly: the same amount can cause nervousness or insomnia that feel like cravings; cutting down can help (Tabac Info Service)"
            else "Contient de la caféine — après l'arrêt du tabac, elle s'élimine plus lentement : la même dose peut donner nervosité ou insomnie, confondues avec le manque ; réduire le café peut aider (Tabac Info Service)",
            AdjustmentCategory.CONDITION)
    }
    if ("smoker" in conditions || "tobacco_quit" in conditions) {
        // EFSA 2012 (ANS Panel, beta-carotene in smokers): supplements of 20 mg/day
        // or more raise lung cancer risk in heavy smokers.
        val name = normalizeForMatching(product.name)
        val supplement = SUPPLEMENT_CONTEXT.containsMatchIn(name)
        val betaCarotene = product.ingredients.any { ing ->
            val n = normalizeForMatching(ing.name)
            BETA_CAROTENE.containsMatchIn(n) && !COLOUR_CONTEXT.containsMatchIn(n)
        }
        if (supplement && betaCarotene) adjustments += PersonalAdjustment(-5.0,
            if (en) "Beta-carotene supplement — in smokers, supplements (20 mg/day or more) increase lung cancer risk (EFSA 2012); avoid unless your doctor advises it"
            else "Complément de bêta-carotène — chez les fumeurs, ces compléments (20 mg/jour ou plus) augmentent le risque de cancer du poumon (EFSA 2012) ; à éviter sauf avis médical",
            AdjustmentCategory.CONDITION)
    }
    return vetoReason
}
