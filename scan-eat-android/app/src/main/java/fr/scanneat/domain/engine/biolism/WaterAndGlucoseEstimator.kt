package fr.scanneat.domain.engine.biolism

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────
// DAILY WATER NEED — EFSA 2010 gender baseline + activity bonus
// Male 2.5L / Female 2.0L baseline, +0.5L when activity multiplier ≥ 1.55
// (matches the "moderate" tier and above in ACTIVITY_LEVELS).
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.computeWaterNeedL(sex: BiolismSex, activityMult: Double): Double {
    val base  = if (sex == BiolismSex.MALE) 2.5 else 2.0
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
