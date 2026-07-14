package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ============================================================================
// PERSONAL SCORE ENGINE — port of public/core/personal-score.js
//                       + public/data/profile.js helper functions
//
// AUTHORITATIVE anchors (preserved from original):
//   Protein PRI: EFSA Scientific Opinion 2012;10(2):2557
//     (0.83 g/kg adults; ≥65y 1.0 g/kg for sarcopenia prevention)
//   Iron RNI: EFSA Scientific Opinion 2015;13(10):4254
//     (men 11 mg/day; menstruating women 16 mg/day)
//   Sodium / BMI: WHO Global Database on BMI (2000); WHO Salt Guideline (2012)
//   Sat-fat / free-sugar daily budgets: WHO SFA Guideline 2023; WHO Sugars 2015
//   Athlete protein/carb: IOC Consensus on Sports Nutrition (Br J Sports Med 2018)
//   Mifflin-St Jeor BMR: Mifflin et al., JADA 1990;90(3):402
//   PAL multipliers: FAO/WHO/UNU 2004 Table 5.1
//   Daily micronutrient targets: EFSA DRV Summary 2017
//
// EDITORIAL: adjustment point magnitudes (±2 / ±3 / ±4 / ±5) and the hard
// veto-to-0 behaviour are Scan'eat design choices, not validated calibrations.
// ============================================================================

// ============================================================================
// Profile helper functions (port of profile.js)
// ============================================================================

private val ACTIVITY_PAL = mapOf(
    ActivityLevel.SEDENTARY        to 1.40,
    ActivityLevel.LIGHTLY_ACTIVE   to 1.55,
    ActivityLevel.MODERATELY_ACTIVE to 1.75,
    ActivityLevel.VERY_ACTIVE      to 1.90,
    ActivityLevel.EXTRA_ACTIVE     to 2.20,
)

fun hasMinimalProfile(p: Profile): Boolean =
    p.sex != Sex.NOT_SPECIFIED &&
    (p.ageYears ?: 0) > 0 &&
    (p.heightCm ?: 0.0) > 0.0 &&
    (p.weightKg ?: 0.0) > 0.0

/** Mifflin-St Jeor BMR in kcal/day. */
fun bmrMifflinStJeor(p: Profile): Double? {
    if (!hasMinimalProfile(p)) return null
    val sexOffset = if (p.sex == Sex.FEMALE) -161.0 else 5.0
    return 10.0 * p.weightKg!! + 6.25 * p.heightCm!! - 5.0 * p.ageYears!! + sexOffset
}

fun tdeeKcal(p: Profile): Double? {
    val bmr = bmrMifflinStJeor(p) ?: return null
    val pal = ACTIVITY_PAL[p.activityLevel] ?: return null
    return (bmr * pal)
}

/** BMI = kg / m². Returns null if height/weight missing. */
fun bmi(p: Profile): Double? {
    val h = p.heightCm ?: return null
    val w = p.weightKg ?: return null
    val m = h / 100.0
    return (w / (m * m)).roundTo1Decimal()
}

enum class BmiCategory { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE_1, OBESE_2, OBESE_3 }

fun bmiCategory(value: Double?): BmiCategory? = when {
    value == null   -> null
    value < 18.5    -> BmiCategory.UNDERWEIGHT
    value < 25.0    -> BmiCategory.NORMAL
    value < 30.0    -> BmiCategory.OVERWEIGHT
    value < 35.0    -> BmiCategory.OBESE_1
    value < 40.0    -> BmiCategory.OBESE_2
    else            -> BmiCategory.OBESE_3
}

/** EFSA PRI: 0.83 g/kg adults; 1.0 g/kg ≥65y. */
fun proteinPriG(p: Profile): Double? {
    val w = p.weightKg ?: return null
    val age = p.ageYears ?: return null
    val perKg = if (age >= 65) 1.0 else 0.83
    return (w * perKg).roundToInt().toDouble()
}

