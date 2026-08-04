package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Product

// Diabetes, hypertension and kidney_disease - the three "metabolic/cardio"
// health conditions checkHealthConditions handles via a flat nutrient
// threshold (sugar, salt/caffeine, protein respectively), each raised above
// its historical flat number to the category-relative bar computed once by
// the caller (see checkHealthConditions's own doc comment for the "mozzarella
// flagged, boxed pasta not" rationale behind that).
internal fun checkMetabolicConditions(
    product: Product,
    conditions: Set<String>,
    lang: String,
    catThresholds: CategoryThresholds,
    isSugarSweetenedBeverage: Boolean,
    adjustments: MutableList<PersonalAdjustment>,
) {
    if ("diabetes" in conditions) {
        val sugars = product.nutrition.sugarsG
        if (isSugarSweetenedBeverage) {
            adjustments += PersonalAdjustment(
                points = -5.0,
                reason = if (lang == "en") "Sugar-sweetened beverage — liquid sugar causes a faster glycemic spike than the same amount in solid food, a particular concern for diabetes (WHO Sugars Intake Guideline 2015)"
                         else "Boisson sucrée — le sucre liquide provoque un pic glycémique plus rapide que la même quantité dans un aliment solide, une préoccupation particulière en cas de diabète (recommandation OMS sur les sucres, 2015)",
                category = AdjustmentCategory.CONDITION,
            )
        // .third/.first = the "major"/"minor" tiers of Quadruple(minor, moderate,
        // major, critical) - for the DEFAULT category these are exactly 15.0/5.0,
        // so this is behavior-identical to the old flat cutoff for any product
        // whose category has no override, and only loosens for categories (e.g.
        // condiments) whose own base-pillar thresholds are already higher.
        } else if (sugars >= catThresholds.sugarThresholds.third) {
            adjustments += PersonalAdjustment(
                points = -4.0,
                reason = if (lang == "en") "High sugar (${sugars} g/100 g) — caution advised for diabetes"
                         else "Sucres élevés (${sugars} g/100 g) — prudence recommandée en cas de diabète",
                category = AdjustmentCategory.CONDITION,
            )
        } else if (sugars <= catThresholds.sugarThresholds.first) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "Low sugar — diabetes-friendly"
                         else "Faible en sucres — adapté au diabète",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
    if ("hypertension" in conditions) {
        val salt = product.nutrition.saltG
        // maxOf against the original flat 1.2g - never lowers the bar for any
        // category (so a random product still gets flagged exactly as before),
        // only raises it for CONDIMENT/PROCESSED_MEAT, whose own category norm
        // (CategoryThresholds.kt's saltThresholds) is structurally higher -
        // same "mozzarella flagged for sat fat, soda wasn't" category-blindness
        // this fixes, just for salt/hypertension instead of sat-fat/BMI. Without
        // this, every soy sauce and every cured ham triggered this exact same
        // caution regardless of being entirely typical for its category.
        val hypertensionSaltBar = maxOf(1.2, catThresholds.saltThresholds.first)
        if (salt >= hypertensionSaltBar) {
            adjustments += PersonalAdjustment(
                points = -4.0,
                reason = if (lang == "en") "High salt (${salt} g/100 g) — caution advised for hypertension"
                         else "Sel élevé (${salt} g/100 g) — prudence recommandée en cas d'hypertension",
                category = AdjustmentCategory.CONDITION,
            )
        } else if (salt <= 0.3) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "Low salt — hypertension-friendly"
                         else "Faible en sel — adapté à l'hypertension",
                category = AdjustmentCategory.CONDITION,
            )
        }
        // Previously no caffeine check existed for hypertension at all — a
        // caffeinated zero-sugar soda/energy drink (e.g. Monster Zero, Coca-Cola
        // Zero) passed every hypertension rule cleanly and read as fully "safe"
        // purely because caffeine was never modeled as data, not because it
        // genuinely poses no risk. Caffeine causes an acute BP rise (Mesas et al.,
        // meta-analysis, Am J Clin Nutr 2011); 20 mg/100g/mL is roughly typical
        // cola/energy-drink level and well below what a null (undeclared) value
        // would ever wrongly match, so this only fires on a real declared amount.
        product.nutrition.caffeineMg?.let { caffeine ->
            if (caffeine >= 20.0) {
                adjustments += PersonalAdjustment(
                    points = -2.0,
                    reason = if (lang == "en") "Contains caffeine (${caffeine.toInt()} mg/100 g) — can raise blood pressure acutely, caution advised for hypertension"
                             else "Contient de la caféine (${caffeine.toInt()} mg/100 g) — peut élever la tension artérielle de façon aiguë, prudence recommandée en cas d'hypertension",
                    category = AdjustmentCategory.CONDITION,
                )
            }
        }
    }
    // maxOf against the original flat 15.0g - never lowers the bar, only raises
    // it for FRESH_MEAT/FISH/CHEESE/PROCESSED_MEAT etc. whose own category norm
    // (CategoryThresholds.kt's proteinG "high" tier) is structurally higher.
    // Without this, every single fresh chicken breast or cod fillet (completely
    // ordinary protein for meat/fish, ~18-23g/100g) triggered the identical
    // kidney-disease caution as a genuine outlier like a protein bar spiked
    // to 3-4x its own category's norm - the warning couldn't distinguish
    // "ordinary meat" from "unusually protein-dense for what it is," exactly
    // the same category-blindness class as the mozzarella/sat-fat case.
    val kidneyProteinBar = maxOf(15.0, catThresholds.proteinG.third)
    if ("kidney_disease" in conditions && product.nutrition.proteinG >= kidneyProteinBar) {
        adjustments += PersonalAdjustment(
            points = -3.0,
            reason = if (lang == "en") "High protein (${product.nutrition.proteinG} g/100 g) — caution advised for kidney disease"
                     else "Protéines élevées (${product.nutrition.proteinG} g/100 g) — prudence recommandée en cas de maladie rénale",
            category = AdjustmentCategory.CONDITION,
        )
    }
}
