package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ===== AGE-BASED =====
internal fun computeAgeAdjustments(
    product: Product,
    profile: Profile,
    catThresholds: CategoryThresholds,
    isSugarSweetenedBeverage: Boolean,
    lang: String,
): List<PersonalAdjustment> {
    val adjustments = mutableListOf<PersonalAdjustment>()
    val age = profile.ageYears
    if (age != null && age > 0) {
        if (age >= 65 && product.nutrition.proteinG >= 12) {
            adjustments += PersonalAdjustment(
                points   = 3.0,
                reason   = if (lang == "en")
                    "High protein (${product.nutrition.proteinG} g/100 g) — helps prevent sarcopenia (PROT-AGE 1.0 g/kg/day for ≥65)"
                else "Protéines élevées (${product.nutrition.proteinG} g/100 g) — prévention de la sarcopénie (PROT-AGE 1,0 g/kg/j après 65 ans)",
                category = AdjustmentCategory.AGE,
            )
        }
        // maxOf against the original flat 1.5g - same category-relative fix as
        // the hypertension check above (CONDIMENT/PROCESSED_MEAT raised, every
        // other category unchanged), applied consistently to this sibling
        // salt check rather than leaving it as the one flat outlier.
        val ageSaltBar = maxOf(1.5, catThresholds.saltThresholds.third)
        if (age >= 50 && product.nutrition.saltG > ageSaltBar) {
            adjustments += PersonalAdjustment(
                points   = -3.0,
                reason   = if (lang == "en") "Salt penalty amplified after 50y (higher hypertension risk, WHO 2012)"
                           else "Pénalité sel amplifiée après 50 ans (risque d'hypertension accru, OMS 2012)",
                category = AdjustmentCategory.AGE,
            )
        }
        if (age < 18) {
            // WHO's stricter sugar guidance for children is largely *about*
            // sugar-sweetened beverages specifically (dental caries, childhood
            // obesity) - the SSB bar applies on top of the category-relative one,
            // same reasoning as the diabetes/depression checks above.
            if (isSugarSweetenedBeverage || product.nutrition.sugarsG > catThresholds.sugarThresholds.third) {
                adjustments += PersonalAdjustment(
                    points   = -4.0,
                    reason   = if (lang == "en") "Sugar penalty amplified for under-18 (WHO stricter in children)"
                               else "Pénalité sucres amplifiée chez les moins de 18 ans (recommandations OMS plus strictes)",
                    category = AdjustmentCategory.AGE,
                )
            }
            val hasAzoColorant = product.ingredients.any { ing ->
                Regex("\\bE(102|104|110|122|124|129)\\b", RegexOption.IGNORE_CASE).containsMatchIn(ing.name) ||
                ing.eNumber?.matches(Regex("E(102|104|110|122|124|129)", RegexOption.IGNORE_CASE)) == true
            }
            if (hasAzoColorant) {
                adjustments += PersonalAdjustment(
                    points   = -3.0,
                    reason   = if (lang == "en")
                        "Azo colorant under EU hyperactivity-warning label (Reg. 1333/2008) in a product for a child"
                    else "Colorant azoïque avec avertissement UE hyperactivité (Règl. 1333/2008) — consommateur mineur",
                    category = AdjustmentCategory.AGE,
                )
            }
        }
    }
    return adjustments
}

// ===== SEX =====
internal fun computeSexAdjustments(product: Product, profile: Profile, lang: String): List<PersonalAdjustment> {
    val adjustments = mutableListOf<PersonalAdjustment>()
    // Gated on the profile's own isMenstruating answer, not an age-range
    // guess — see dailyTargets()'s ironTarget for why the guess was wrong.
    if (profile.sex == Sex.FEMALE && profile.isMenstruating) {
        // declaredMicronutrients only ever contains OffMapper's fixed English
        // tokens (see declaredMicronutrientsOf()) - never a French "fer" label.
        val declaresIron = product.declaredMicronutrients.any { it.contains("iron", ignoreCase = true) }
        if (declaresIron) {
            adjustments += PersonalAdjustment(
                points   = 2.0,
                reason   = if (lang == "en")
                    "Iron-declared product — menstruating women have higher RNI (EFSA 2015: 16 mg/day)"
                else "Fer déclaré — les femmes en âge menstruel ont un besoin plus élevé (EFSA 2015 : 16 mg/j)",
                category = AdjustmentCategory.SEX,
            )
        }
    }
    return adjustments
}

