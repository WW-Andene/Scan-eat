package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.roundTo1Decimal
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
    val fiberG: Double = 0.0,
    val ironMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val vitDUg: Double = 0.0,
    val b12Ug: Double = 0.0,
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
                date      = date,
                kcal      = dayEntries.sumOf { it.consumed.energyKcal },
                proteinG  = dayEntries.sumOf { it.consumed.proteinG },
                carbsG    = dayEntries.sumOf { it.consumed.carbsG },
                fatG      = dayEntries.sumOf { it.consumed.fatG },
                satFatG   = dayEntries.sumOf { it.consumed.saturatedFatG },
                sugarsG   = dayEntries.sumOf { it.consumed.sugarsG },
                saltG     = dayEntries.sumOf { it.consumed.saltG },
                count     = dayEntries.size,
                fiberG    = dayEntries.sumOf { it.consumed.fiberG },
                ironMg    = dayEntries.sumOf { it.consumed.ironMg },
                calciumMg = dayEntries.sumOf { it.consumed.calciumMg },
                vitDUg    = dayEntries.sumOf { it.consumed.vitDUg },
                b12Ug     = dayEntries.sumOf { it.consumed.b12Ug },
            )
        }
    }

    val daysLogged = buckets.count { it.count > 0 }
    val denom = daysLogged.coerceAtLeast(1).toDouble()

    fun r1(v: Double) = v.roundTo1Decimal()

    val total = NutrientTotals(
        kcal      = buckets.sumOf { it.kcal },
        proteinG  = buckets.sumOf { it.proteinG },
        carbsG    = buckets.sumOf { it.carbsG },
        fatG      = buckets.sumOf { it.fatG },
        satFatG   = buckets.sumOf { it.satFatG },
        sugarsG   = buckets.sumOf { it.sugarsG },
        saltG     = buckets.sumOf { it.saltG },
        count     = buckets.sumOf { it.count },
        fiberG    = buckets.sumOf { it.fiberG },
        ironMg    = buckets.sumOf { it.ironMg },
        calciumMg = buckets.sumOf { it.calciumMg },
        vitDUg    = buckets.sumOf { it.vitDUg },
        b12Ug     = buckets.sumOf { it.b12Ug },
    )

    val avg = NutrientTotals(
        kcal      = r1(total.kcal / denom),
        proteinG  = r1(total.proteinG / denom),
        carbsG    = r1(total.carbsG / denom),
        fatG      = r1(total.fatG / denom),
        satFatG   = r1(total.satFatG / denom),
        sugarsG   = r1(total.sugarsG / denom),
        saltG     = r1(total.saltG / denom),
        fiberG    = r1(total.fiberG / denom),
        ironMg    = r1(total.ironMg / denom),
        calciumMg = r1(total.calciumMg / denom),
        vitDUg    = r1(total.vitDUg / denom),
        b12Ug     = r1(total.b12Ug / denom),
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
    fun delta(a: Double, b: Double) = (a - b).roundTo1Decimal()
    return WeekOverWeekDelta(
        kcal     = delta(current.avg.kcal,     prior.avg.kcal),
        proteinG = delta(current.avg.proteinG, prior.avg.proteinG),
        carbsG   = delta(current.avg.carbsG,   prior.avg.carbsG),
        fatG     = delta(current.avg.fatG,     prior.avg.fatG),
    )
}

/**
 * Same delta math as [weekOverWeekDelta], for a 30-day window instead of 7 -
 * MonthlyTrendCard only ever plotted 30 daily bars against a flat target line,
 * with no comparison number to the prior 30-day window the way WeekDeltaCard
 * already has for weeks. Reuses [WeekOverWeekDelta]'s shape (it's just a kcal/
 * protein/carbs/fat delta struct, not actually week-specific) rather than
 * introducing a parallel type for the exact same four fields.
 */
