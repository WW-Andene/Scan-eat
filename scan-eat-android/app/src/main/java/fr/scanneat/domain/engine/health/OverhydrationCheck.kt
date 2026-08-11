package fr.scanneat.domain.engine.health

/**
 * User-requested: "can the app detect a real water surplus (e.g. 10L/day)
 * and warn about it?" - genuine gap, nothing existed anywhere in the
 * hydration feature to flag over-consumption, only under-consumption
 * (goal/streak/adherence). Excess water intake can outpace the kidneys' max
 * excretion rate (roughly 800-1000 mL/hour sustained), diluting blood sodium
 * and risking hyponatremia ("water intoxication") - a real, if uncommon,
 * acute risk, unlike most cautions elsewhere in this app which are chronic
 * (long-term dietary patterns) rather than same-day.
 *
 * Thresholds are relative to the user's own goal (a proportionally large
 * excess is unusual regardless of the exact goal number - 2-3x anyone's
 * daily need is well past normal) with an absolute floor, since a very low
 * or default-profile goal shouldn't make a moderate excess look enormous in
 * purely relative terms.
 */
private const val MODERATE_FLOOR_ML = 5000
private const val HIGH_FLOOR_ML = 8000

enum class OverhydrationSeverity { MODERATE, HIGH }

data class OverhydrationWarning(val totalMl: Int, val severity: OverhydrationSeverity)

/** Non-blocking - like every other caution in this app, this flags, it never
 *  prevents logging another glass. */
fun checkOverhydration(totalMlToday: Int, goalMl: Int): OverhydrationWarning? {
    val moderateThreshold = maxOf(goalMl * 2, MODERATE_FLOOR_ML)
    val highThreshold = maxOf(goalMl * 3, HIGH_FLOOR_ML)
    val severity = when {
        totalMlToday >= highThreshold     -> OverhydrationSeverity.HIGH
        totalMlToday >= moderateThreshold -> OverhydrationSeverity.MODERATE
        else -> return null
    }
    return OverhydrationWarning(totalMlToday, severity)
}
