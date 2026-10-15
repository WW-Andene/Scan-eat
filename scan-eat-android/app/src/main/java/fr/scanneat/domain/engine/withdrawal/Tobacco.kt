package fr.scanneat.domain.engine.withdrawal

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Tobacco: quit on a date, or cut down to a daily target. The user logs the
 * cigarettes actually smoked; nothing here gives nicotine-replacement doses -
 * the screen sends that to a pharmacist, doctor or Tabac Info Service (39 89).
 */
data class TobaccoPlan(
    val startDate: LocalDate,
    /** Cigarettes a day before the plan - the base for "avoided" and "saved". */
    val baselinePerDay: Int,
    val packPriceEuros: Double,
    val cigarettesPerPack: Int = 20,
    /** Null = full quit from [startDate]; otherwise the daily cut-down target. */
    val targetPerDay: Int? = null,
)

data class TobaccoStats(
    val smokeFreeDays: Int,
    val cigarettesAvoided: Int,
    val moneySavedEuros: Double,
    val todayCount: Int,
    val todayOverTarget: Boolean,
    /** Last cigarette (or start of the plan), the clock for [milestones]. */
    val lastSmoke: LocalDateTime,
)

/** [log]: cigarettes smoked per day. Days before [TobaccoPlan.startDate] are ignored. */
fun tobaccoStats(plan: TobaccoPlan, log: Map<LocalDate, Int>, now: LocalDateTime, lastSmokeAt: LocalDateTime?): TobaccoStats {
    val today = now.toLocalDate()
    val days = if (today.isBefore(plan.startDate)) emptyList()
        else (0..ChronoUnit.DAYS.between(plan.startDate, today)).map { plan.startDate.plusDays(it) }
    val avoided = days.sumOf { (plan.baselinePerDay - (log[it] ?: 0)).coerceAtLeast(0) }
    // Consecutive days at zero, ending yesterday (today still counts once over).
    var streak = 0
    var d = today.minusDays(1)
    while (!d.isBefore(plan.startDate) && (log[d] ?: 0) == 0) { streak++; d = d.minusDays(1) }
    if ((log[today] ?: 0) == 0 && !today.isBefore(plan.startDate)) streak++
    val todayCount = log[today] ?: 0
    val target = plan.targetPerDay ?: 0
    val start = plan.startDate.atStartOfDay()
    return TobaccoStats(
        smokeFreeDays = streak,
        cigarettesAvoided = avoided,
        moneySavedEuros = if (plan.cigarettesPerPack > 0) avoided.toDouble() / plan.cigarettesPerPack * plan.packPriceEuros else 0.0,
        todayCount = todayCount,
        todayOverTarget = todayCount > target,
        lastSmoke = listOfNotNull(lastSmokeAt, start).max(),
    )
}

/**
 * WHO, "Tobacco: health benefits of smoking cessation" (fact sheet, also used
 * by the WHO Regional Office for Europe). Times are since the last cigarette.
 */
enum class QuitMilestone(val after: Duration) {
    HEART_RATE(Duration.ofMinutes(20)),          // heart rate and blood pressure drop
    CARBON_MONOXIDE(Duration.ofHours(12)),       // blood CO back to normal
    CIRCULATION(Duration.ofDays(14)),            // 2-12 weeks: circulation and lung function improve
    COUGH(Duration.ofDays(30)),                  // 1-9 months: coughing and shortness of breath decrease
    HEART_RISK_HALF(Duration.ofDays(365)),       // 1 year: coronary heart disease risk about half a smoker's
    STROKE(Duration.ofDays(5 * 365L)),           // 5-15 years: stroke risk as a non-smoker's
    LUNG_CANCER_HALF(Duration.ofDays(10 * 365L)),// 10 years: lung cancer death rate about half
    HEART_RISK_NORMAL(Duration.ofDays(15 * 365L)),// 15 years: coronary risk as a non-smoker's
}

fun reachedMilestones(lastSmoke: LocalDateTime, now: LocalDateTime): List<QuitMilestone> {
    val since = Duration.between(lastSmoke, now)
    return QuitMilestone.entries.filter { !since.minus(it.after).isNegative }
}

fun nextMilestone(lastSmoke: LocalDateTime, now: LocalDateTime): QuitMilestone? {
    val since = Duration.between(lastSmoke, now)
    return QuitMilestone.entries.firstOrNull { since.minus(it.after).isNegative }
}
