package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ===== BMI =====
internal fun computeBmiAdjustments(
    product: Product,
    profile: Profile,
    catThresholds: CategoryThresholds,
    isSugarSweetenedBeverage: Boolean,
    lang: String,
): List<PersonalAdjustment> {
    val adjustments = mutableListOf<PersonalAdjustment>()
    val bmiValue = bmi(profile)
    val bmiCat   = bmiCategory(bmiValue)
    if (bmiCat == BmiCategory.OVERWEIGHT || bmiCat?.name?.startsWith("OBESE") == true) {
        if (isSugarSweetenedBeverage) {
            adjustments += PersonalAdjustment(
                points   = -5.0,
                reason   = if (lang == "en")
                    "Sugar-sweetened beverage — one of the most consistently established dietary risk factors for weight gain and obesity (Malik et al., Circulation 2010; Hu FB, Obes Rev 2013), amplified for BMI $bmiValue"
                else "Boisson sucrée — l'un des facteurs alimentaires les plus solidement établis de prise de poids et d'obésité (Malik et al., Circulation 2010 ; Hu FB, Obes Rev 2013), amplifié pour IMC $bmiValue",
                category = AdjustmentCategory.BMI,
            )
        // .first = the "moderate" tier of Triple(moderate, major, critical) - for
        // the DEFAULT category this is exactly 5.0, so behavior-identical to the
        // old flat cutoff for any uncategorized product, and only loosens for
        // categories (cheese, oil) whose own base-pillar threshold is already
        // higher - see catThresholds' own comment above for why a flat 5g bar
        // flagged literally every cheese regardless of whether it was unusually
        // fatty for a cheese.
        } else if (product.nutrition.saturatedFatG > catThresholds.satFatThresholds.first || product.nutrition.sugarsG > catThresholds.sugarThresholds.third) {
            adjustments += PersonalAdjustment(
                points   = -4.0,
                reason   = if (lang == "en")
                    "BMI $bmiValue (${bmiCat?.name?.lowercase()}) — high sat fat/sugar penalty amplified (WHO BMI 2000)"
                else "IMC $bmiValue (${bmiCat?.name?.lowercase()}) — pénalité accrue sur graisses saturées/sucres (OMS 2000)",
                category = AdjustmentCategory.BMI,
            )
        }
        // Refined-carbohydrate / energy-density signal — previously this whole
        // BMI section only ever looked at sat fat and sugar, so an ultra-
        // processed, low-fat, low-sugar refined-starch product (a boxed pasta/
        // rice meal: wheat pasta + a seasoning sachet, no meaningful fat or
        // sugar, but essentially fiber-free refined carbohydrate and often
        // markedly more calorie-dense than a typical ready meal) passed through
        // completely untouched for an overweight/obese profile. Refined
        // carbohydrate/glycemic load is an independently established obesity-
        // risk factor, not just fat/sugar (Mozaffarian et al., NEJM 2011;
        // McKeown et al., Framingham Offspring Study, Am J Clin Nutr 2004).
        val netCarbs = (product.nutrition.carbsG - product.nutrition.fiberG).coerceAtLeast(0.0)
        if (product.novaClass == NovaClass.ULTRA_PROCESSED && netCarbs >= 40.0 && product.nutrition.fiberG < 3.0) {
            adjustments += PersonalAdjustment(
                points   = -3.0,
                reason   = if (lang == "en")
                    "Ultra-processed refined carbohydrate (${netCarbs}g net carbs/100g, low fiber) — obesity-risk signal amplified (Mozaffarian et al., NEJM 2011)"
                else "Glucides raffinés ultra-transformés (${netCarbs} g de glucides nets/100 g, peu de fibres) — signal de risque amplifié (Mozaffarian et al., NEJM 2011)",
                category = AdjustmentCategory.BMI,
            )
        }
        // Same idea, orthogonal to macro composition: a product can be
        // calorie-dense for its own category through refined starch alone,
        // with no single macro crossing the sat-fat/sugar/net-carbs bars above.
        // 1.15x (looser than the base engine's own >1.25x "anomaly" bar in
        // NegativeNutrientsPillar) so this amplifies moderately-elevated
        // density specifically for an at-risk BMI, on top of - not only
        // duplicating - the neutral anomaly flag everyone already gets.
        val (_, kcalHigh) = catThresholds.expectedKcalRange
        if (product.nutrition.energyKcal > kcalHigh * 1.15) {
            adjustments += PersonalAdjustment(
                points   = -2.0,
                reason   = if (lang == "en")
                    "Energy-dense for its category (${product.nutrition.energyKcal.roundToInt()} kcal/100 g vs ${kcalHigh.roundToInt()} typical max) — amplified for BMI $bmiValue"
                else "Dense en énergie pour sa catégorie (${product.nutrition.energyKcal.roundToInt()} kcal/100 g vs ${kcalHigh.roundToInt()} maximum typique) — amplifié pour IMC $bmiValue",
                category = AdjustmentCategory.BMI,
            )
        }
    }
    if (bmiCat == BmiCategory.UNDERWEIGHT) {
        if (product.nutrition.energyKcal > 300 && product.nutrition.proteinG >= 8) {
            adjustments += PersonalAdjustment(
                points   = 2.0,
                reason   = if (lang == "en")
                    "BMI $bmiValue (underweight) — energy- and protein-dense product is supportive"
                else "IMC $bmiValue (insuffisance pondérale) — produit dense en énergie et protéines, bénéfique",
                category = AdjustmentCategory.BMI,
            )
        }
    }
    return adjustments
}
