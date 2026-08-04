package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.Profile

// ============================================================================
// Personalized (Profile.healthConditions/allergens/diet) hint lines — the
// counterpart to the population-level rules in ProductHintsBenefitsRisks.kt.
// Closes the gap with PersonalScoreEngine, which already personalizes the
// *score* for diabetes/hypertension/kidney_disease/pregnancy — the hint panel
// is a separate surface and previously ignored the profile entirely.
// Thresholds/wording deliberately mirror PersonalScoreEngine's own condition
// checks exactly so the two surfaces never disagree with each other.
// ============================================================================

/** Appends every profile-personalized hint line (allergens, diet violations,
 *  and Profile.healthConditions-driven cautions) to [benefits]/[conditionRisks].
 *  Returns whether the product contains a caffeine-source ingredient, so the
 *  pairing rules in ProductHintsPairings.kt can reuse the same check without
 *  recomputing it differently. */
internal fun appendPersonalizedHints(
    product: Product,
    profile: Profile,
    lang: String,
    benefits: MutableList<String>,
    conditionRisks: MutableList<String>,
): Boolean {
    val en = lang == "en"
    val n = product.nutrition

    // Allergen/diet violations were computed by PersonalScoreEngine for the
    // score itself (checkUserAllergens/checkDiet) but never surfaced here —
    // a user with a declared allergy got a score penalty with no
    // corresponding line in the hint panel explaining why.
    val allergenHits = fr.scanneat.domain.engine.scoring.checkUserAllergens(product, profile.allergens, lang)
    allergenHits.forEach { hit ->
        val label = if (en) hit.labelEn else hit.labelFr
        conditionRisks += if (en) "Contains $label — you've flagged this as an allergen" else "Contient $label — vous avez signalé cet allergène"
    }
    if (profile.diet != fr.scanneat.domain.engine.scoring.DietKey.NONE) {
        val dietResult = fr.scanneat.domain.engine.scoring.checkDiet(product, profile.diet, lang)
        if (!dietResult.compliant && dietResult.violations.isNotEmpty()) {
            val list = dietResult.violations.joinToString(", ")
            conditionRisks += if (en) "Not compliant with your diet — contains: $list" else "Non compatible avec votre régime — contient : $list"
        }
    }

    // Category-relative, matching PersonalScoreEngine.computePersonalScore's own
    // diabetes/hypertension/kidney_disease checks exactly (getThresholds() +
    // the same maxOf-against-the-original-flat-value pattern, and the same SSB
    // definition) - this section's own doc comment above claims these mirror
    // PersonalScoreEngine "exactly" so the two surfaces "never disagree," but
    // before this fix they used flat cutoffs while PersonalScoreEngine had
    // already been made category-aware in a prior round: a condiment at 20g
    // sugar (below its own 30g major tier - no diabetes penalty in the actual
    // score) still showed "high sugar, caution for diabetes" here, and every
    // typical soy sauce/cured meat/fresh meat or fish showed a salt/protein
    // caution the score itself no longer applied - the hint panel and the
    // score visibly disagreeing, exactly what this comment promises can't happen.
    val catThresholds = fr.scanneat.domain.engine.scoring.getThresholds(product.category)
    val isSugarSweetenedBeverage = product.category == fr.scanneat.domain.model.ProductCategory.BEVERAGE_SOFT &&
        (n.addedSugarsG ?: n.sugarsG) > 5.0 && n.proteinG < 1.0 && n.fiberG < 1.0
    val conditions = profile.healthConditions
    if ("diabetes" in conditions) {
        if (isSugarSweetenedBeverage) {
            conditionRisks += if (en) "Sugar-sweetened beverage — caution advised for diabetes (liquid sugar causes a faster glycemic spike)"
                     else "Boisson sucrée — prudence recommandée en cas de diabète (le sucre liquide provoque un pic glycémique plus rapide)"
        } else if (n.sugarsG >= catThresholds.sugarThresholds.third) conditionRisks += if (en) "High sugar (${n.sugarsG} g/100 g) — caution advised for diabetes"
                                        else "Sucres élevés (${n.sugarsG} g/100 g) — prudence recommandée en cas de diabète"
        else if (n.sugarsG <= catThresholds.sugarThresholds.first) benefits += if (en) "Low sugar — diabetes-friendly" else "Faible en sucres — adapté au diabète"
    }
    if ("hypertension" in conditions) {
        val hypertensionSaltBar = maxOf(1.2, catThresholds.saltThresholds.first)
        if (n.saltG >= hypertensionSaltBar) conditionRisks += if (en) "High salt (${n.saltG} g/100 g) — caution advised for hypertension"
                                     else "Sel élevé (${n.saltG} g/100 g) — prudence recommandée en cas d'hypertension"
        else if (n.saltG <= 0.3) benefits += if (en) "Low salt — hypertension-friendly" else "Faible en sel — adapté à l'hypertension"
        // Mirrors PersonalScoreEngine's own caffeine/hypertension check exactly
        // (same 20 mg/100g bar) - see that check's own doc comment for why.
        n.caffeineMg?.let { caffeine ->
            if (caffeine >= 20.0) {
                conditionRisks += if (en) "Contains caffeine (${caffeine.toInt()} mg/100 g) — can raise blood pressure acutely, caution advised for hypertension"
                                   else "Contient de la caféine (${caffeine.toInt()} mg/100 g) — peut élever la tension artérielle de façon aiguë, prudence recommandée en cas d'hypertension"
            }
        }
    }
    val kidneyProteinBar = maxOf(15.0, catThresholds.proteinG.third)
    if ("kidney_disease" in conditions && n.proteinG >= kidneyProteinBar) {
        conditionRisks += if (en) "High protein (${n.proteinG} g/100 g) — caution advised for kidney disease"
                 else "Protéines élevées (${n.proteinG} g/100 g) — prudence recommandée en cas de maladie rénale"
    }
    conditionRisks += findHealthConditionGuidance(product.ingredients, profile.healthConditions, lang)
    // \b-bounded, not `.contains()` on the normalized name - a plain substring
    // check let bare "mate" match inside "tomate" (tomato) and bare "tea"
    // match inside "steak", firing a false caffeine hint on completely
    // unrelated, extremely common ingredients. Bare "cafe" similarly matched
    // inside "décaféiné"/"decafeine" (decaf).
    val containsCaffeineSource = product.ingredients.any {
        val norm = fr.scanneat.domain.engine.scoring.normalizeForMatching(it.name)
        Regex("""\b(?:cafeine|guarana|yerba mate|mate|the vert|the noir|coffee|tea|cocoa|cacao|cafe)\b""")
            .containsMatchIn(norm)
    }
    if ("pregnancy" in conditions && containsCaffeineSource) {
        // ANSES: pregnant women should keep total caffeine intake under 200 mg/day —
        // a stricter, condition-specific limit than the generic EFSA "75 mg = alertness"
        // claim threshold already reported above for the general population. Flagged
        // on any caffeine-source ingredient, not just declared caffeine content,
        // since coffee/tea/cocoa rarely declare an exact mg figure on-label.
        conditionRisks += if (en) "May contain caffeine — ANSES recommends pregnant women keep total daily caffeine intake under 200 mg"
                 else "Peut contenir de la caféine — l'ANSES recommande de limiter l'apport total en caféine à 200 mg/jour pendant la grossesse"
    }
    // Same alcohol-content check PersonalScoreEngine already runs for its own
    // cancer/depression adjustments — kept in sync so the hint panel never
    // disagrees with the score adjustments section.
    // \b-bounded, not `.contains()` - a plain substring check on "vin " (space-
    // suffixed to avoid matching "vinaigre") missed the case where "vin" is the
    // ingredient's exact/final name with nothing following it.
    val containsAlcohol = product.ingredients.any {
        Regex("""\b(?:alcool|alcohol|vin|wine|bi[eè]re|beer)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.name)
    }
    if ("cancer" in conditions && containsAlcohol) {
        conditionRisks += if (en) "Contains alcohol — WCRF cancer prevention guidance recommends limiting alcohol intake"
                 else "Contient de l'alcool — les recommandations WCRF de prévention du cancer conseillent d'en limiter la consommation"
    }
    if ("depression" in conditions && containsAlcohol) {
        conditionRisks += if (en) "Contains alcohol — can worsen depressive symptoms and interacts with most antidepressants"
                 else "Contient de l'alcool — peut aggraver les symptômes dépressifs et interagit avec la plupart des antidépresseurs"
    }
    // Mirrors PersonalScoreEngine's own epilepsy adjustment exactly (same
    // Epilepsy Foundation sourcing) - was scored but had no hint-panel line,
    // the one condition where the two surfaces disagreed after epilepsy was
    // added to DietAndConditionAdjustments.kt without a matching entry here.
    if ("epilepsy" in conditions && containsAlcohol) {
        conditionRisks += if (en) "Contains alcohol — can lower seizure threshold and interacts with most anti-epileptic medications (Epilepsy Foundation)"
                 else "Contient de l'alcool — peut abaisser le seuil épileptogène et interagit avec la plupart des traitements antiépileptiques (Epilepsy Foundation)"
    }
    // Mirrors PersonalScoreEngine's own sugar/NOVA depression adjustments exactly
    // (same 15.0 g threshold, same two cited cohort studies) so the two surfaces
    // never disagree.
    if ("depression" in conditions && n.sugarsG >= 15.0) {
        conditionRisks += if (en) "High sugar (${n.sugarsG} g/100 g) — prospectively associated with depression risk (Knüppel et al., Whitehall II cohort, Sci Rep 2017)"
                 else "Sucres élevés (${n.sugarsG} g/100 g) — associé de façon prospective au risque de dépression (Knüppel et al., cohorte Whitehall II, Sci Rep 2017)"
    }
    if ("depression" in conditions && product.novaClass == NovaClass.ULTRA_PROCESSED) {
        conditionRisks += if (en) "Ultra-processed (NOVA 4) — prospectively associated with incident depressive symptoms (Adjibade et al., NutriNet-Santé cohort, BMC Medicine 2019)"
                 else "Ultra-transformé (NOVA 4) — associé de façon prospective à l'apparition de symptômes dépressifs (Adjibade et al., cohorte NutriNet-Santé, BMC Medicine 2019)"
    }

    return containsCaffeineSource
}