fun monthOverMonthDelta(current: RollupResult, prior: RollupResult): WeekOverWeekDelta {
    fun delta(a: Double, b: Double) = (a - b).roundTo1Decimal()
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
// longestLogStreak — the longest-ever run of consecutive logged days
//
// logStreakDays() above only reports the *current* streak (anchored at
// `today`, with a 1-day grace period). It can't answer "what's my record?"
// once a streak breaks — a user who logged 30 days in a row last month and
// then missed a day sees that achievement disappear entirely. This scans
// the full set of logged dates and returns the longest unbroken run found
// anywhere in the history (no grace period — a genuine gap ends the run).
// ============================================================================

fun longestLogStreak(entries: List<DiaryEntry>): Int {
    if (entries.isEmpty()) return 0
    val days = entries.map { it.date }.toSortedSet()
    var best = 1
    var current = 1
    var prev: LocalDate? = null
    for (day in days) {
        if (prev != null && day == prev.plusDays(1)) {
            current++
        } else {
            current = 1
        }
        if (current > best) best = current
        prev = day
    }
    return best
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
        "ironMg"    to NutrientValues(totals.ironMg,    targets.ironMgTarget)   { it.ironMg },
        "calciumMg" to NutrientValues(totals.calciumMg, targets.calciumMgTarget){ it.calciumMg },
        "vitDUg"    to NutrientValues(totals.vitDUg,    targets.vitDUgTarget)   { it.vitDUg },
        "b12Ug"     to NutrientValues(totals.b12Ug,     targets.b12UgTarget)    { it.b12Ug },
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
            val contribution = (density * (grams / 100.0)).roundTo1Decimal()
            ranked += Triple(food, grams, contribution)
        }
        ranked.sortByDescending { nv.foodDensity(it.first) }
        if (ranked.isEmpty()) continue

        out += GapEntry(
            nutrient    = def.label,
            deficit     = deficit.roundTo1Decimal(),
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

// ============================================================================
// chronicNutrientGaps — recurring (not just today's) nutrient deficits
//
// closeTheGap() above only looks at *today*'s totals: a single unusually good
// or bad day either hides a real ongoing pattern or creates a false alarm.
// This scans the trailing 7-day window (via rollup()) day-by-day and flags
// nutrients the user is falling short on most days they actually logged —
// the thing worth surfacing to a user is "you're low on iron most days",
// not "you were low on iron today because you skipped breakfast".
// Days with no logged entries are excluded from both the numerator and
// denominator so a day the user simply forgot to log doesn't count as a
// "deficient" day. Requires at least 3 logged days in the window before
// reporting anything, so a near-empty week doesn't produce a spurious trend.
// ============================================================================

data class ChronicGap(
    val nutrient: String,
    val daysBelowTarget: Int,
    val daysLogged: Int,
    val avgPctOfTarget: Int,
    val suggestions: List<GapSuggestion>,
)

fun chronicNutrientGaps(
    entries: List<DiaryEntry>,
    targets: DailyTargets,
    foodDB: List<FoodEntry>,
    end: LocalDate = LocalDate.now(),
    minLoggedDays: Int = 3,
    deficitThreshold: Double = 0.85,
): List<ChronicGap> {
    val week = rollup(entries, end, windowDays = 7)
    val loggedDays = week.days.filter { it.count > 0 }
    if (loggedDays.size < minLoggedDays) return emptyList()

    data class Def(val label: String, val target: Double, val bucketValue: (DayBucket) -> Double, val foodDensity: (FoodEntry) -> Double)

    val defs = listOf(
        Def("protein",  targets.proteinGTarget,  { it.proteinG })   { it.proteinG },
        Def("fiber",    targets.fiberGTarget,     { it.fiberG })     { it.fiberG },
        Def("iron",     targets.ironMgTarget,     { it.ironMg })     { it.ironMg },
        Def("calcium",  targets.calciumMgTarget,  { it.calciumMg })  { it.calciumMg },
        Def("vit_d",    targets.vitDUgTarget,     { it.vitDUg })     { it.vitDUg },
        Def("b12",      targets.b12UgTarget,      { it.b12Ug })      { it.b12Ug },
    )

    val out = mutableListOf<ChronicGap>()
    for (def in defs) {
        if (def.target <= 0) continue
        val threshold = def.target * deficitThreshold
        val values = loggedDays.map { def.bucketValue(it) }
        val daysBelow = values.count { it < threshold }
        // Majority of logged days below the threshold — a recurring pattern,
        // not an isolated bad day.
        if (daysBelow * 2 <= loggedDays.size) continue

        val avgValue = values.average()
        val avgPct = ((avgValue / def.target) * 100).roundToInt()
        val avgDeficit = (def.target - avgValue).coerceAtLeast(0.0)

        val ranked = mutableListOf<Pair<FoodEntry, Double>>() // food, contribution
        for (food in foodDB) {
            val density = def.foodDensity(food)
            if (density <= 0) continue
            ranked += food to density
        }
        ranked.sortByDescending { it.second }
        val suggestions = ranked.take(3).map { (food, density) ->
            val grams = ((avgDeficit * 0.5 / density) * 100).roundToInt().coerceAtLeast(1)
            val contribution = (density * (grams / 100.0)).roundTo1Decimal()
            GapSuggestion(food.name, grams, contribution)
        }

        out += ChronicGap(
            nutrient        = def.label,
            daysBelowTarget = daysBelow,
            daysLogged      = loggedDays.size,
            avgPctOfTarget  = avgPct,
            suggestions     = suggestions,
        )
    }
    return out
}

// ============================================================================
// weeklyCrossTrackerInsight — checks whether this week's calorie deficit/
// surplus actually agrees with the real weight-trend direction.
//
// Every function above reports on exactly one metric in isolation
// (weeklyRollup on intake, weightForecast on the scale). Five trackers
// (nutrition, weight, activity, hydration, fasting) never cross-reference
// each other anywhere in the app, so a user has no way to see "am I actually
// eating the deficit I think I am, and does the scale agree?" without doing
// the arithmetic themselves. Requires at least [minLoggedDays] logged diary
// days this week (a near-empty week's average is too noisy to compare
// against anything) and a real weight-trend regression (WeightRepository
// already returns 0.0 when it has fewer than 2 points).
// ============================================================================

enum class InsightAgreement { CONSISTENT, MISMATCH, INCONCLUSIVE }

sealed class CrossTrackerInsight {
    data object InsufficientData : CrossTrackerInsight()
    data class WeightVsIntake(
        val avgDailyDeficitKcal: Int,   // positive = under target (deficit), negative = surplus
        val weightTrendKgPerWeek: Double,
        val weeklyActiveMinutes: Int,
        val agreement: InsightAgreement,
        // Fasting and hydration were tracked but never cross-referenced anywhere
        // (see this section's own doc comment: "five trackers... never cross-
        // reference each other"). Both optional/percent-of-goal rather than
        // folded into [agreement] - a genuine "does eating-under-target,
        // fasting adherence, AND hydration all agree" verdict needs its own
        // reasoned rules, not a guess bolted onto the existing weight-vs-intake
        // one. Null means the user isn't tracking that one (no history/no goal),
        // not "0% adherence".
        val weeklyFastingAdherencePct: Int? = null,
        val weeklyHydrationAdherencePct: Int? = null,
    ) : CrossTrackerInsight()
}

// Below these, a normal day-to-day noise floor (scale water-weight swings,
// a single indulgent meal) is common enough that calling it a real signal
// either way would be misleading rather than insightful.
private const val DEFICIT_NOISE_FLOOR_KCAL = 50
private const val WEIGHT_TREND_NOISE_FLOOR_KG = 0.05

fun weeklyCrossTrackerInsight(
    weeklyAvgKcal: Double,
    kcalTarget: Double,
    daysLogged: Int,
    weightTrendKgPerWeek: Double?,
    weeklyActiveMinutes: Int,
    minLoggedDays: Int = 4,
    weeklyFastingAdherencePct: Int? = null,
    weeklyHydrationAdherencePct: Int? = null,
): CrossTrackerInsight {
    if (daysLogged < minLoggedDays || weightTrendKgPerWeek == null || kcalTarget <= 0) return CrossTrackerInsight.InsufficientData
    val avgDeficit = (kcalTarget - weeklyAvgKcal).roundToInt()
    val realDeficit = abs(avgDeficit) > DEFICIT_NOISE_FLOOR_KCAL
    val realTrend = abs(weightTrendKgPerWeek) > WEIGHT_TREND_NOISE_FLOOR_KG
    val agreement = when {
        !realDeficit || !realTrend -> InsightAgreement.INCONCLUSIVE
        avgDeficit > 0 && weightTrendKgPerWeek < 0 -> InsightAgreement.CONSISTENT // eating under target, scale trending down
        avgDeficit < 0 && weightTrendKgPerWeek > 0 -> InsightAgreement.CONSISTENT // eating over target, scale trending up
        else -> InsightAgreement.MISMATCH
    }
    return CrossTrackerInsight.WeightVsIntake(
        avgDeficit, weightTrendKgPerWeek, weeklyActiveMinutes, agreement,
        weeklyFastingAdherencePct, weeklyHydrationAdherencePct,
    )
}

// DailyTargets (from PersonalScoreEngine) now includes all micronutrient targets.
