package fr.scanneat.domain.engine.biolism

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────
// DAILY WATER NEED — EFSA 2010 gender baseline (or, when weight is known, a
// weight-proportional baseline) + activity bonus.
// +0.5L when activity multiplier ≥ 1.55 (matches the "moderate" tier and
// above in ACTIVITY_LEVELS).
// ─────────────────────────────────────────────────────────────────────────
/**
 * User-reported: weight wasn't used anywhere in this formula - a 50kg and a
 * 100kg person of the same sex/activity level got an identical target, even
 * though real hydration guidance (commonly cited as 30-35 mL/kg/day for
 * adults) scales with body weight, not a flat two-bucket sex baseline.
 * [weightKg] is optional (Profile.weightKg is nullable) - null falls back to
 * the prior EFSA 2010 sex-only baseline (2.5L male / 2.0L female) unchanged.
 * 0.033 L/kg (33 mL/kg) is the midpoint of that commonly-cited range;
 * clamped to a sane 1.5-4.5L band so a data-entry weight typo can't produce
 * an absurd target.
 */
fun BiolismEngine.computeWaterNeedL(sex: BiolismSex, activityMult: Double, weightKg: Double? = null): Double {
    val base = if (weightKg != null && weightKg > 0) (weightKg * 0.033).coerceIn(1.5, 4.5)
               else if (sex == BiolismSex.MALE) 2.5 else 2.0
    val bonus = if (activityMult >= 1.55) 0.5 else 0.0
    return base + bonus
}

// ─────────────────────────────────────────────────────────────────────────
// BLOOD GLUCOSE ESTIMATE — Guyton & Hall 2016, Cahill 1966, Björntorp 2000
// Simplified kinetic model: carb oxidation draws down a ~20% BW glucose
// distribution volume, capped at the glycogen-exhaustion window, plus a
// cortisol-driven GNG-rebound bell curve at 8–20h and recent-meal recovery.
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.computeBloodGlucoseMmol(
    weightKg: Double,
    kcalSec: Double,
    carbFrac: Double,
    ketoHours: Double,
    fastingHours: Double,
    ketosis: Boolean,
    elapsedSec: Double,
): Double {
    val glucVolL = weightKg * 0.200
    val baseline = 5.0
    val carbOxRateMmolS = (kcalSec * carbFrac / 4.0) / 0.18
    val capSec = if (ketosis) 8.0 * 3600.0 else 24.0 * 3600.0
    val glucElapsed = min(elapsedSec.coerceAtLeast(0.0), capSec)
    val glucDropMmol = (carbOxRateMmolS * glucElapsed * 0.50) / glucVolL.coerceAtLeast(1.0)
    val glucFloor = if (ketoHours > 72.0) 3.8 else 2.5
    val glucDepleted = max(glucFloor, baseline - glucDropMmol)
    val recoveryFrac = if (fastingHours < 2.0) 1.0 - (fastingHours / 2.0) else 0.0
    val postDrain = glucDepleted + (baseline - glucDepleted) * recoveryFrac
    val rebumpApplies = if (ketosis) ketoHours else fastingHours
    val gngRebound = if (rebumpApplies in 8.0..20.0) {
        val centerH = 13.0; val sigmaH = 3.5
        0.40 * exp(-0.5 * ((rebumpApplies - centerH) / sigmaH).pow(2))
    } else 0.0
    return postDrain + gngRebound
}
