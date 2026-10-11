package fr.scanneat.domain.engine.biolism

import kotlin.math.exp
import kotlin.math.min

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
fun BiolismEngine.computeKetoRQ(ketoHours: Double, ketoAdapted: Boolean): Double {
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
