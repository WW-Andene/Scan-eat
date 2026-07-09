package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.DiaryEntry
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================================
// DASHBOARD AGGREGATOR — port of public/core/presenters.js
//
// Pure functions — no I/O, no side effects.
// Ported: weeklyRollup, monthlyRollup, logStreakDays, closeTheGap,
//         weightForecast, weekOverWeekDelta.
// ============================================================================

// ---- Data shapes ----

data class DayBucket(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val satFatG: Double,
    val sugarsG: Double,
    val saltG: Double,
    val count: Int,
)

data class NutrientTotals(
    val kcal: Double      = 0.0,
    val proteinG: Double  = 0.0,
    val carbsG: Double    = 0.0,
    val fatG: Double      = 0.0,
    val satFatG: Double   = 0.0,
    val sugarsG: Double   = 0.0,
    val saltG: Double     = 0.0,
    val ironMg: Double    = 0.0,
    val calciumMg: Double = 0.0,
    val vitDUg: Double    = 0.0,
    val b12Ug: Double     = 0.0,
    val fiberG: Double    = 0.0,
    val count: Int        = 0,
)

data class RollupResult(
    val days: List<DayBucket>,
    val total: NutrientTotals,
    val avg: NutrientTotals,
    val daysLogged: Int,
)

// ============================================================================
// weeklyRollup — 7-day window ending at [end]
// Port of weeklyRollup() from presenters.js
// ============================================================================

fun weeklyRollup(entries: List<DiaryEntry>, end: LocalDate = LocalDate.now()): RollupResult =
    rollup(entries, end, windowDays = 7)

// ============================================================================
// monthlyRollup — 30-day trailing window ending at [end]
// Port of monthlyRollup() from presenters.js
// ============================================================================

fun monthlyRollup(entries: List<DiaryEntry>, end: LocalDate = LocalDate.now()): RollupResult =
    rollup(entries, end, windowDays = 30)

