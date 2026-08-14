package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.roundTo1Decimal
import java.time.LocalDate
import kotlin.math.roundToInt

// ============================================================================
// Nutrient-gap suggestions — split out of DashboardAggregator.kt (pure
// structural move, no behavior change). Port of closeTheGap() from
// presenters.js, plus chronicNutrientGaps() (recurring, not just today's,
// deficits over a trailing 7-day window).
// ============================================================================

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

// Suggestion sizing below - empirical/heuristic, no single clinical guideline
// dictates these (unlike e.g. PersonalScoreEngine's EFSA/WHO-sourced
// thresholds). GAP_CLOSURE_SHARE: a suggestion targets closing roughly half
// of the remaining deficit, not all of it - reads as "this genuinely helps,"
// not "eat exactly this amount and you're done." The gram cap is split by
// nutrient class rather than one flat number: protein/calcium are commonly
// dense in foods eaten in large single servings (a chicken breast, a glass of
// milk), so a portion up to 300g is a realistic suggestion; fiber/iron/
// vitamin D/B12 sources that dense (bran, liver, fatty fish) would be an
// unrealistic single portion much past 200g.
private const val GAP_CLOSURE_SHARE = 0.5f
private const val MACRO_GRAMS_CAP = 300
private const val MICRO_GRAMS_CAP = 200

// Nutrients where MORE is better, with [totalsKey, targetKey, label, share, gramsCap]
private val GAP_NUTRIENTS = listOf(
    GapNutrientDef("proteinG",   "proteinGTarget",  "protein",   GAP_CLOSURE_SHARE, MACRO_GRAMS_CAP),
    GapNutrientDef("fiberG",     "fiberGTarget",    "fiber",     GAP_CLOSURE_SHARE, MICRO_GRAMS_CAP),
    GapNutrientDef("ironMg",     "ironMgTarget",    "iron",      GAP_CLOSURE_SHARE, MICRO_GRAMS_CAP),
    GapNutrientDef("calciumMg",  "calciumMgTarget", "calcium",   GAP_CLOSURE_SHARE, MACRO_GRAMS_CAP),
    GapNutrientDef("vitDUg",     "vitDUgTarget",    "vit_d",     GAP_CLOSURE_SHARE, MICRO_GRAMS_CAP),
    GapNutrientDef("b12Ug",      "b12UgTarget",     "b12",       GAP_CLOSURE_SHARE, MICRO_GRAMS_CAP),
)

private data class GapNutrientDef(
    val totalsKey: String,
    val targetKey: String,
    val label: String,
    val share: Float,
    val gramsCap: Int,
)

// User-requested: a suggestion shouldn't repeat a food category already
// logged today (e.g. re-suggesting a sweet snack right after an already-sweet
// meal) - same "prefer novel, fall back to same-category candidates only if
// there aren't enough novel ones" partition PairingsDb.findPairings() already
// uses for FoodGroup, reused here for ProductCategory. OTHER is excluded
// since it's the default/unknown bucket, not a real category match.
private fun eatenCategoriesOf(entries: List<DiaryEntry>): Set<ProductCategory> =
    entries.map { it.category }.filterTo(mutableSetOf()) { it != ProductCategory.OTHER }

private fun <T> List<T>.preferNovelCategory(eatenCategories: Set<ProductCategory>, poolSize: Int, foodOf: (T) -> FoodEntry): List<T> {
    if (eatenCategories.isEmpty()) return this
    val (novel, sameCategory) = partition { foodOf(it).category !in eatenCategories }
    return if (novel.size >= poolSize) novel else novel + sameCategory
}

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
    date: LocalDate = LocalDate.now(),
    // User-requested: avoid suggesting a food from the same category as
    // something already logged today in Repas (e.g. don't re-suggest a
    // sweet snack right after an already-sweet meal). Defaults to empty so
    // existing callers that haven't passed today's entries are unaffected.
    todaysEntries: List<DiaryEntry> = emptyList(),
): List<GapEntry> {
    val out = mutableListOf<GapEntry>()
    val eatenCategories = eatenCategoriesOf(todaysEntries)

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
        val prioritized = ranked.preferNovelCategory(eatenCategories, poolSize = 8) { it.first }

        // Previously always `ranked.take(3)` — the same handful of highest-
        // density foods (e.g. maquereau/saumon/sardine for vitamin D, every
        // single day, for every user with the same deficit) regardless of
        // what the user actually eats, since this ranking has zero rotation.
        // Widening to the top 8 and keeping 6 with a day-seeded shuffle keeps
        // suggestions nutritionally sound (still drawn from the strongest
        // sources for this nutrient, never the weak tail of the list) while
        // rotating day to day instead of freezing on one fixed top-3 forever.
        // Seeded by date + nutrient (not just date) so different nutrients
        // don't all reshuffle in lockstep on the same day. Widened from
        // 6/3 to 8/6 so GapCloserCard's refresh button has a real pool of
        // spare candidates to shuffle through instead of re-showing the same
        // 3 chips every tap.
        val pool = prioritized.take(8)
        val seed = date.toEpochDay() * 31 + def.label.hashCode()
        val chosen = pool.shuffled(kotlin.random.Random(seed)).take(6)

        out += GapEntry(
            nutrient    = def.label,
            deficit     = deficit.roundTo1Decimal(),
            suggestions = chosen.map { (f, g, c) -> GapSuggestion(f.name, g, c) },
        )
    }
    return out
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

    // Same category-avoidance signal as closeTheGap() above - derived from
    // entries actually logged on [end] (today, by default), not the whole
    // 7-day window, since the goal is "don't repeat what I just ate", not
    // "avoid whatever I ate at any point this week".
    val eatenCategories = eatenCategoriesOf(entries.filter { it.date == end })
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
        val prioritized = ranked.preferNovelCategory(eatenCategories, poolSize = 6) { it.first }
        // Same top-6/day-seeded-shuffle rotation as closeTheGap() above, and
        // for the same reason - this recurring (weekly) gap previously showed
        // the identical top-3 foods every time it fired for a given nutrient.
        val pool = prioritized.take(6)
        val seed = end.toEpochDay() * 31 + def.label.hashCode()
        val suggestions = pool.shuffled(kotlin.random.Random(seed)).take(3).map { (food, density) ->
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