// ===== ACTIVITY =====
internal fun computeActivityAdjustments(product: Product, profile: Profile, lang: String): List<PersonalAdjustment> {
    val adjustments = mutableListOf<PersonalAdjustment>()
    val activity = profile.activityLevel
    if (activity == ActivityLevel.VERY_ACTIVE || activity == ActivityLevel.EXTRA_ACTIVE) {
        if (product.nutrition.proteinG >= 15) {
            adjustments += PersonalAdjustment(
                points   = 2.0,
                reason   = if (lang == "en")
                    "High-protein product — supports athlete recovery (IOC 2018: 1.2–2.0 g/kg/day)"
                else "Protéines élevées — adaptées à la récupération sportive (CIO 2018 : 1,2–2,0 g/kg/j)",
                category = AdjustmentCategory.ACTIVITY,
            )
        }
        val sugars = product.nutrition.sugarsG
        if (sugars > 5 && sugars <= 15) {
            adjustments += PersonalAdjustment(
                points   = 2.0,
                reason   = if (lang == "en") "Moderate-sugar product — active lifestyle uses carbs"
                           else "Sucres modérés — ton activité justifie un apport glucidique",
                category = AdjustmentCategory.ACTIVITY,
            )
        }
    }
    if (activity == ActivityLevel.LIGHTLY_ACTIVE || activity == ActivityLevel.MODERATELY_ACTIVE) {
        if (product.nutrition.proteinG >= 15) {
            adjustments += PersonalAdjustment(
                points   = 1.0,
                reason   = if (lang == "en")
                    "High protein — useful for moderate-activity adults (EFSA PRI 0.83 g/kg/day)"
                else "Protéines élevées — utiles pour un niveau d'activité modéré (PRI EFSA 0,83 g/kg/j)",
                category = AdjustmentCategory.ACTIVITY,
            )
        }
    }
    if (activity == ActivityLevel.SEDENTARY) {
        if (product.nutrition.sugarsG > 10) {
            adjustments += PersonalAdjustment(
                points   = -3.0,
                reason   = if (lang == "en")
                    "Sedentary lifestyle — sugar penalty amplified (higher insulin-resistance risk)"
                else "Mode de vie sédentaire — pénalité sucres amplifiée (risque accru de résistance insulinique)",
                category = AdjustmentCategory.ACTIVITY,
            )
        }
    }
    return adjustments
}

// ===== GOAL =====
// profile.goal (lose/maintain/gain) was captured at onboarding and stored,
// but never read anywhere in scoring — a user who set a weight goal saw
// zero effect from it on the products they scan.
internal fun computeGoalAdjustments(
    product: Product,
    profile: Profile,
    catThresholds: CategoryThresholds,
    lang: String,
): List<PersonalAdjustment> {
    val adjustments = mutableListOf<PersonalAdjustment>()
    when (profile.goal) {
        Goal.LOSE -> {
            // maxOf against the original flat 10g - same category-relative fix
            // as the BMI section's own sat-fat check (which already uses
            // catThresholds.satFatThresholds), applied here too so a weight-
            // loss-goal user scoring a completely typical cheese doesn't get
            // this flagged on top of that already-category-aware BMI penalty.
            val goalSatFatBar = maxOf(10.0, catThresholds.satFatThresholds.second)
            if (product.nutrition.energyKcal >= 400 && product.nutrition.saturatedFatG > goalSatFatBar) {
                adjustments += PersonalAdjustment(
                    points   = -2.0,
                    reason   = if (lang == "en")
                        "Energy-dense (${product.nutrition.energyKcal.roundToInt()} kcal/100 g) — your goal is weight loss"
                    else "Dense en énergie (${product.nutrition.energyKcal.roundToInt()} kcal/100 g) — ton objectif est la perte de poids",
                    category = AdjustmentCategory.GOAL,
                )
            }
        }
        Goal.GAIN -> {
            if (product.nutrition.energyKcal >= 300 && product.nutrition.proteinG >= 15) {
                adjustments += PersonalAdjustment(
                    points   = 2.0,
                    reason   = if (lang == "en")
                        "Calorie- and protein-dense — supports your weight-gain goal"
                    else "Dense en calories et en protéines — soutient ton objectif de prise de poids",
                    category = AdjustmentCategory.GOAL,
                )
            }
        }
        Goal.MAINTAIN -> {}
    }
    return adjustments
}
