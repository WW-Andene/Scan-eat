package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Product

// Chronic migraine: alcohol (especially red wine), tyramine (aged cheese,
// cured/fermented foods), nitrite/nitrate preservatives (cured/processed
// meat) are the most consistently documented dietary migraine triggers
// (American Migraine Foundation; National Headache Foundation's
// low-tyramine diet). MSG and aspartame are commonly patient-reported
// triggers per the same sources, weighted lower here since the underlying
// evidence is more mixed than for the first three. Migraine triggers are
// individualized - not everyone reacts to every item - so this is framed
// as caution, never a veto.
internal fun checkMigraineCondition(
    product: Product,
    conditions: Set<String>,
    lang: String,
    alcoholHit: Boolean,
    adjustments: MutableList<PersonalAdjustment>,
) {
    if ("chronic_migraine" !in conditions) return
    val tyramineHit = product.ingredients.any { ing -> TYRAMINE_SOURCE_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    val nitrateHit = product.ingredients.any { ing -> NITRATE_PRESERVATIVE_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    val msgHit = product.ingredients.any { ing -> MSG_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    val aspartameHit = product.ingredients.any { ing -> ASPARTAME_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    if (alcoholHit) {
        adjustments += PersonalAdjustment(
            points = -3.0,
            reason = if (lang == "en") "Contains alcohol — one of the most consistently reported migraine triggers, especially red wine (American Migraine Foundation)"
                     else "Contient de l'alcool — l'un des déclencheurs de migraine les plus régulièrement rapportés, en particulier le vin rouge (American Migraine Foundation)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    if (tyramineHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains an aged/fermented ingredient (cheese, cured meat...) — a tyramine source, a well-documented migraine trigger (National Headache Foundation low-tyramine diet)"
                     else "Contient un ingrédient affiné/fermenté (fromage, charcuterie...) — une source de tyramine, déclencheur de migraine bien documenté (régime pauvre en tyramine, National Headache Foundation)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    if (nitrateHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains a nitrite/nitrate preservative — a documented migraine trigger in cured/processed meat (American Migraine Foundation)"
                     else "Contient un conservateur nitrite/nitrate — un déclencheur de migraine documenté dans la charcuterie/viande transformée (American Migraine Foundation)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    if (msgHit) {
        adjustments += PersonalAdjustment(
            points = -1.0,
            reason = if (lang == "en") "Contains MSG — reported as a migraine trigger by some patients (American Migraine Foundation)"
                     else "Contient du glutamate monosodique — rapporté comme déclencheur de migraine par certains patients (American Migraine Foundation)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    if (aspartameHit) {
        adjustments += PersonalAdjustment(
            points = -1.0,
            reason = if (lang == "en") "Contains aspartame — reported as a migraine trigger by some patients (American Migraine Foundation)"
                     else "Contient de l'aspartame — rapporté comme déclencheur de migraine par certains patients (American Migraine Foundation)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    if (!alcoholHit && !tyramineHit && !nitrateHit && !msgHit && !aspartameHit) {
        adjustments += PersonalAdjustment(
            points = 2.0,
            reason = if (lang == "en") "No common migraine trigger detected"
                     else "Aucun déclencheur de migraine habituel détecté",
            category = AdjustmentCategory.CONDITION,
        )
    }
}
