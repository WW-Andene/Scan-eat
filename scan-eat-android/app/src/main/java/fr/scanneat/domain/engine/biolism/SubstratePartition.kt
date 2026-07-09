package fr.scanneat.domain.engine.biolism

import kotlin.math.exp
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────
// SUBSTRATE PARTITION — Frayn 1983, Cahill 1966, Owen 1967, Keys 1950
// 3-pool model: fat / carb / protein, protein fraction varies by phase.
// ─────────────────────────────────────────────────────────────────────────
fun BiolismEngine.computeSubstrates(npRQ: Double, ketoHours: Double): SubstrateResult {
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