private fun rollup(entries: List<DiaryEntry>, end: LocalDate, windowDays: Int): RollupResult {
    val days = (windowDays - 1 downTo 0).map { i ->
        end.minusDays(i.toLong())
    }

    val byDate = days.associateWith { date ->
        entries.filter { it.date == date }
    }

    val buckets = days.map { date ->
        val dayEntries = byDate[date] ?: emptyList()
        if (dayEntries.isEmpty()) {
            DayBucket(date, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        } else {
            DayBucket(
                date     = date,
                kcal     = dayEntries.sumOf { it.consumed.energyKcal },
                proteinG = dayEntries.sumOf { it.consumed.proteinG },
                carbsG   = dayEntries.sumOf { it.consumed.carbsG },
                fatG     = dayEntries.sumOf { it.consumed.fatG },
                satFatG  = dayEntries.sumOf { it.consumed.saturatedFatG },
                sugarsG  = dayEntries.sumOf { it.consumed.sugarsG },
                saltG    = dayEntries.sumOf { it.consumed.saltG },
                count    = dayEntries.size,
            )
        }
    }

    val daysLogged = buckets.count { it.count > 0 }
    val denom = daysLogged.coerceAtLeast(1).toDouble()

    fun r1(v: Double) = (v * 10).roundToInt() / 10.0

    val total = NutrientTotals(
        kcal     = buckets.sumOf { it.kcal },
        proteinG = buckets.sumOf { it.proteinG },
        carbsG   = buckets.sumOf { it.carbsG },
        fatG     = buckets.sumOf { it.fatG },
        satFatG  = buckets.sumOf { it.satFatG },
        sugarsG  = buckets.sumOf { it.sugarsG },
        saltG    = buckets.sumOf { it.saltG },
        count    = buckets.sumOf { it.count },
    )

    val avg = NutrientTotals(
        kcal     = r1(total.kcal / denom),
        proteinG = r1(total.proteinG / denom),
        carbsG   = r1(total.carbsG / denom),
        fatG     = r1(total.fatG / denom),
        satFatG  = r1(total.satFatG / denom),
        sugarsG  = r1(total.sugarsG / denom),
        saltG    = r1(total.saltG / denom),
    )

    return RollupResult(buckets, total, avg, daysLogged)
}

// ============================================================================
// weekOverWeekDelta — compare this week to the prior week
// Port of weekOverWeekDelta() from presenters.js
// ============================================================================

data class WeekOverWeekDelta(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

fun weekOverWeekDelta(current: RollupResult, prior: RollupResult): WeekOverWeekDelta {
    // Plain subtraction, not a ratio — a zero-guard here isn't protecting against
    // division by zero, it was silently reporting "no change" whenever the prior
    // week had no logged data at all, hiding a real (and often large) jump.
    fun delta(a: Double, b: Double) = ((a - b) * 10).roundToInt() / 10.0
    return WeekOverWeekDelta(
        kcal     = delta(current.avg.kcal,     prior.avg.kcal),
        proteinG = delta(current.avg.proteinG, prior.avg.proteinG),
        carbsG   = delta(current.avg.carbsG,   prior.avg.carbsG),
        fatG     = delta(current.avg.fatG,     prior.avg.fatG),
    )
}

// ============================================================================
// logStreakDays — consecutive days with at least one logged entry (1-day grace)
// Port of logStreakDays() from presenters.js
// ============================================================================

fun logStreakDays(entries: List<DiaryEntry>, today: LocalDate = LocalDate.now()): Int {
    if (entries.isEmpty()) return 0
    val days = entries.map { it.date }.toHashSet()
    var cursor = today
    if (!days.contains(cursor)) {
        cursor = cursor.minusDays(1)
        if (!days.contains(cursor)) return 0
    }
    var streak = 0
    while (days.contains(cursor)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

// ============================================================================
// closeTheGap — nutrient suggestions to close today's deficit
// Port of closeTheGap() from presenters.js
// ============================================================================

data class GapSuggestion(val name: String, val grams: Int, val contribution: Double)
data class GapEntry(
    val nutrient: String,
    val deficit: Double,
    val suggestions: List<GapSuggestion>,
)

// Nutrients where MORE is better, with [totalsKey, targetKey, label, share, gramsCap]
private val GAP_NUTRIENTS = listOf(
    GapNutrientDef("proteinG",   "proteinGTarget",  "protein",   0.5f, 300),
    GapNutrientDef("fiberG",     "fiberGTarget",    "fiber",     0.5f, 200),
    GapNutrientDef("ironMg",     "ironMgTarget",    "iron",      0.5f, 200),
    GapNutrientDef("calciumMg",  "calciumMgTarget", "calcium",   0.5f, 300),
    GapNutrientDef("vitDUg",     "vitDUgTarget",    "vit_d",     0.5f, 200),
    GapNutrientDef("b12Ug",      "b12UgTarget",     "b12",       0.5f, 200),
)

private data class GapNutrientDef(
    val totalsKey: String,
    val targetKey: String,
    val label: String,
    val share: Float,
    val gramsCap: Int,
)

/**
 * Suggest foods from [foodDB] that would close roughly half of each
 * nutritional deficit relative to [targets].
 *
 * [totals] is today's ConsumedNutrition.
 * [targets] is the user's DailyTargets from PersonalScoreEngine.
 * [foodDB] is FOOD_DB + custom foods (as FoodEntry list).
 *
 * Port of closeTheGap() from presenters.js.
 */
fun closeTheGap(
    totals: ConsumedNutrition,
    targets: DailyTargets,
    foodDB: List<FoodEntry>,
): List<GapEntry> {
    val out = mutableListOf<GapEntry>()

    data class NutrientValues(val got: Double, val tgt: Double, val foodDensity: (FoodEntry) -> Double)

    val nutrientMap = mapOf(
        "proteinG"  to NutrientValues(totals.proteinG,  targets.proteinGTarget) { it.proteinG },
        "fiberG"    to NutrientValues(totals.fiberG,    targets.fiberGTarget)   { it.fiberG },
        "ironMg"    to NutrientValues(totals.ironMg,    targets.ironMgTarget)   { 0.0 },
        "calciumMg" to NutrientValues(totals.calciumMg, targets.calciumMgTarget){ 0.0 },
        "vitDUg"    to NutrientValues(totals.vitDUg,    targets.vitDUgTarget)   { 0.0 },
        "b12Ug"     to NutrientValues(totals.b12Ug,     targets.b12UgTarget)    { 0.0 },
    )

    for (def in GAP_NUTRIENTS) {
        val nv = nutrientMap[def.totalsKey] ?: continue
        if (nv.tgt <= 0) continue
        val deficit = nv.tgt - nv.got
        if (deficit <= 0) continue
        val need = deficit * def.share

        val ranked = mutableListOf<Triple<FoodEntry, Int, Double>>() // food, grams, contribution
        for (food in foodDB) {
            val density = nv.foodDensity(food)
            if (density <= 0) continue
            val grams = ((need / density) * 100).roundToInt()
            if (grams <= 0 || grams > def.gramsCap) continue
            val contribution = (density * (grams / 100.0) * 10).roundToInt() / 10.0
            ranked += Triple(food, grams, contribution)
        }
        ranked.sortByDescending { nv.foodDensity(it.first) }
        if (ranked.isEmpty()) continue

        out += GapEntry(
            nutrient    = def.label,
            deficit     = (deficit * 10).roundToInt() / 10.0,
            suggestions = ranked.take(3).map { (f, g, c) -> GapSuggestion(f.name, g, c) },
        )
    }
    return out
}

// ============================================================================
// weightForecast — linear extrapolation to goal weight
// Port of weightForecast() from presenters.js
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

fun weightForecast(currentKg: Double, goalKg: Double, weeklySlopeKg: Double): WeightForecast {
    if (currentKg <= 0 || goalKg <= 0) return WeightForecast.InsufficientData
    val delta = goalKg - currentKg
    if (abs(delta) < 0.05) return WeightForecast.AlreadyReached
    if (weeklySlopeKg == 0.0) return WeightForecast.Flat
    if (delta.compareTo(0.0) != weeklySlopeKg.compareTo(0.0)) return WeightForecast.WrongDirection
    val weeks = abs(delta / weeklySlopeKg)
    val days  = (weeks * 7).roundToInt()
    return WeightForecast.Ok(
        weeks       = (weeks * 10).roundToInt() / 10.0,
        days        = days,
        targetDate  = LocalDate.now().plusDays(days.toLong()),
        kgPerWeek   = weeklySlopeKg,
    )
}

// DailyTargets (from PersonalScoreEngine) now includes all micronutrient targets.
