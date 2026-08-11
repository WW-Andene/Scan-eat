package fr.scanneat.domain.engine.health

import fr.scanneat.data.repository.health.ActivityType

/**
 * User-requested: the app previously had zero awareness of excessive single-day
 * training volume - AddActivityDialog only validated minutes as a bare 1..1440
 * range (any duration up to 24h/day accepted with no feedback at all), and
 * nothing else in the codebase looked at cumulative daily minutes per activity
 * type. This is a coarse, non-blocking heuristic (never prevents logging, same
 * spirit as Result's own cautions/vetoes never blocking a scan from being
 * saved) meant to flag genuinely unusual volume - a single hard session isn't
 * flagged, but e.g. 6h of running or 3h of continuous strength work in one day
 * is well past what most training guidance considers a normal single-day
 * volume for that modality and carries real overuse/injury/overtraining risk.
 *
 * Per-type daily minute thresholds - deliberately generous (well above what a
 * dedicated athlete's hard day looks like) so this never nags a normal heavy
 * training day, only a volume that's actually exceptional. HIGH is 2x the
 * MODERATE threshold.
 */
private val DAILY_MODERATE_MINUTES: Map<ActivityType, Int> = mapOf(
    ActivityType.WALKING_BRISK to 240,
    ActivityType.RUNNING to 120,
    ActivityType.CYCLING to 240,
    ActivityType.SWIMMING to 150,
    ActivityType.STRENGTH to 120,
    ActivityType.YOGA to 240,
    ActivityType.HIIT to 60,
    ActivityType.OTHER to 180,
)

enum class OvertrainingSeverity { MODERATE, HIGH }

data class OvertrainingWarning(val type: ActivityType, val totalMinutes: Int, val severity: OvertrainingSeverity)

/** [totalMinutesToday] is the type's own cumulative minutes for the day
 *  (already-logged entries of this same type plus the one being added/edited),
 *  not the day's grand total across all activity types - a runner who also did
 *  20min of yoga shouldn't have the yoga session flagged as contributing to
 *  running's threshold. */
fun checkDailyOvertraining(type: ActivityType, totalMinutesToday: Int): OvertrainingWarning? {
    val moderate = DAILY_MODERATE_MINUTES[type] ?: return null
    val severity = when {
        totalMinutesToday >= moderate * 2 -> OvertrainingSeverity.HIGH
        totalMinutesToday >= moderate     -> OvertrainingSeverity.MODERATE
        else -> return null
    }
    return OvertrainingWarning(type, totalMinutesToday, severity)
}
