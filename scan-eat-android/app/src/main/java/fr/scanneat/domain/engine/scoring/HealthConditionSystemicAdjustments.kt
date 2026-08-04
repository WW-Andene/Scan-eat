package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product

/** Pregnancy's alcohol veto plus its own ANSES caffeine caution - the only
 *  condition in this file whose alcohol hit is a veto rather than a caution. */
internal fun checkPregnancyCondition(
    product: Product,
    conditions: Set<String>,
    lang: String,
    alcoholHit: Boolean,
    adjustments: MutableList<PersonalAdjustment>,
): Pair<Boolean, String?>? {
    if ("pregnancy" !in conditions) return null
    var veto = false
    var dietReason: String? = null
    if (alcoholHit) {
        veto = true
        val reason = if (lang == "en") "Contains alcohol — avoid during pregnancy"
                     else "Contient de l'alcool — à éviter pendant la grossesse"
        dietReason = dietReason ?: reason
        adjustments += PersonalAdjustment(0.0, reason, AdjustmentCategory.CONDITION, veto = true)
    }
    // ANSES's 200mg/day pregnancy caffeine cap was already cited in the hint
    // panel (ProductHints.kt's containsCaffeineSource) but never actually
    // affected the *score* for a pregnant profile - a caffeinated soda or
    // coffee-flavored product scored identically to a decaf one here. Same
    // ingredient-name heuristic as the hint panel (kept duplicated rather
    // than shared, same rationale as this file's other small helpers).
    val containsCaffeineSource = product.ingredients.any { ing ->
        CAFFEINE_SOURCE_PATTERN.containsMatchIn(normalizeForMatching(ing.name))
    }
    if (containsCaffeineSource) {
        adjustments += PersonalAdjustment(
            points = -3.0,
            reason = if (lang == "en") "May contain caffeine — ANSES recommends pregnant women keep total daily caffeine intake under 200 mg"
                     else "Peut contenir de la caféine — l'ANSES recommande de limiter l'apport total en caféine à 200 mg/jour pendant la grossesse",
            category = AdjustmentCategory.CONDITION,
        )
    }
    return veto to dietReason
}

/** Cancer, depression and epilepsy's shared alcohol caution, plus depression's
 *  own sugar/ultra-processed prospective-risk checks. */
internal fun checkCancerDepressionEpilepsyConditions(
    product: Product,
    conditions: Set<String>,
    lang: String,
    alcoholHit: Boolean,
    isSugarSweetenedBeverage: Boolean,
    adjustments: MutableList<PersonalAdjustment>,
) {
    // WCRF/AICR (World Cancer Research Fund) Cancer Prevention Recommendations:
    // alcohol intake is a well-established risk factor for several cancer types —
    // a caution, not a veto, since abstinence isn't universally medically required
    // the way it is in pregnancy.
    if ("cancer" in conditions && alcoholHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains alcohol — WCRF cancer prevention guidance recommends limiting alcohol intake"
                     else "Contient de l'alcool — les recommandations WCRF de prévention du cancer conseillent d'en limiter la consommation",
            category = AdjustmentCategory.CONDITION,
        )
    }
    // NHS/CDC guidance: alcohol is a depressant that can worsen depressive symptoms
    // and interacts with most antidepressant classes (notably MAOIs and, to a
    // lesser extent, SSRIs) — a caution, not a veto.
    if ("depression" in conditions && alcoholHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains alcohol — can worsen depressive symptoms and interacts with most antidepressants"
                     else "Contient de l'alcool — peut aggraver les symptômes dépressifs et interagit avec la plupart des antidépresseurs",
            category = AdjustmentCategory.CONDITION,
        )
    }
    // Knüppel et al., Scientific Reports 2017 (Whitehall II cohort): higher sweet
    // food/*beverage* sugar intake prospectively associated with incident common
    // mental disorder and depression in men over ~5 years follow-up - sweet
    // beverages are literally half of what that cohort measured, so the SSB
    // bar applies here too, not just the flat 15g solid-food bar.
    if ("depression" in conditions && (isSugarSweetenedBeverage || product.nutrition.sugarsG >= 15.0)) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "High sugar (${product.nutrition.sugarsG} g/100 g) — prospectively associated with depression risk (Knüppel et al., Whitehall II cohort, Sci Rep 2017)"
                     else "Sucres élevés (${product.nutrition.sugarsG} g/100 g) — associé de façon prospective au risque de dépression (Knüppel et al., cohorte Whitehall II, Sci Rep 2017)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    // Adjibade et al., BMC Medicine 2019 (French NutriNet-Santé cohort): higher
    // ultra-processed food consumption prospectively associated with incident
    // depressive symptoms.
    if ("depression" in conditions && product.novaClass == NovaClass.ULTRA_PROCESSED) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Ultra-processed (NOVA 4) — prospectively associated with incident depressive symptoms (Adjibade et al., NutriNet-Santé cohort, BMC Medicine 2019)"
                     else "Ultra-transformé (NOVA 4) — associé de façon prospective à l'apparition de symptômes dépressifs (Adjibade et al., cohorte NutriNet-Santé, BMC Medicine 2019)",
            category = AdjustmentCategory.CONDITION,
        )
    }

    // Epilepsy Foundation guidance: alcohol lowers the seizure threshold and
    // interacts with most anti-epileptic drugs (reduced efficacy, increased
    // sedation) - a caution, not a veto, same framing as cancer/depression's
    // alcohol checks above.
    if ("epilepsy" in conditions && alcoholHit) {
        adjustments += PersonalAdjustment(
            points = -2.0,
            reason = if (lang == "en") "Contains alcohol — can lower seizure threshold and interacts with most anti-epileptic medications (Epilepsy Foundation)"
                     else "Contient de l'alcool — peut abaisser le seuil épileptogène et interagit avec la plupart des traitements antiépileptiques (Epilepsy Foundation)",
            category = AdjustmentCategory.CONDITION,
        )
    }
}
