package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.model.roundTo1Decimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================================
// weightForecast — linear extrapolation to goal weight
// Port of weightForecast() from presenters.js
//
// Split out of DashboardAggregator.kt (pure structural move, no behavior
// change).
// ============================================================================

sealed class WeightForecast {
    data object InsufficientData : WeightForecast()
    data object Flat              : WeightForecast()
    data object WrongDirection    : WeightForecast()
    data object AlreadyReached    : WeightForecast()
    data class  Ok(
        val weeks: Double,
        val days: Int,
        val targetDate: LocalDate,
        val kgPerWeek: Double,
    ) : WeightForecast()
}

// Below any home scale's noise floor — a regression slope over real
// (noisy) weigh-ins is essentially never exactly 0.0, so checking only
// for that exact value let a near-flat trend (e.g. 5g/week) produce a
// multi-decade "forecast" instead of being recognized as no real trend.
private const val FLAT_SLOPE_THRESHOLD_KG_PER_WEEK = 0.02
private const val MAX_FORECAST_WEEKS = 104.0

fun weightForecast(currentKg: Double, goalKg: Double, weeklySlopeKg: Double): WeightForecast {
    if (currentKg <= 0 || goalKg <= 0) return WeightForecast.InsufficientData
    val delta = goalKg - currentKg
    if (abs(delta) < 0.05) return WeightForecast.AlreadyReached
    if (abs(weeklySlopeKg) < FLAT_SLOPE_THRESHOLD_KG_PER_WEEK) return WeightForecast.Flat
    if (delta.compareTo(0.0) != weeklySlopeKg.compareTo(0.0)) return WeightForecast.WrongDirection
    val weeks = abs(delta / weeklySlopeKg)
    if (weeks > MAX_FORECAST_WEEKS) return WeightForecast.Flat
    val days  = (weeks * 7).roundToInt()
    return WeightForecast.Ok(
        weeks       = weeks.roundTo1Decimal(),
        days        = days,
        targetDate  = LocalDate.now().plusDays(days.toLong()),
        kgPerWeek   = weeklySlopeKg,
    )
}
