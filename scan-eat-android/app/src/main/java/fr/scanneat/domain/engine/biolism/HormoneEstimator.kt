package fr.scanneat.domain.engine.biolism

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────
// HORMONE ESTIMATION — 12 hormones, phase-aware
// Sources cited inline in App.jsx are preserved in comment blocks.
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.computeHormones(
    profile: BiolismProfile,
    m: MetabolicResult,
    ketoHours: Double,
    fastingHours: Double,
    ketoAdapted: Boolean,
): HormoneResult? {
    if (!profile.isValid) return null
    val g    = profile.sex
    val age  = profile.ageYears.toDouble()
    val kh   = ketoHours.coerceAtLeast(0.0)
    val fh   = fastingHours.coerceAtLeast(0.0)
    val isMale = g == BiolismSex.MALE
    val bfPct  = m.bfPct
    val fm     = m.fm

    // ── Testosterone ──────────────────────────────────────────────────────
    val testBase = if (isMale) {
        val base = if (age <= 30) 600.0 else (600.0 * 0.99.pow(age - 30.0)).coerceAtLeast(150.0)
        val bfAdj = if (bfPct > 25) 1.0 - (bfPct - 25.0) * 0.016 else if (bfPct < 15) 1.07 else 1.0
        base * bfAdj
    } else {
        if (age <= 20) 50.0 else max(10.0, 50.0 * 0.992.pow(age - 20.0))
    }
    val ketoTestMod = if (kh > 24) 1.0 + min(0.15, kh / (7.0 * 24.0) * 0.15) else 1.0
    val fastTestMod = if (fh > 0) {
        if (fh <= 48) 1.0 + (fh / 48.0) * 0.18
        else max(1.0, 1.18 - (fh - 48.0) / 72.0 * 0.18)
    } else 1.0
    val testosterone = testBase * ketoTestMod * fastTestMod
    val testLow = if (isMale) 300.0 else 15.0; val testHigh = if (isMale) 1000.0 else 70.0

    // ── Estradiol ─────────────────────────────────────────────────────────
    val estradiol: Double
    if (isMale) {
        val arom = 0.042 * (1.0 + max(0.0, bfPct - 20.0) * 0.005)
        estradiol = min(testosterone * arom, 60.0)
    } else {
        val menopauseAge = 52.0
        val cycleBase: Double = if (age >= menopauseAge) {
            max(10.0, 15.0 * 0.97.pow(age - menopauseAge))
        } else {
            val cd = profile.cycleDay.coerceIn(1, 28).toDouble()
            val raw = when {
                cd <= 5  -> 50.0
                cd <= 13 -> 60.0 + (200.0 - 60.0) * ((cd - 5.0) / 8.0)
                cd <= 14 -> 350.0
                cd <= 21 -> 100.0 + (200.0 - 100.0) * ((cd - 15.0) / 6.0)
                else     -> 200.0 - (200.0 - 60.0) * ((cd - 21.0) / 7.0)
            }
            val periMod = if (age >= 40) max(0.35, 1.0 - (age - 40.0) * 0.054) else 1.0
            val bfBoost = if (bfPct > 30) 1.0 + (bfPct - 30.0) * 0.012 else 1.0
            raw * periMod * bfBoost
        }
        val fastE2 = if (fh > 24) max(0.80, 1.0 - (fh - 24.0) / 200.0 * 0.20) else 1.0
        estradiol = cycleBase * fastE2
    }
    val e2Low  = if (isMale) 8.0  else if (age > 52) 5.0  else 50.0
    val e2High = if (isMale) 42.0 else if (age > 52) 35.0 else 400.0

    // ── Cortisol ──────────────────────────────────────────────────────────
    val fastCort = if (fh > 0) 1.0 + min(0.40, fh / 48.0 * 0.40) else 1.0
    val ketoCort = if (kh > 48) max(0.88, 1.0 - (kh - 48.0) / 500.0 * 0.12) else 1.0
    val ageCort  = if (age > 60) 0.995.pow(age - 60.0) else 1.0
    val cortisol = 12.0 * fastCort * ketoCort * ageCort

    // ── Insulin ───────────────────────────────────────────────────────────
    val bfInsulin   = if (bfPct > 25) 1.0 + (bfPct - 25.0) * 0.04 else 1.0
    val ketoInsulin = if (kh > 24) max(0.35, 1.0 - (kh / 72.0) * 0.65) else 1.0
    val fastInsulin = if (fh > 0)  max(0.4,  1.0 - (fh / 24.0) * 0.50) else 1.0
    val insulin     = 6.5 * bfInsulin * ketoInsulin * fastInsulin

    // ── Leptin ────────────────────────────────────────────────────────────
    val leptinBase  = if (isMale) fm * 0.30 else fm * 0.52
    val fastLeptin  = if (fh > 0) max(0.50, 1.0 - (fh / 48.0) * 0.50) else 1.0
    val ketoLeptin  = if (kh > 72) if (ketoAdapted) 0.70 else 0.80 else 1.0
    val leptin      = max(0.5, leptinBase) * fastLeptin * ketoLeptin
    val leptinLow   = if (isMale) 0.5 else 1.0; val leptinHigh = if (isMale) 12.0 else 24.0

    // ── Ghrelin ───────────────────────────────────────────────────────────
    val fastGhrelin = if (fh > 0) {
        if (fh <= 24) 1.0 + (fh / 24.0) * 0.50
        else max(1.20, 1.50 - (fh - 24.0) / 96.0 * 0.30)
    } else 1.0
    val ketoGhrelin = if (kh > 24) if (ketoAdapted) 0.85 else 0.92 else 1.0
    val ageGhrelin  = if (age > 60) 0.996.pow(age - 60.0) else 1.0
    val ghrelin     = 600.0 * fastGhrelin * ketoGhrelin * ageGhrelin

    // ── Glucagon ──────────────────────────────────────────────────────────
    val ketoGlucagon = if (kh > 0) 1.0 + min(0.60, kh / 48.0 * 0.60) * (if (ketoAdapted) 0.7 else 1.0) else 1.0
    val fastGlucagon = if (fh > 0) 1.0 + min(0.30, fh / 48.0 * 0.30) else 1.0
    val glucagon     = 80.0 * ketoGlucagon * fastGlucagon

    // ── Growth Hormone ────────────────────────────────────────────────────
    val ghBase = max(0.2, if (age <= 30) 3.5 else 3.5 * 0.986.pow(age - 30.0))
    val fastGH = if (fh > 0) 1.0 + min(2.0, fh / 24.0 * 1.8) else 1.0
    val bfGH   = if (bfPct > 25) max(0.5, 1.0 - (bfPct - 25.0) * 0.02) else 1.0
    val gh     = ghBase * fastGH * bfGH

    // ── IGF-1 ─────────────────────────────────────────────────────────────
    val igf1Base = max(60.0, if (age <= 25) 250.0 else 250.0 * 0.985.pow(age - 25.0))
    val fastIgf1 = if (fh > 24) max(0.80, 1.0 - (fh - 24.0) / 120.0 * 0.20) else 1.0
    val igf1     = igf1Base * fastIgf1

    // ── Free T3 ───────────────────────────────────────────────────────────
    val ketoT3 = if (kh > 48) max(0.84, 1.0 - (kh - 48.0) / 480.0 * 0.16) else 1.0
    val fastT3 = if (fh > 48) max(0.82, 1.0 - (fh - 48.0) / 200.0 * 0.18) else 1.0
    val ageT3  = if (age > 65) 0.997.pow(age - 65.0) else 1.0
    val fT3    = 3.2 * ketoT3 * fastT3 * ageT3

    // ── DHEA-S ────────────────────────────────────────────────────────────
    val dheaS = max(15.0, if (isMale) {
        if (age <= 25) 320.0 else 320.0 * 0.980.pow(age - 25.0)
    } else {
        if (age <= 25) 240.0 else 240.0 * 0.980.pow(age - 25.0)
    })
    val dheaSLow  = if (isMale) 80.0 else 35.0; val dheaSHigh = if (isMale) 500.0 else 430.0

    // ── Progesterone (female only) ────────────────────────────────────────
    val progesterone: Double? = if (isMale) null else {
        val menopauseAge = 52.0
        val cd = profile.cycleDay.coerceIn(1, 28).toDouble()
        when {
            age >= menopauseAge -> 0.2
            age >= 40.0 -> {
                val periProg = max(0.5, 8.0 * 0.93.pow(age - 40.0))
                val v = when {
                    cd <= 14 -> 0.8
                    cd <= 21 -> periProg * ((cd - 14.0) / 7.0)
                    else     -> periProg * max(0.0, 1.0 - (cd - 21.0) / 7.0)
                }
                max(0.3, v)
            }
            else -> {
                val v = when {
                    cd <= 14 -> 0.5 + (cd / 14.0) * 1.0
                    cd <= 21 -> 1.5 + (20.0 - 1.5) * ((cd - 14.0) / 7.0)
                    else     -> 20.0 - (20.0 - 0.5) * ((cd - 21.0) / 7.0)
                }
                max(0.3, v)
            }
        }
    }

    fun classify(v: Double, lo: Double, hi: Double): Pair<String, String> = when {
        v < lo * 0.85 -> "Low" to "Danger"
        v < lo        -> "Low-Normal" to "Warm"
        v > hi * 1.15 -> "High" to "Violet"
        v > hi        -> "High-Normal" to "Teal"
        else          -> "Normal" to "Teal"
    }

    fun hr(v: Double, lo: Double, hi: Double, unit: String): HormoneReading {
        val (label, color) = classify(v, lo, hi)
        return HormoneReading(v, unit, lo, hi, label, color)
    }

    return HormoneResult(
        testosterone  = hr(testosterone, testLow, testHigh, "ng/dL"),
        estradiol     = hr(estradiol, e2Low, e2High, "pg/mL"),
        cortisol      = hr(cortisol, 5.0, 25.0, "µg/dL"),
        insulin       = hr(insulin, 2.0, 25.0, "µU/mL"),
        leptin        = hr(leptin, leptinLow, leptinHigh, "ng/mL"),
        ghrelin       = hr(ghrelin, 300.0, 1000.0, "pg/mL"),
        glucagon      = hr(glucagon, 50.0, 200.0, "pg/mL"),
        gh            = hr(gh, 0.1, 10.0, "ng/mL"),
        igf1          = hr(igf1, 70.0, 350.0, "ng/mL"),
        fT3           = hr(fT3, 2.3, 4.1, "pg/mL"),
        dheaS         = hr(dheaS, dheaSLow, dheaSHigh, "µg/dL"),
        progesterone  = progesterone?.let { hr(it, 0.3, 20.0, "ng/mL") },
    )
}
