package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.Profile

/**
 * Daily caffeine budget - EFSA Scientific Opinion on the safety of caffeine
 * (EFSA Journal 2015;13(5):4102): up to 400 mg/day and 200 mg per single dose
 * for healthy adults; 200 mg/day during pregnancy (also the ANSES figure the
 * scoring engine already cites); 3 mg/kg body weight/day for children and
 * adolescents. Caffeine is only counted where the product data carries it, so
 * the total is a floor, not an exact intake.
 */
data class CaffeineBudget(
    val totalMg: Double,
    val limitMg: Double,
    /** Logged items above EFSA's 200 mg single-dose level. */
    val highSingleDoses: List<String>,
    /** Part of [totalMg] estimated from EFSA typical contents, not from product data. */
    val estimatedMg: Double = 0.0,
    /** True when [limitMg] is the user's reduction-plan cap for the day, not the EFSA limit. */
    val fromReductionPlan: Boolean = false,
) {
    val over: Boolean get() = totalMg > limitMg
}

const val CAFFEINE_ADULT_DAILY_MG = 400.0
const val CAFFEINE_PREGNANCY_DAILY_MG = 200.0
const val CAFFEINE_SINGLE_DOSE_MG = 200.0
const val CAFFEINE_MINOR_MG_PER_KG = 3.0

/** Null when the limit can't be set (a minor with no weight entered). */
fun caffeineDailyLimitMg(profile: Profile): Double? = when {
    "pregnancy" in profile.healthConditions -> CAFFEINE_PREGNANCY_DAILY_MG
    profile.ageYears != null && profile.ageYears < 18 ->
        profile.weightKg?.takeIf { it > 0 }?.let { it * CAFFEINE_MINOR_MG_PER_KG }
    else -> CAFFEINE_ADULT_DAILY_MG
}

/**
 * Typical caffeine per 100 g/mL when the product data has none - EFSA
 * "Caffeine" consumer infographic accompanying the 2015 opinion: espresso
 * 80 mg / 60 mL, filter coffee 90 mg / 200 mL, tea 50 mg / 220 mL, cola
 * 40 mg / 355 mL, energy drink 80 mg / 250 mL, dark chocolate 25 mg / 50 g.
 * Order matters: the more specific names first.
 */
// Word boundaries spelled out as Unicode-letter lookarounds: the "(?U)" flag
// (UNICODE_CHARACTER_CLASS) throws on Android, and a plain \b treats "é" as a
// non-word char on the JVM.
private const val WB = "(?<![\\p{L}\\p{N}_])"
private const val WE = "(?![\\p{L}\\p{N}_])"
private val TYPICAL_CAFFEINE_PER_100: List<Pair<Regex, Double>> = listOf(
    Regex("$WB(?:espresso|expresso|ristretto)") to 80.0 / 60 * 100,
    Regex("energy drink|boisson [eé]nergisante|red bull|monster") to 80.0 / 250 * 100,
    Regex("$WB(?:caf[eé]s?$WE|coffee)") to 90.0 / 200 * 100,
    Regex("$WB(?:th[eé]s?|tea)$WE|matcha") to 50.0 / 220 * 100,
    Regex("${WB}cola$WE|coca") to 40.0 / 355 * 100,
    Regex("chocolat noir|dark chocolate") to 25.0 / 50 * 100,
)
private val NO_CAFFEINE = Regex("d[eé]ca|sans caf[eé]ine|caffeine[- ]free|zero caf|infusion|tisane|rooibos|glace au caf[eé]|ice cream")

/** Caffeine for one entry: product data, else a typical-content estimate (flag true), else 0. */
internal fun entryCaffeineMg(e: DiaryEntry): Pair<Double, Boolean> {
    e.nutrition.caffeineMg?.takeIf { it > 0 }?.let { return it * e.portionG / 100.0 to false }
    val name = e.productName.lowercase()
    if (NO_CAFFEINE.containsMatchIn(name)) return 0.0 to false
    val per100 = TYPICAL_CAFFEINE_PER_100.firstOrNull { it.first.containsMatchIn(name) }?.second ?: return 0.0 to false
    return per100 * e.portionG / 100.0 to true
}

/** Null when nothing logged today carries caffeine, or no limit applies. */
fun caffeineBudget(profile: Profile, entries: List<DiaryEntry>, planCapMg: Int? = null): CaffeineBudget? {
    val perEntry = entries.map { it to entryCaffeineMg(it) }
    val total = perEntry.sumOf { it.second.first }
    val efsa = caffeineDailyLimitMg(profile)
    // A reduction plan shows its cap even on a day with nothing logged yet.
    if (total <= 0.0 && planCapMg == null) return null
    val useCap = planCapMg != null && (efsa == null || planCapMg < efsa)
    val limit = if (useCap) planCapMg!!.toDouble() else efsa ?: return null
    return CaffeineBudget(
        fromReductionPlan = useCap,
        totalMg = total, limitMg = limit,
        highSingleDoses = perEntry.filter { it.second.first > CAFFEINE_SINGLE_DOSE_MG }.map { it.first.productName },
        estimatedMg = perEntry.filter { it.second.second }.sumOf { it.second.first },
    )
}
