package fr.scanneat.domain.model

import kotlin.math.roundToInt

/**
 * Rounds to 1 decimal place. The same `(v * 10).roundToInt() / 10.0` shape was
 * independently reimplemented across DashboardAggregator, PersonalScoreEngine,
 * WeightRepository, and FastingRepository (the latter via Math.round instead
 * of roundToInt) — one shared helper instead of five copies of the same math.
 */
fun Double.roundTo1Decimal(): Double = (this * 10.0).roundToInt() / 10.0
