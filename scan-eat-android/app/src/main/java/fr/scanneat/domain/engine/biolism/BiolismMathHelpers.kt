package fr.scanneat.domain.engine.biolism

import kotlin.math.pow
import kotlin.math.roundToLong

// Shared across BiolismEngine's per-concern files (MetabolicsCalculator.kt,
// OrganHeatDistribution.kt). The original file also declared file-private
// Double.pow(Double)/Double.pow(Int) shadows of the kotlin.math stdlib
// extensions of the same signature — those were redundant (identical
// Math.pow delegation) and are dropped here; kotlin.math.pow is used
// directly via the wildcard import in each file that needs it.
internal fun Double.roundTo(dp: Int): Double {
    val factor = 10.0.pow(dp)
    return (this * factor).roundToLong() / factor
}
