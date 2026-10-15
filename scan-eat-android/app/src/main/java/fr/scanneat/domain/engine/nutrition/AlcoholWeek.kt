package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.Profile
import java.time.LocalDate

/**
 * Weekly alcohol against the French low-risk guidelines (Santé publique France
 * / INCa expert opinion, 2017): at most 10 standard drinks a week, at most 2 a
 * day, and not every day; none during pregnancy. A standard drink is 10 g of
 * pure alcohol. Grams = volume (mL, portion grams taken as mL) × %vol / 100 ×
 * 0.8 (ethanol density, ~0.79 g/mL).
 */
data class AlcoholWeek(
    val standardDrinks: Double,
    val daysWithAlcohol: Int,
    val daysOverTwo: Int,
    /** Alcoholic drinks logged without a %vol, not counted in [standardDrinks]. */
    val unknownStrength: Int,
    val pregnancy: Boolean,
) {
    val overWeekly: Boolean get() = if (pregnancy) standardDrinks > 0 else standardDrinks > ALCOHOL_WEEKLY_MAX
}

const val ALCOHOL_WEEKLY_MAX = 10.0
const val ALCOHOL_DAILY_MAX = 2.0
const val STANDARD_DRINK_G = 10.0
private const val ETHANOL_DENSITY = 0.8

internal fun standardDrinks(e: DiaryEntry): Double? {
    val vol = e.nutrition.alcoholPercentVol ?: return null
    return e.portionG * vol / 100.0 * ETHANOL_DENSITY / STANDARD_DRINK_G
}

/** Null when nothing alcoholic was logged in the 7 days ending [endDate]. */
fun alcoholWeek(profile: Profile, entries: List<DiaryEntry>, endDate: LocalDate): AlcoholWeek? {
    val week = entries.filter { !it.date.isAfter(endDate) && it.date.isAfter(endDate.minusDays(7)) }
    val alcoholic = week.filter { (it.nutrition.alcoholPercentVol ?: 0.0) > 0.5 || (it.category == ProductCategory.ALCOHOLIC_BEVERAGE && it.nutrition.alcoholPercentVol == null) }
    if (alcoholic.isEmpty()) return null
    val perDay = alcoholic.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { standardDrinks(it) ?: 0.0 } }
    return AlcoholWeek(
        standardDrinks = perDay.values.sum(),
        daysWithAlcohol = perDay.size,
        daysOverTwo = perDay.values.count { it > ALCOHOL_DAILY_MAX },
        unknownStrength = alcoholic.count { it.nutrition.alcoholPercentVol == null },
        pregnancy = "pregnancy" in profile.healthConditions,
    )
}
