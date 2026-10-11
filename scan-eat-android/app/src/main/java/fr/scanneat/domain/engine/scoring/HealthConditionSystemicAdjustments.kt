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
    // coffee-flavored product scored identically to a decaf one here.
    //
    // Checks the declared numeric value first, same 20mg/100g bar
    // HealthConditionMetabolicAdjustments' hypertension check and
    // HealthConditionGiAdjustments' chronic-diarrhea check both use for the
    // identical underlying question ("does this product contain caffeine?") -
    // previously this was the only one of the three caffeine checks in this
    // package that ignored caffeineMg entirely and matched on ingredient name
    // alone, so a decaffeinated coffee-flavored product (name matches, but
    // caffeineMg is 0 or null) tripped this caution while correctly passing
    // the other two, and a genuinely caffeinated product without a
    // recognized ingredient-name pattern (e.g. "natural flavoring" instead
    // of "cola nut extract") passed this check while failing them. Only
    // falls back to the name heuristic when caffeineMg isn't declared at all.
    val containsCaffeineSource = product.nutrition.caffeineMg?.let { it >= 20.0 }
        ?: product.ingredients.any { ing -> CAFFEINE_SOURCE_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    if (containsCaffeineSource) {
        adjustments += PersonalAdjustment(
            points = -3.0,
            reason = if (lang == "en") "May contain caffeine — ANSES recommends pregnant women keep total daily caffeine intake under 200 mg"
                     else "Peut contenir de la caféine — l'ANSES recommande de limiter l'apport total en caféine à 200 mg/jour pendant la grossesse",
            category = AdjustmentCategory.CONDITION,
        )
    }
    // ANSES's listeriosis/toxoplasmosis/mercury/vitamin-A-teratogenicity
    // guidance (raw meat/fish, unpasteurized soft cheese, high-mercury fish,
    // liver, unheated deli meat, raw sprouts) was already cited in the hint
    // panel (HealthConditionGuidanceDb.kt's PREGNANCY_GUIDANCE) but never
    // affected the score - a raw-milk soft cheese or a smoked-salmon product
    // scored identically to a pasteurized/safe equivalent here, the same
    // hint-text-but-no-score-effect gap the caffeine check above already
    // closed for its own risk. Not a veto (unlike alcohol, which has no safe
    // threshold) since some of these are a matter of degree/preparation
    // (thorough reheating neutralizes the deli-meat risk) rather than an
    // absolute contraindication - a real, disclosed point deduction instead.
    val containsListeriaRisk = product.ingredients.any { ing -> PREGNANCY_LISTERIA_RISK_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    if (containsListeriaRisk) {
        adjustments += PersonalAdjustment(
            points = -4.0,
            reason = if (lang == "en") "May carry listeriosis/toxoplasmosis/mercury risk — ANSES recommends caution with raw/unpasteurized/high-mercury foods during pregnancy"
                     else "Peut présenter un risque de listériose/toxoplasmose/mercure — l'ANSES recommande la prudence avec les aliments crus, au lait cru ou riches en mercure pendant la grossesse",
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

/** Positive-reinforcement nutrient-source bonuses (never a caution/veto,
 *  since there's nothing to avoid) for the three conditions whose only safe,
 *  broadly-reaching effect is rewarding the right nutrient source, reusing
 *  the exact same NRV thresholds ProductHintsPairings.kt already uses so the
 *  score and the hint panel always agree:
 *  - anemia (WHO: ~2 billion affected worldwide) - iron source, >=2.1mg/100g
 *    (15% of 14mg per EU Reg 1169/2011 Annex XIII).
 *  - osteoporosis (IOF: ~200 million affected worldwide) - calcium source,
 *    >=120mg/100g (NOF/NIH bone-health guidance), plus vitamin D as the
 *    other cornerstone of the same guidance.
 *  - hair_loss - iron and zinc, both documented, correctable nutritional
 *    contributors (NIH Office of Dietary Supplements), same iron threshold
 *    as anemia plus zinc >=1.5mg/100g. */
internal fun checkNutrientSourceConditions(
    product: Product,
    conditions: Set<String>,
    lang: String,
    adjustments: MutableList<PersonalAdjustment>,
) {
    val n = product.nutrition
    val isIronSource = (n.ironMg ?: 0.0) >= 2.1
    if (("anemia" in conditions || "hair_loss" in conditions) && isIronSource) {
        adjustments += PersonalAdjustment(
            points = 2.0,
            reason = if (lang == "en") "Good iron source — helpful for iron-deficiency anemia (WHO)"
                     else "Bonne source de fer — utile en cas d'anémie ferriprive (OMS)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    if ("hair_loss" in conditions && (n.zincMg ?: 0.0) >= 1.5) {
        adjustments += PersonalAdjustment(
            points = 1.5,
            reason = if (lang == "en") "Good zinc source — zinc deficiency is a documented, correctable contributor to hair loss (NIH ODS)"
                     else "Bonne source de zinc — une carence en zinc est une cause nutritionnelle documentée et corrigible de la chute de cheveux (NIH ODS)",
            category = AdjustmentCategory.CONDITION,
        )
    }
    if ("osteoporosis" in conditions) {
        if ((n.calciumMg ?: 0.0) >= 120.0) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "Good calcium source — a cornerstone of bone health guidance for osteoporosis (NOF/NIH)"
                         else "Bonne source de calcium — un pilier des recommandations pour la santé osseuse en cas d'ostéoporose (NOF/NIH)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if ((n.vitDUg ?: 0.0) >= 0.5) {
            adjustments += PersonalAdjustment(
                points = 1.5,
                reason = if (lang == "en") "Good vitamin D source — essential for calcium absorption and bone health with osteoporosis (NIH)"
                         else "Bonne source de vitamine D — essentielle à l'absorption du calcium et à la santé osseuse en cas d'ostéoporose (NIH)",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
}
