package fr.scanneat.domain.engine.biolism

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────
// FULL METABOLIC RESULT — all downstream values
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.computeMetabolics(
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
    val rwlMlHr    = vePerMin * 60.0 * (44.0 - 10.0) / 760.0 * (18.0 / 22.4)
    val tewlMlHr   = 0.45 * (bsa * 10000.0) / 1000.0   // Pinnagoda 1990
    val iwlMlHr    = rwlMlHr + tewlMlHr
    val metWaterMlHr   = metWaterMin * 60.0
    val netHydroMlHr   = metWaterMlHr - iwlMlHr

    // ── Navy tape BF% — Hodgdon & Beckett 1984 ───────────────────────────
    // The published coefficients (86.010/70.041/36.76 for men,
    // 163.205/97.684/78.387 for women) are fit to inches, not cm - wc/hc/nc/h
    // are cm everywhere else in this app, so each is converted to inches
    // right before this formula only (not reassigned) to avoid corrupting
    // whtr/whr/bai/ibw below, which all expect the cm values.
    val wc = profile.waistCm; val hc = profile.hipCm; val nc = profile.neckCm
    var navyBfPct: Double? = null; var navyFfm: Double? = null; var navyFm: Double? = null
    val haveNavyM = g == BiolismSex.MALE   && wc > 0 && nc > 0 && h > 0 && (wc - nc) > 0
    val haveNavyF = g == BiolismSex.FEMALE && wc > 0 && hc > 0 && nc > 0 && h > 0 && (wc + hc - nc) > 0
    if (haveNavyM) {
        val wcIn = wc / 2.54; val ncIn = nc / 2.54; val hIn = h / 2.54
        val v = (86.010 * log10(wcIn - ncIn) - 70.041 * log10(hIn) + 36.76).coerceIn(3.0, 65.0)
        navyBfPct = v; navyFfm = w * (1 - v / 100); navyFm = w - navyFfm!!
    } else if (haveNavyF) {
        val wcIn = wc / 2.54; val hcIn = hc / 2.54; val ncIn = nc / 2.54; val hIn = h / 2.54
        val v = (163.205 * log10(wcIn + hcIn - ncIn) - 97.684 * log10(hIn) - 78.387).coerceIn(3.0, 65.0)
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