data class DailyTargets(
    // Energy + macros
    val kcal: Double,
    val satFatGMax: Double,
    val freeSugarsGMax: Double,
    val freeSugarsGIdeal: Double,
    val saltGMax: Double = 5.0,
    val proteinGTarget: Double,
    // 30% of kcal from fat (WHO/EFSA acceptable macronutrient range 20-35%,
    // midpoint) - unlike satFatGMax (saturated fat only), this was previously
    // entirely absent, so the Journal's fat total had no target to show
    // (unlike calories/protein/carbs, which all had one).
    val fatGTarget: Double,
    val fiberGTarget: Double = 25.0,        // EFSA DRV 2017
    // Micros — EFSA Population Reference Intakes (adults, sex-averaged where split)
    val ironMgTarget: Double = 11.0,        // EFSA 2015: 11 mg men / 16 mg menstruating women
    val calciumMgTarget: Double = 950.0,    // EFSA DRV 2015
    val vitDUgTarget: Double = 15.0,        // EFSA DRV 2016
    val b12UgTarget: Double = 4.0,          // EFSA DRV 2015
    val magnesiumMgTarget: Double = 350.0,  // EFSA DRV 2015 (adult men); 300 women
    val potassiumMgTarget: Double = 3500.0, // EFSA DRV 2016 AI
    val zincMgTarget: Double = 9.4,         // EFSA DRV 2014 (adult men)
    val vitCMgTarget: Double = 95.0,        // EFSA DRV 2013
    // Diet-driven target overrides - null unless the selected diet actually
    // implies a daily total (most diets are ingredient-exclusion only, not
    // macro-budget diets, so this stays null for them).
    val carbsGDailyMax: Double? = null,
    // General carb target shown in the Journal/Dashboard for every diet (unlike
    // carbsGDailyMax, which is only a hard cap for macro-budget diets like keto).
    // Previously the Journal only ever showed a carbs target for keto users -
    // everyone else saw a bare total with no "/target" the way calories,
    // protein and fat all had, even though the same AMDR-derived number was
    // trivially available as "whatever's left of kcal after protein+fat".
    val carbsGTarget: Double,
)

/**
 * Standard ~0.45 kg/week (1 lb/week) deficit/surplus - a 3500 kcal/lb energy
 * equivalence spread over 7 days. Applied on top of TDEE so the kcal target
 * actually reflects the Goal the user picked in Profile, instead of always
 * showing maintenance calories regardless of Lose/Gain.
 */
private const val GOAL_KCAL_ADJUSTMENT = 500.0

/**
 * [weightKgOverride] recomputes every weight-derived figure (BMR, protein
 * PRI target) as if the profile's body weight were this value instead of
 * [Profile.weightKg] - used to show what the day's macro targets would look
 * like at the user's stated goal weight, alongside the targets for their
 * current weight. Diet/health-condition/sex/age fields are untouched.
 */
