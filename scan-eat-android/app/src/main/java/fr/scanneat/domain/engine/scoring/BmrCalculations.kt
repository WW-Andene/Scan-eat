package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ============================================================================
// PERSONAL SCORE ENGINE — port of public/core/personal-score.js
//                       + public/data/profile.js helper functions
//
// AUTHORITATIVE anchors (preserved from original):
//   Protein PRI: EFSA Scientific Opinion 2012;10(2):2557 (0.83 g/kg adults);
//     ≥65y 1.0 g/kg for sarcopenia prevention: PROT-AGE Study Group
//     (Bauer et al., J Am Med Dir Assoc 2013;14(8):542-559), not an EFSA figure
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
    // Age has no upper sanity cap upstream in profile entry, and the formula's
    // -5×age term can drive BMR toward/below zero for a very old, light, short
    // profile (e.g. age=130, weight=35kg, height=140cm) - clamped to the range
    // the formula was actually validated for, then floored at a minimum
    // physiologically plausible BMR so nothing downstream (daily targets,
    // sat-fat/sugar budgets) ever displays a negative or near-zero value.
    val clampedAge = p.ageYears!!.coerceIn(1, 110)
    val raw = 10.0 * p.weightKg!! + 6.25 * p.heightCm!! - 5.0 * clampedAge + sexOffset
    return raw.coerceAtLeast(500.0)
}

fun tdeeKcal(p: Profile): Double? {
    val bmr = bmrMifflinStJeor(p) ?: return null
    val pal = ACTIVITY_PAL[p.activityLevel] ?: return null
    return (bmr * pal)
}

/** BMI = kg / m². Returns null if height/weight missing or non-positive. */
fun bmi(p: Profile): Double? {
    val h = p.heightCm ?: return null
    val w = p.weightKg ?: return null
    // Same non-positive guard as BiolismEngine.computeMetabolics() - height/weight
    // are non-null Doubles here (not Int), so a stray 0.0 (or negative) passes the
    // ?: return null above untouched and previously divided by zero silently
    // producing Infinity instead of a clean "can't compute" null.
    if (h <= 0.0 || w <= 0.0) return null
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

/** EFSA PRI: 0.83 g/kg adults; 1.0 g/kg ≥65y (PROT-AGE Study Group, sarcopenia prevention). */
fun proteinPriG(p: Profile): Double? {
    val w = p.weightKg ?: return null
    val age = p.ageYears ?: return null
    val perKg = if (age >= 65) 1.0 else 0.83
    return (w * perKg).roundToInt().toDouble()
}
