package fr.scanneat.domain.engine.biolism

import kotlin.math.*

// ============================================================================
// BIOLISM ENGINE — pure Kotlin port of all compute functions from App.jsx
// All source citations preserved. No I/O, no Android dependencies — pure math.
// ============================================================================

object BiolismEngine {

    // ─────────────────────────────────────────────────────────────────────────
    // RQ CURVE — Cahill 1966, Owen 1967, Volek & Phinney 2012, Keys 1950
    // 7-phase continuous non-protein RQ from ketoHours elapsed.
    // Phase 0  0–8h:    glycogen depletion       0.858 → 0.828
    // Phase 1  8–24h:   lipolysis rising         0.828 → 0.788
    // Phase 2  24–72h:  ketosis onset            0.788 → 0.768
    // Phase 3  72–168h: deep ketosis             0.768 → 0.735
    // Phase 4  168–504h:prolonged fast           0.735 → 0.720
    // Phase 5  504–1440h:keto-adapted            0.720 → 0.710
    // Phase 6  1440h+:  extended starvation RQ creeps back (Keys 1950)
    // ─────────────────────────────────────────────────────────────────────────
    fun computeKetoRQ(ketoHours: Double, ketoAdapted: Boolean): Double {
        if (ketoHours <= 0.0) return 0.858
        if (ketoHours <= 8.0)   return 0.858 - 0.030 * (ketoHours / 8.0)
        if (ketoHours <= 24.0)  return 0.828 - 0.040 * ((ketoHours - 8.0) / 16.0)
        if (ketoHours <= 72.0)  return 0.788 - 0.020 * ((ketoHours - 24.0) / 48.0)
        if (ketoHours <= 168.0) return 0.768 - 0.033 * ((ketoHours - 72.0) / 96.0)
        if (ketoHours <= 504.0) return 0.735 - 0.015 * ((ketoHours - 168.0) / 336.0)
        if (ketoAdapted || ketoHours <= 1440.0) {
            return 0.720 - 0.010 * (min(ketoHours, 1440.0) - 504.0) / 936.0
        }
        // Phase 6: extended starvation
        val extra6 = ketoHours - 1440.0
        return min(0.740, 0.710 + 0.030 * (1.0 - exp(-extra6 / 720.0)))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBSTRATE PARTITION — Frayn 1983, Cahill 1966, Owen 1967, Keys 1950
    // 3-pool model: fat / carb / protein, protein fraction varies by phase.
    // ─────────────────────────────────────────────────────────────────────────
    fun computeSubstrates(npRQ: Double, ketoHours: Double): SubstrateResult {
        val kh = ketoHours.coerceAtLeast(0.0)

        val protFrac = when {
            kh <= 0.0   -> 0.170
            kh <= 24.0  -> 0.170 + 0.020 * (kh / 24.0)
            kh <= 96.0  -> 0.190 + 0.030 * ((kh - 24.0) / 72.0)
            kh <= 168.0 -> 0.220 - 0.040 * ((kh - 96.0) / 72.0)
            kh <= 504.0 -> 0.180 - 0.060 * ((kh - 168.0) / 336.0)
            kh <= 1440.0 -> 0.120 - 0.030 * ((kh - 504.0) / 936.0)
            else -> {
                val ex = kh - 1440.0
                min(0.150, 0.090 + 0.060 * (1.0 - exp(-ex / 720.0)))
            }
        }

        val npFrac   = 1.0 - protFrac
        val fatNP    = ((1.0 - npRQ) / 0.30).coerceIn(0.0, 1.0)
        val carbNP   = 1.0 - fatNP
        val fatFrac  = fatNP  * npFrac
        val carbFrac = carbNP * npFrac
        val rq       = fatFrac * 0.70 + carbFrac * 1.00 + protFrac * 0.81
        val oxycal   = fatFrac * 4.686 + carbFrac * 5.047 + protFrac * 4.485

        return SubstrateResult(
            fatFrac   = fatFrac,
            carbFrac  = carbFrac,
            protFrac  = protFrac,
            rq        = rq,
            npRq      = npRQ,
            oxycaloric = oxycal,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORGAN HEAT REDISTRIBUTION — Elia 1992 baseline, Cahill 2006 adaptation
    // ─────────────────────────────────────────────────────────────────────────
    fun computeOrganPcts(ketoHours: Double, adaptBlend: Double): List<OrganPct> {
        val kh = ketoHours.coerceAtLeast(0.0)
        val t  = min(1.0, kh / 72.0)

        // Acute targets (72h)
        val brainAcute   = 18.0 - t * 5.4   // → 12.6
        val liverAcute   = 26.0 + t * 4.0   // → 30.0
        val muscleAcute  = 22.0 + t * 0.6   // → 22.6
        val kidneysAcute =  9.0 + t * 0.4   // → 9.4
        val heartAcute   =  9.0 + t * 0.4   // → 9.4

        // Long-term adapted targets 21d+ (Cahill 2006)
        var brainAdapt   = 11.0
        var liverAdapt   = 32.0
        var muscleAdapt  = 23.0
        val kidneysAdapt =  9.8
        val heartAdapt   =  9.6

        // Extended starvation 60d+ — muscle rises as lean mass catabolises (Keys 1950)
        if (kh > 1440.0) {
            val ex = kh - 1440.0
            val catFrac = min(0.4, 1.0 - exp(-ex / 720.0))
            muscleAdapt = 23.0 + catFrac * 4.0
            brainAdapt  = 11.0 + catFrac * 1.5
            liverAdapt  = 32.0 - catFrac * 2.0
        }

        val blend   = adaptBlend.coerceIn(0.0, 1.0)
        val brain   = brainAcute   + blend * (brainAdapt   - brainAcute)
        val liver   = liverAcute   + blend * (liverAdapt   - liverAcute)
        val muscle  = muscleAcute  + blend * (muscleAdapt  - muscleAcute)
        val kidneys = kidneysAcute + blend * (kidneysAdapt - kidneysAcute)
        val heart   = heartAcute   + blend * (heartAdapt   - heartAcute)
        val residual = 100.0 - brain - liver - muscle - kidneys - heart

        return listOf(
            OrganPct("Liver",           liver.roundTo(1),   "Gold"),
            OrganPct("Skeletal Muscle", muscle.roundTo(1),  "Teal"),
            OrganPct("Brain",           brain.roundTo(1),   "Violet"),
            OrganPct("Residual",        residual.roundTo(1),"IconInactive"),
            OrganPct("Kidneys",         kidneys.roundTo(1), "Teal"),
            OrganPct("Heart",           heart.roundTo(1),   "Gold"),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FULL METABOLIC RESULT — all downstream values
    // ─────────────────────────────────────────────────────────────────────────
    fun computeMetabolics(
        profile: BiolismProfile,
        npRqOverride: Double? = null,
        ketoHours: Double = 0.0,
        ketoAdapted: Boolean = false,
    ): MetabolicResult? {
        val w = profile.weightKg
        val h = profile.heightCm
        val a = profile.ageYears.toDouble()
        val g = profile.sex
        if (w <= 0 || h <= 0 || a <= 0 || g == BiolismSex.NOT_SPECIFIED) return null

        // Effective keto hours: ketoAdapted flag floors at 72h
        val effKh = if (ketoAdapted && ketoHours < 72.0) 72.0 else ketoHours.coerceAtLeast(0.0)

        // ── BMR: Mifflin-St Jeor + Katch-McArdle average ────────────────────
        val offset   = if (g == BiolismSex.MALE) 5.0 else -161.0
        val bmrMsj   = 10.0 * w + 6.25 * h - 5.0 * a + offset
        val hm       = h / 100.0
        val bmi      = w / (hm * hm)
        val sexBin   = if (g == BiolismSex.MALE) 1.0 else 0.0

        // ── Ethnicity-corrected BF% — Deurenberg 1991 + Deurenberg 1998 ─────
        val ethnic    = profile.ethnicMeta
        val bmiForBF  = bmi - ethnic.bmiOffset
        val bfRaw     = 1.20 * bmiForBF + 0.23 * a - 10.8 * sexBin - 5.4
        val bfPct     = bfRaw.coerceIn(3.0, 65.0)
        val ffm       = w * (1.0 - bfPct / 100.0)
        val fm        = w - ffm
        val bmrKm     = 370.0 + 21.6 * ffm
        val bmrDay    = (bmrMsj + bmrKm) / 2.0
        val bsa       = 0.007184 * w.pow(0.425) * h.pow(0.725)  // DuBois 1916

        // ── Substrates ───────────────────────────────────────────────────────
        val npRQ  = npRqOverride ?: 0.858
        val sub   = computeSubstrates(npRQ, effKh)
        val vo2PerMin  = bmrDay / (sub.oxycaloric * 1440.0)
        val vco2PerMin = vo2PerMin * sub.rq
        val vePerMin   = vo2PerMin / (0.2093 - 0.160)  // West 2012: FiO2−FeO2

        // ── Adaptive suppression — T3/thyroid + Keys starvation ──────────────
        // Sources: Phinney 1983, Keys 1950, Leibel 1995, Bisschop 2001
        val ketoSuppr = when {
            effKh <= 0.0   -> 1.000
            effKh <= 72.0  -> 1.000 - 0.050 * (effKh / 72.0)
            effKh <= 168.0 -> 0.950 - 0.030 * ((effKh - 72.0) / 96.0)
            effKh <= 504.0 -> 0.920 - 0.040 * ((effKh - 168.0) / 336.0)
            effKh <= 1440.0 -> 0.880 - 0.030 * ((effKh - 504.0) / 936.0)
            else -> {
                val ex = effKh - 1440.0
                max(0.800, 0.850 - 0.050 * (1.0 - exp(-ex / 720.0)))
            }
        }

        val kcalSec = (bmrDay * ketoSuppr) / 86400.0
        val watts   = (bmrDay * ketoSuppr) * 4184.0 / 86400.0
        val kcalPerMin = bmrDay * ketoSuppr / 1440.0

        // ── Organ heat distribution ──────────────────────────────────────────
        val adaptBlend = if (ketoAdapted) 1.0
                         else max(0.0, min(1.0, (effKh - 72.0) / (504.0 - 72.0)))
        val organs = computeOrganPcts(effKh, adaptBlend)

        // ── TDEE ─────────────────────────────────────────────────────────────
        val act    = profile.activityMeta
        val tdee   = bmrDay * ketoSuppr * act.mult

        // ── Substrate oxidation rates (g/min) ────────────────────────────────
        val fatOx  = (sub.fatFrac  * kcalPerMin) / 9.0
        val carbOx = (sub.carbFrac * kcalPerMin) / 4.0
        val protOx = (sub.protFrac * kcalPerMin) / 4.1

        // ── FFA lipolysis flux (Wolfe 1990) ──────────────────────────────────
        val reesterFrac = when {
            effKh <= 0.0   -> 0.050
            effKh <= 72.0  -> 0.050 - 0.020 * (effKh / 72.0)
            effKh <= 168.0 -> 0.030 - 0.008 * ((effKh - 72.0) / 96.0)
            effKh <= 504.0 -> 0.022 - 0.007 * ((effKh - 168.0) / 336.0)
            else           -> 0.015
        }
        val ffaFlux = fatOx / (1.0 - reesterFrac)

        // ── Hepatic FFA fraction → BHB (McGarry & Foster 1980) ───────────────
        val hepaticFrac = when {
            effKh <= 0.0    -> 0.150
            effKh <= 72.0   -> 0.150 + 0.250 * (effKh / 72.0)
            effKh <= 168.0  -> 0.400 + 0.030 * ((effKh - 72.0) / 96.0)
            effKh <= 504.0  -> 0.430 + 0.020 * ((effKh - 168.0) / 336.0)
            effKh <= 1440.0 -> 0.450
            else -> {
                val ex = effKh - 1440.0
                max(0.300, 0.450 - 0.150 * (1.0 - exp(-ex / 720.0)))
            }
        }
        val ketoAct = when {
            effKh <= 0.0    -> 0.0
            effKh <= 24.0   -> min(1.0, effKh / 24.0)
            effKh <= 1440.0 -> 1.0
            else -> {
                val ex = effKh - 1440.0
                max(0.6, 1.0 - 0.4 * (1.0 - exp(-ex / 720.0)))
            }
        }
        val hepaticFfa = ffaFlux * hepaticFrac
        val bhbMmolPerMin = if (ketoAct > 0) (hepaticFfa / 0.256) * 4.0 * ketoAct else 0.0

        // ── GNG fraction (Cahill 1966 / Owen 1967 / Keys 1950) ───────────────
        val gngProtFrac = when {
            effKh <= 0.0    -> 0.300
            effKh <= 96.0   -> 0.300 + 0.550 * (effKh / 96.0)
            effKh <= 168.0  -> 0.850 - 0.150 * ((effKh - 96.0) / 72.0)
            effKh <= 504.0  -> 0.700 - 0.150 * ((effKh - 168.0) / 336.0)
            effKh <= 1440.0 -> 0.550 - 0.050 * ((effKh - 504.0) / 936.0)
            else -> {
                val ex = effKh - 1440.0
                min(0.750, 0.500 + 0.250 * (1.0 - exp(-ex / 720.0)))
            }
        }
        val gngGPerHr = protOx * gngProtFrac * 0.58 * 60.0

        // ── Acetyl-CoA (Berg 2015) ────────────────────────────────────────────
        val acCoaFat  = (fatOx  / 0.256) * 8.0
        val acCoaCarb = (carbOx / 0.180) * 2.0
        val acCoaProt = (protOx / 0.110) * 0.6
        val acCoaTotal = acCoaFat + acCoaCarb + acCoaProt

        // ── Physiological rates ───────────────────────────────────────────────
        val hrEstimated = vo2PerMin / 0.0035  // Fick (SV 70mL, a-vO2 50mL/L)
        val atpPerKcal  = sub.fatFrac * 0.0453 + sub.carbFrac * 0.0453 + sub.protFrac * 0.0444
        val atpMmol     = atpPerKcal * kcalPerMin * 1000.0
        val metWaterMin = (sub.fatFrac  * kcalPerMin * 0.1189 +
                           sub.carbFrac * kcalPerMin * 0.139  +
                           sub.protFrac * kcalPerMin * 0.105)  // g/min (Hill 2004)
        val nExcrGPerDay   = (sub.protFrac * bmrDay * ketoSuppr) / 26.51  // Consolazio 1963

        // ── Heat dissipation — Fanger 1970 / ASHRAE 55 ───────────────────────
        val heatRad      = watts * 0.45
        val heatConv     = watts * 0.30
        val heatCond     = watts * 0.02
        val heatEvap     = watts * 0.23
        val heatEvapSkin = watts * 0.15
        val heatEvapResp = watts * 0.08

        // ── Insensible water loss ─────────────────────────────────────────────
        val rwlMlHr    = vePerMin * 60.0 * (44.0 - 10.0) / 760.0 * (18.0 / 22.4) * 1000.0
        val tewlMlHr   = 0.45 * (bsa * 10000.0) / 1000.0   // Pinnagoda 1990
        val iwlMlHr    = rwlMlHr + tewlMlHr
        val metWaterMlHr   = metWaterMin * 60.0
        val netHydroMlHr   = metWaterMlHr - iwlMlHr

        // ── Navy tape BF% — Hodgdon & Beckett 1984 ───────────────────────────
        val wc = profile.waistCm; val hc = profile.hipCm; val nc = profile.neckCm
        var navyBfPct: Double? = null; var navyFfm: Double? = null; var navyFm: Double? = null
        val haveNavyM = g == BiolismSex.MALE   && wc > 0 && nc > 0 && h > 0 && (wc - nc) > 0
        val haveNavyF = g == BiolismSex.FEMALE && wc > 0 && hc > 0 && nc > 0 && h > 0 && (wc + hc - nc) > 0
        if (haveNavyM) {
            val v = (86.010 * log10(wc - nc) - 70.041 * log10(h) + 36.76).coerceIn(3.0, 65.0)
            navyBfPct = v; navyFfm = w * (1 - v / 100); navyFm = w - navyFfm!!
        } else if (haveNavyF) {
            val v = (163.205 * log10(wc + hc - nc) - 97.684 * log10(h) - 78.387).coerceIn(3.0, 65.0)
            navyBfPct = v; navyFfm = w * (1 - v / 100); navyFm = w - navyFfm!!
        }

        // ── Visceral ratios ───────────────────────────────────────────────────
        val whtr = if (wc > 0 && h > 0) wc / h else null
        val whr  = if (wc > 0 && hc > 0) wc / hc else null
        val bai  = if (hc > 0 && h > 0) ((hc / hm.pow(1.5)) - 18.0).coerceAtLeast(0.0) else null

        // ── IBW — Devine/Robinson/Miller ─────────────────────────────────────
        val hInches  = h / 2.54
        val hOver60  = max(0.0, hInches - 60.0)
        val ibwDev   = if (g == BiolismSex.MALE) 50.0 + 2.30 * hOver60 else 45.5 + 2.30 * hOver60
        val ibwRob   = if (g == BiolismSex.MALE) 52.0 + 1.90 * hOver60 else 49.0 + 1.70 * hOver60
        val ibwMil   = if (g == BiolismSex.MALE) 56.2 + 1.41 * hOver60 else 53.1 + 1.36 * hOver60
        val ibwMean  = (ibwDev + ibwRob + ibwMil) / 3.0

        // ── BMI classification ────────────────────────────────────────────────
        val bmiClass = when {
            bmi < 18.5              -> BiolismBmiCategory.UNDERWEIGHT
            bmi < ethnic.bmiOverweight -> BiolismBmiCategory.NORMAL
            bmi < ethnic.bmiObese   -> BiolismBmiCategory.OVERWEIGHT
            else                    -> BiolismBmiCategory.OBESE
        }

        // ── Macro targets ─────────────────────────────────────────────────────
        val protGPerKgFfm = mapOf(
            "sedentary" to 1.2, "light" to 1.4, "moderate" to 1.6, "very" to 1.8, "extra" to 2.0
        )[profile.activityId] ?: 1.6
        val macroProtMin  = protGPerKgFfm * ffm
        val macroProtKcal = macroProtMin * 4.0
        val macroCarbMin  = if (effKh > 24.0) 30.0 else 120.0
        val macroCarbKcal = macroCarbMin * 4.0
        val essentialFat  = 0.5 * w
        val macroFatMin   = max(essentialFat, (tdee - macroProtKcal - macroCarbKcal) / 9.0)
        val macroFatKcal  = macroFatMin * 9.0
        val macroFloor    = macroProtKcal + macroCarbKcal + macroFatKcal
        val waterNeed     = (if (g == BiolismSex.MALE) 2.5 else 2.0) + (if (act.mult >= 1.55) 0.5 else 0.0)

        return MetabolicResult(
            bmi = bmi.roundTo(2), bmiClass = bmiClass,
            bfPct = bfPct.roundTo(1), ffm = ffm.roundTo(3), fm = fm.roundTo(3), bsa = bsa.roundTo(4),
            navyBfPct = navyBfPct?.roundTo(1), navyFfm = navyFfm?.roundTo(2), navyFm = navyFm?.roundTo(2),
            ibwDevine = ibwDev.roundTo(1), ibwRobinson = ibwRob.roundTo(1), ibwMiller = ibwMil.roundTo(1), ibwMean = ibwMean.roundTo(1),
            whtr = whtr?.roundTo(4), whr = whr?.roundTo(4), bai = bai?.roundTo(2),
            bmrMsj = bmrMsj.roundTo(1), bmrKm = bmrKm.roundTo(1), bmrDay = bmrDay.roundTo(1),
            tdeeDay = tdee.roundTo(1), ketoSupprFactor = ketoSuppr,
            kcalSec = kcalSec, watts = watts,
            vo2PerMin = vo2PerMin, vco2PerMin = vco2PerMin, vePerMin = vePerMin,
            sub = sub,
            fatOxGPerMin = fatOx, carbOxGPerMin = carbOx, protOxGPerMin = protOx, ffaFluxGPerMin = ffaFlux,
            bhbMmolPerMin = bhbMmolPerMin, ketoActivation = ketoAct,
            acCoaTotalMmolMin = acCoaTotal, acCoaFatMmolMin = acCoaFat, acCoaCarbMmolMin = acCoaCarb, acCoaProtMmolMin = acCoaProt,
            gngGPerHr = gngGPerHr, gngProtFrac = gngProtFrac,
            hrEstimated = hrEstimated, atpMmolPerMin = atpMmol, metWaterPerMin = metWaterMin, nExcrGPerDay = nExcrGPerDay,
            heatRadW = heatRad, heatConvW = heatConv, heatCondW = heatCond, heatEvapW = heatEvap,
            heatEvapSkinW = heatEvapSkin, heatEvapRespW = heatEvapResp,
            rwlMlPerHr = rwlMlHr, tewlMlPerHr = tewlMlHr, iwlMlPerHr = iwlMlHr,
            metWaterMlPerHr = metWaterMlHr, netHydroBalMlPerHr = netHydroMlHr,
            organs = organs,
            macroProtMinG = macroProtMin, macroProtKcal = macroProtKcal, protGPerKgFfm = protGPerKgFfm,
            macroCarbMinG = macroCarbMin, macroCarbKcal = macroCarbKcal,
            macroFatMinG = macroFatMin, macroFatKcal = macroFatKcal, essentialFatMinG = essentialFat,
            macroFloorKcal = macroFloor, waterNeedL = waterNeed,
            ethnicMeta = ethnic,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HORMONE ESTIMATION — 12 hormones, phase-aware
    // Sources cited inline in App.jsx are preserved in comment blocks.
    // ─────────────────────────────────────────────────────────────────────────
    fun computeHormones(
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

    // ─────────────────────────────────────────────────────────────────────────
    // DAILY WATER NEED — EFSA 2010 gender baseline + activity bonus
    // Male 2.5L / Female 2.0L baseline, +0.5L when activity multiplier ≥ 1.55
    // (matches the "moderate" tier and above in ACTIVITY_LEVELS).
    // ─────────────────────────────────────────────────────────────────────────
    fun computeWaterNeedL(sex: BiolismSex, activityMult: Double): Double {
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
    fun computeBloodGlucoseMmol(
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

    // ─────────────────────────────────────────────────────────────────────────
    // KETO PHASE INFO
    // ─────────────────────────────────────────────────────────────────────────
    fun ketoPhaseInfo(ketoHours: Double, ketoAdapted: Boolean): KetoPhaseInfo {
        return when {
            ketoHours < 8 -> KetoPhaseInfo(
                KetoPhase.GLYCOGEN_DEPLETION, "Glycogen Depletion",
                "Liver glycogen depleting · glucose still primary fuel · RQ easing",
                (ketoHours / 8.0) * 100, "Gold", "< 0.2 mM")
            ketoHours < 24 -> KetoPhaseInfo(
                KetoPhase.TRANSITION, "Transition",
                "Ketone bodies rising · lipolysis accelerating · RQ dropping",
                ((ketoHours - 8) / 16.0) * 100, "Warm", "0.2–0.5 mM")
            ketoHours < 72 -> KetoPhaseInfo(
                KetoPhase.KETOSIS_ONSET, "Ketosis Onset",
                "Blood ketones 0.5–1.5 mM · fat is primary fuel",
                ((ketoHours - 24) / 48.0) * 100, "Teal", "0.5–1.5 mM")
            ketoHours < 168 -> KetoPhaseInfo(
                KetoPhase.DEEP_KETOSIS, "Deep Ketosis",
                "Blood ketones 1.5–3.0 mM · peak protein catabolism · GNG maximised",
                ((ketoHours - 72) / 96.0) * 100, "Violet", "1.5–3.0 mM")
            ketoHours < 504 -> KetoPhaseInfo(
                KetoPhase.PROLONGED_FAST, "Prolonged Fast",
                "Protein sparing activating · brain ketone uptake rising",
                ((ketoHours - 168) / 336.0) * 100, "Violet", "2.0–5.0 mM")
            ketoHours < 1440 -> KetoPhaseInfo(
                KetoPhase.KETO_ADAPTED, if (ketoAdapted) "Fully Adapted" else "Keto-Adapted",
                "Brain ~70% ketone-fuelled · protein sparing near-maximal · RQ floor 0.710",
                min(100.0, ((ketoHours - 504) / 936.0) * 100), "Gold", "3.0–6.0 mM")
            else -> KetoPhaseInfo(
                KetoPhase.EXTENDED_STARVATION, "Extended Starvation",
                "Fat stores depleting · protein catabolism rising again (Keys 1950)",
                min(100.0, ((ketoHours - 1440) / 1440.0) * 100), "Severe", "1.0–3.0 mM ↓")
        }
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────
private fun Double.roundTo(dp: Int): Double {
    val factor = 10.0.pow(dp)
    return (this * factor).roundToLong() / factor
}
private fun Double.pow(exp: Double) = Math.pow(this, exp)
private fun Double.pow(exp: Int)    = Math.pow(this, exp.toDouble())