fun dailyTargets(p: Profile, weightKgOverride: Double? = null): DailyTargets? {
    val effectiveP = weightKgOverride?.let { p.copy(weightKg = it) } ?: p
    val tdee = tdeeKcal(effectiveP) ?: return null
    val goalAdjustedKcal = when (p.goal) {
        Goal.LOSE     -> tdee - GOAL_KCAL_ADJUSTMENT
        Goal.GAIN     -> tdee + GOAL_KCAL_ADJUSTMENT
        Goal.MAINTAIN -> tdee
    }
    val pri  = proteinPriG(effectiveP) ?: 0.0
    // Sex-specific iron: menstruating women 16 mg/day (EFSA 2015). Uses the
    // profile's own isMenstruating answer rather than inferring from age —
    // the app already asks this explicitly (ProfileScreen shows the checkbox
    // for any female profile), so a woman in the 13-50 range who says she
    // isn't currently menstruating (menopause, pregnancy, hormonal
    // contraception, amenorrhea) got the wrong 16 mg target from the age
    // heuristic alone, and the checkbox answer had no effect anywhere.
    val ironTarget = if (p.sex == Sex.FEMALE && p.isMenstruating) 16.0 else 11.0
    // Sex-specific zinc: women 7.5 mg/day (EFSA 2014)
    val zincTarget = if (p.sex == Sex.FEMALE) 7.5 else 9.4
    // Older adults: higher vitD target 20 µg/day (EFSA 2016 ≥75y)
    val vitDTarget = if ((p.ageYears ?: 0) >= 75) 20.0 else 15.0

    // WHO guidance caps free sugars/salt harder for these conditions than the
    // general-population default - halved rather than a made-up clinical value,
    // since an exact per-condition target needs a dietitian, not this app.
    val sugarsCapFraction = if ("diabetes" in p.healthConditions) 0.05 else 0.10
    val saltCap = if ("hypertension" in p.healthConditions) 3.0 else 5.0

    // Diet-driven overrides - Volek & Phinney clinical ketosis range is 20-50g net
    // carbs/day; carnivore structurally has no plant fiber intake to target.
    val carbsMax = if (p.diet == DietKey.KETO) 30.0 else null
    val fiberTarget = if (p.diet == DietKey.CARNIVORE) 0.0 else 25.0
    val fatTarget = (0.30 * goalAdjustedKcal / 9.0)
    // Remaining calories after protein+fat, converted at 4 kcal/g - the
    // standard "carbs fill the rest of the budget" AMDR approach, so every
    // diet gets a real number here instead of only keto's hard cap.
    val generalCarbsTarget = ((goalAdjustedKcal - pri * 4.0 - fatTarget * 9.0) / 4.0).coerceAtLeast(0.0)

    return DailyTargets(
        kcal              = goalAdjustedKcal,
        satFatGMax        = (0.10 * goalAdjustedKcal / 9.0),
        freeSugarsGMax    = (sugarsCapFraction * goalAdjustedKcal / 4.0),
        freeSugarsGIdeal  = (0.05 * goalAdjustedKcal / 4.0),
        saltGMax          = saltCap,
        proteinGTarget    = pri,
        fatGTarget        = fatTarget,
        fiberGTarget      = fiberTarget,
        carbsGDailyMax    = carbsMax,
        carbsGTarget      = carbsMax ?: generalCarbsTarget,
        ironMgTarget      = ironTarget,
        calciumMgTarget   = 950.0,
        vitDUgTarget      = vitDTarget,
        b12UgTarget       = 4.0,
        magnesiumMgTarget = if (p.sex == Sex.FEMALE) 300.0 else 350.0,
        potassiumMgTarget = 3500.0,
        zincMgTarget      = zincTarget,
        vitCMgTarget      = 95.0,
    )
}

/**
 * Rescales every kcal-derived field in [targets] onto [rawKcal] (a raw TDEE/
 * maintenance estimate, e.g. Biolism's body-composition-aware
 * computeMetabolics().tdeeDay) - satFat/sugars/fat/carbs are all a fixed
 * fraction of the day's calorie budget, so swapping in a richer TDEE without
 * also rescaling them left macros that no longer summed to the kcal figure
 * shown next to them. proteinGTarget (per-kg body weight, not kcal) and the
 * diet-driven carbsGDailyMax hard cap (a fixed clinical ceiling, not a kcal
 * fraction) are deliberately left untouched.
 *
 * [goal] re-applies the same ±500 kcal Lose/Gain adjustment dailyTargets()
 * applies to its own TDEE estimate - [rawKcal] is a plain maintenance
 * estimate with no notion of the user's goal, so passing it straight into
 * `kcal` here previously discarded that adjustment entirely: a Lose-goal
 * user with a valid Biolism profile (auto-populated from Profile as soon as
 * sex/age/height/weight exist, no Biolism screen visit required) was shown
 * maintenance calories as their "target", not the deficit dailyTargets()
 * itself would have computed.
 */
fun DailyTargets.withKcalOverride(rawKcal: Double, goal: Goal): DailyTargets {
    if (rawKcal <= 0.0 || kcal <= 0.0) return this
    val newKcal = when (goal) {
        Goal.LOSE     -> rawKcal - GOAL_KCAL_ADJUSTMENT
        Goal.GAIN     -> rawKcal + GOAL_KCAL_ADJUSTMENT
        Goal.MAINTAIN -> rawKcal
    }
    val ratio = newKcal / kcal
    val newFatTarget = fatGTarget * ratio
    val newCarbsTarget = if (carbsGDailyMax != null) carbsGTarget
        else ((newKcal - proteinGTarget * 4.0 - newFatTarget * 9.0) / 4.0).coerceAtLeast(0.0)
    return copy(
        kcal             = newKcal,
        satFatGMax       = satFatGMax * ratio,
        freeSugarsGMax   = freeSugarsGMax * ratio,
        freeSugarsGIdeal = freeSugarsGIdeal * ratio,
        fatGTarget       = newFatTarget,
        carbsGTarget     = newCarbsTarget,
    )
}

