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

/** Null when nothing logged today carries caffeine, or no limit applies. */
fun caffeineBudget(profile: Profile, entries: List<DiaryEntry>): CaffeineBudget? {
    val total = entries.sumOf { it.consumed.caffeineMg }
    if (total <= 0.0) return null
    val limit = caffeineDailyLimitMg(profile) ?: return null
    return CaffeineBudget(total, limit, entries.filter { it.consumed.caffeineMg > CAFFEINE_SINGLE_DOSE_MG }.map { it.productName })
}
