package fr.scanneat.domain.engine.biolism

import kotlin.math.min

// DataViewModel's session-cumulative totals and TrackerViewModel's live metabolic
// snapshot each independently duplicated this exact formula (glycogen depletion
// caps at GLYCOGEN_KCAL, then blends fat's heat-of-combustion from 7700 kcal/kg
// toward 9300 kcal/kg as glycogen depletes) before this extraction — kept in one
// place so a future formula tweak can't silently diverge between the two tabs.
data class GlycogenFatBurn(
    val glycogenDepletedKcal: Double,
    val glycogenFraction: Double,
    val kcalPerKgFat: Double,
)

fun BiolismEngine.computeGlycogenFatBurn(kcalTotal: Double, carbFrac: Double, ketosisOn: Boolean): GlycogenFatBurn {
    val glycogenDepletedKcal = if (ketosisOn) min(kcalTotal * carbFrac, GLYCOGEN_KCAL) else 0.0
    val glycogenFraction = if (ketosisOn) min(1.0, glycogenDepletedKcal / GLYCOGEN_KCAL) else 0.0
    val kcalPerKgFat = if (ketosisOn) 7700.0 + (9300.0 - 7700.0) * glycogenFraction else 7700.0
    return GlycogenFatBurn(glycogenDepletedKcal, glycogenFraction, kcalPerKgFat)
}