// ============================================================================
// Personal score output
// ============================================================================

data class PersonalAdjustment(
    val points: Double,
    val reason: String,
    val category: AdjustmentCategory,
    val veto: Boolean = false,
)

enum class AdjustmentCategory { DIET, AGE, SEX, ACTIVITY, BMI, GOAL, MODIFIER, CONDITION }

data class PersonalScoreResult(
    val personalScore: Int,
    val delta: Int,
    val adjustments: List<PersonalAdjustment>,
    val applicable: Boolean,
    val dietReason: String?,
    val veto: Boolean,
    /** Allergens found in this product that match the user's declared allergens. */
    val allergenHits: List<AllergenHit> = emptyList(),
)

// ============================================================================
// Main function
// ============================================================================

/**
 * Compute the personal score overlay on top of the classic ScoreAudit.
 * Port of computePersonalScore() from personal-score.js.
 *
 * @param audit   Output of scoreProduct()
 * @param product The same product that was scored
 * @param profile User profile (diet, allergens, sex, age, weight, activity)
 * @param lang    "fr" or "en" for reason strings
 */
fun computePersonalScore(
    audit: ScoreAudit,
    product: Product,
    profile: Profile,
    lang: String = "fr",
): PersonalScoreResult {

    val applicable = (profile.diet != DietKey.NONE) ||
        hasMinimalProfile(profile) ||
        profile.allergens.isNotEmpty() ||
        profile.healthConditions.isNotEmpty()

    if (!applicable) {
        return PersonalScoreResult(
            personalScore = audit.score,
            delta         = 0,
            adjustments   = emptyList(),
            applicable    = false,
            dietReason    = null,
            veto          = false,
        )
    }

    val adjustments = mutableListOf<PersonalAdjustment>()
    var dietReason: String? = null
    var veto = false

    // ===== DIET COMPLIANCE (HARD) =====
    if (profile.diet != DietKey.NONE) {
        val r = checkDiet(product, profile.diet, lang)
        if (!r.compliant) {
            veto = true
            dietReason = r.reason
            adjustments += PersonalAdjustment(0.0, r.reason ?: "", AdjustmentCategory.DIET, veto = true)
        } else if (r.certified) {
            val label = if (lang == "en") profile.diet.labelEn else profile.diet.labelFr
            adjustments += PersonalAdjustment(
                points   = 5.0,
                reason   = if (lang == "en") "$label certification detected: ${r.preferredHits.take(2).joinToString()}"
                           else "Certification $label détectée : ${r.preferredHits.take(2).joinToString()}",
                category = AdjustmentCategory.DIET,
            )
        } else if (r.preferredHits.isNotEmpty()) {
            val label = if (lang == "en") profile.diet.labelEn else profile.diet.labelFr
            adjustments += PersonalAdjustment(
                points   = 3.0,
                reason   = if (lang == "en") "$label-friendly ingredients: ${r.preferredHits.take(2).joinToString()}"
                           else "Conforme $label : ${r.preferredHits.take(2).joinToString()}",
                category = AdjustmentCategory.DIET,
            )
        }
    }

    // ===== ALLERGEN CHECK =====
    val allergenHits = if (profile.allergens.isNotEmpty())
        checkUserAllergens(product, profile.allergens, lang)
    else emptyList()

    // ===== HEALTH CONDITIONS =====
    // Only the conditions with an established, simple nutrition-level rule get a
    // scoring effect here (WHO sugar/salt guidance, kidney protein load, pregnancy
    // alcohol veto, WCRF/NHS alcohol caution for cancer/depression). Thyroid
    // disorders / food_allergies / intolerances / digestive disorders are still
    // selectable (surfaced elsewhere, e.g. the hint system) but have no dedicated
    // nutrition-threshold rule reliable enough to code here yet.
    val conditions = profile.healthConditions
    if ("diabetes" in conditions) {
        val sugars = product.nutrition.sugarsG
        if (sugars >= 15.0) {
            adjustments += PersonalAdjustment(
                points = -4.0,
                reason = if (lang == "en") "High sugar (${sugars} g/100 g) — caution advised for diabetes"
                         else "Sucres élevés (${sugars} g/100 g) — prudence recommandée en cas de diabète",
                category = AdjustmentCategory.CONDITION,
            )
        } else if (sugars <= 5.0) {
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
        if (salt >= 1.2) {
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
    }
    if ("kidney_disease" in conditions && product.nutrition.proteinG >= 15.0) {
        adjustments += PersonalAdjustment(
            points = -3.0,
            reason = if (lang == "en") "High protein (${product.nutrition.proteinG} g/100 g) — caution advised for kidney disease"
                     else "Protéines élevées (${product.nutrition.proteinG} g/100 g) — prudence recommandée en cas de maladie rénale",
            category = AdjustmentCategory.CONDITION,
        )
    }
    val alcoholHit = product.ingredients.any { ing ->
        val n = ing.name.lowercase()
        "alcool" in n || "alcohol" in n || "vin " in n || "wine" in n || "bière" in n || "beer" in n
    }
    if ("pregnancy" in conditions) {
        if (alcoholHit) {
            veto = true
            val reason = if (lang == "en") "Contains alcohol — avoid during pregnancy"
                         else "Contient de l'alcool — à éviter pendant la grossesse"
            dietReason = dietReason ?: reason
            adjustments += PersonalAdjustment(0.0, reason, AdjustmentCategory.CONDITION, veto = true)
        }
    }
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
    // food/beverage sugar intake prospectively associated with incident common
    // mental disorder and depression in men over ~5 years follow-up.
    if ("depression" in conditions && product.nutrition.sugarsG >= 15.0) {
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

    // ===== AGE-BASED =====
    val age = profile.ageYears
    if (age != null && age > 0) {
        if (age >= 65 && product.nutrition.proteinG >= 12) {
            adjustments += PersonalAdjustment(
                points   = 3.0,
                reason   = if (lang == "en")
                    "High protein (${product.nutrition.proteinG} g/100 g) — helps prevent sarcopenia (EFSA PRI 1.0 g/kg/day for ≥65)"
                else "Protéines élevées (${product.nutrition.proteinG} g/100 g) — prévention de la sarcopénie (PRI EFSA 1,0 g/kg/j après 65 ans)",
                category = AdjustmentCategory.AGE,
            )
        }
        if (age >= 50 && product.nutrition.saltG > 1.5) {
            adjustments += PersonalAdjustment(
                points   = -3.0,
                reason   = if (lang == "en") "Salt penalty amplified after 50y (higher hypertension risk, WHO 2012)"
                           else "Pénalité sel amplifiée après 50 ans (risque d'hypertension accru, OMS 2012)",
                category = AdjustmentCategory.AGE,
            )
        }
        if (age < 18) {
            if (product.nutrition.sugarsG > 15) {
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

    // ===== SEX =====
    // Gated on the profile's own isMenstruating answer, not an age-range
    // guess — see dailyTargets()'s ironTarget for why the guess was wrong.
    if (profile.sex == Sex.FEMALE && profile.isMenstruating) {
        val declaresIron = product.declaredMicronutrients.any { it.contains("iron", ignoreCase = true) || it.contains("fer", ignoreCase = true) }
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

    // ===== ACTIVITY =====
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

    // ===== GOAL =====
    // profile.goal (lose/maintain/gain) was captured at onboarding and stored,
    // but never read anywhere in scoring — a user who set a weight goal saw
    // zero effect from it on the products they scan.
    when (profile.goal) {
        Goal.LOSE -> {
            if (product.nutrition.energyKcal >= 400 && product.nutrition.saturatedFatG > 10) {
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

    // ===== PROTEIN PRI =====
    val priTarget = proteinPriG(profile)
    if (priTarget != null && priTarget > 0) {
        val pctOfPRI = (product.nutrition.proteinG / priTarget) * 100.0
        if (pctOfPRI >= 20) {
            adjustments += PersonalAdjustment(
                points   = 2.0,
                reason   = if (lang == "en")
                    "100 g covers ${pctOfPRI.roundToInt()} % of your daily protein target (${priTarget.roundToInt()} g, EFSA PRI)"
                else "100 g couvre ${pctOfPRI.roundToInt()} % de ton besoin protéique journalier (${priTarget.roundToInt()} g, PRI EFSA)",
                category = AdjustmentCategory.BMI,
            )
        }
    }

    // ===== DAILY TARGET CONTEXT =====
    val targets = dailyTargets(profile)
    if (targets != null) {
        val satFatPct = (product.nutrition.saturatedFatG / targets.satFatGMax.coerceAtLeast(1.0)) * 100.0
        if (satFatPct >= 50) {
            adjustments += PersonalAdjustment(
                points   = -4.0,
                reason   = if (lang == "en")
                    "100 g uses ${satFatPct.roundToInt()} % of your daily sat-fat budget (${targets.satFatGMax.roundToInt()} g, WHO 2023)"
                else "100 g consomme ${satFatPct.roundToInt()} % de ton budget AGS journalier (${targets.satFatGMax.roundToInt()} g, OMS 2023)",
                category = AdjustmentCategory.BMI,
            )
        }
        val sugars    = product.nutrition.addedSugarsG ?: product.nutrition.sugarsG
        val sugarPct  = (sugars / targets.freeSugarsGMax.coerceAtLeast(1.0)) * 100.0
        if (sugarPct >= 50) {
            adjustments += PersonalAdjustment(
                points   = -4.0,
                reason   = if (lang == "en")
                    "100 g uses ${sugarPct.roundToInt()} % of your daily free-sugar budget (${targets.freeSugarsGMax.roundToInt()} g, WHO 2015)"
                else "100 g consomme ${sugarPct.roundToInt()} % de ton budget sucres libres (${targets.freeSugarsGMax.roundToInt()} g, OMS 2015)",
                category = AdjustmentCategory.BMI,
            )
        }
        val saltPct = (product.nutrition.saltG / targets.saltGMax) * 100.0
        if (saltPct >= 30) {
            adjustments += PersonalAdjustment(
                points   = -3.0,
                reason   = if (lang == "en")
                    "100 g uses ${saltPct.roundToInt()} % of the WHO 5 g/day salt ceiling"
                else "100 g consomme ${saltPct.roundToInt()} % du plafond OMS de 5 g/j de sel",
                category = AdjustmentCategory.BMI,
            )
        }
    }

    // ===== BMI =====
    val bmiValue = bmi(profile)
    val bmiCat   = bmiCategory(bmiValue)
    if (bmiCat == BmiCategory.OVERWEIGHT || bmiCat?.name?.startsWith("OBESE") == true) {
        if (product.nutrition.saturatedFatG > 5 || product.nutrition.sugarsG > 15) {
            adjustments += PersonalAdjustment(
                points   = -4.0,
                reason   = if (lang == "en")
                    "BMI $bmiValue (${bmiCat?.name?.lowercase()}) — high sat fat/sugar penalty amplified (WHO BMI 2000)"
                else "IMC $bmiValue (${bmiCat?.name?.lowercase()}) — pénalité accrue sur graisses saturées/sucres (OMS 2000)",
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

    val delta = adjustments.sumOf { it.points }

    var personalScore = if (veto) 0.0
        else (audit.score + delta).coerceIn(0.0, 100.0)

    // Re-apply engine veto cap after personal delta (same as JS Fix #7)
    if (!veto && audit.veto.triggered && personalScore > audit.veto.cap) {
        personalScore = audit.veto.cap.toDouble()
    }

    return PersonalScoreResult(
        personalScore = personalScore.roundToInt(),
        delta         = if (veto) -audit.score else delta.roundToInt(),
        adjustments   = adjustments,
        applicable    = true,
        dietReason    = dietReason,
        veto          = veto,
        allergenHits  = allergenHits,
    )
}

/** Map 0-100 personal score to grade. Same breakpoints as the main engine. */
fun personalGrade(score: Int): Grade = when {
    score >= 85 -> Grade.A_PLUS
    score >= 70 -> Grade.A
    score >= 55 -> Grade.B
    score >= 40 -> Grade.C
    score >= 25 -> Grade.D
    else        -> Grade.F
}
