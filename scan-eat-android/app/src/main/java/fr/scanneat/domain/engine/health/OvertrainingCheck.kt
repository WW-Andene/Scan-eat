package fr.scanneat.domain.engine.health

import fr.scanneat.data.repository.health.ActivityType
import kotlin.math.roundToInt

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
 * MODERATE threshold. These are the *baseline* (unadjusted) thresholds - see
 * [checkDailyOvertraining]'s own doc comment for how age/health conditions
 * tighten them.
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

/** User-requested: is 65 treated as a hard "elderly" cutoff anywhere else in
 *  this app? No - there's no elderly-specific entry in Profile.healthConditions
 *  (see DietAndConditionAdjustments' condition-key list), so this thresholds
 *  directly on the plain ageYears field, same as the rest of this app would
 *  have to. 65 mirrors the WHO/most national guidelines' common "older adult"
 *  cutoff for exercise-prescription caution. */
private const val ELDERLY_AGE_THRESHOLD = 65

/** healthConditions key -> reduced tolerance for sustained high-volume/high-
 *  intensity output, mirroring the exact condition keys already tracked in
 *  Profile.healthConditions (see DietAndConditionAdjustments.kt). Hypertension
 *  and cardiac strain from sustained high-intensity effort are the direct
 *  concern here; the other Profile conditions (IBS, migraine, epilepsy...)
 *  aren't volume-relevant and are deliberately left out. */
private val CARDIAC_RISK_CONDITIONS = setOf("hypertension")

private val HIGH_IMPACT_TYPES = setOf(ActivityType.RUNNING, ActivityType.HIIT)

enum class OvertrainingSeverity { MODERATE, HIGH }

/**
 * Distinct, mechanism-specific risks a flagged session may carry - a generic
 * "overtraining risk" string doesn't tell a user *why* it matters or what to
 * actually do about it. Every warning always carries at least
 * [OVERUSE_INJURY] (the baseline mechanical-strain risk any high volume
 * carries); the others are added only when the specific person/session
 * combination makes them relevant.
 */
enum class ActivityRiskType {
    /** Tendon/joint/muscle strain from sustained mechanical load - the
     *  baseline risk any flagged volume carries, regardless of who's doing it. */
    OVERUSE_INJURY,
    /** Sustained high cardiac output - elevated for an older user (see
     *  [ELDERLY_AGE_THRESHOLD]) or one with a condition in [CARDIAC_RISK_CONDITIONS]. */
    CARDIAC_STRAIN,
    /** Prolonged high-impact/high-intensity effort without described fluid intake -
     *  this app has no hydration-during-exercise tracking, so this is a coarse
     *  "long/hard session" flag, not a measured deficit. */
    DEHYDRATION,
    /** Prolonged exercise depletes glycogen and can trigger delayed
     *  hypoglycemia in a user managing diabetes - flagged whenever "diabetes"
     *  is in the profile's health conditions, regardless of activity type. */
    HYPOGLYCEMIA,
}

data class OvertrainingWarning(
    val type: ActivityType,
    val totalMinutes: Int,
    val severity: OvertrainingSeverity,
    val riskTypes: Set<ActivityRiskType>,
)

/**
 * [totalMinutesToday] is the type's own cumulative minutes for the day
 * (already-logged entries of this same type plus the one being added/edited),
 * not the day's grand total across all activity types - a runner who also did
 * 20min of yoga shouldn't have the yoga session flagged as contributing to
 * running's threshold.
 *
 * [ageYears]/[healthConditions] personalize the threshold itself (not just
 * the message): an older user or one with a cardiac-relevant condition has
 * measurably lower tolerance for sustained high-output volume before the same
 * duration becomes a real strain risk, so their threshold is tightened by 25%
 * rather than only relabeling an identical cutoff with a scarier message.
 */
fun checkDailyOvertraining(
    type: ActivityType,
    totalMinutesToday: Int,
    ageYears: Int? = null,
    healthConditions: Set<String> = emptySet(),
): OvertrainingWarning? {
    val baseline = DAILY_MODERATE_MINUTES[type] ?: return null
    val isElderly = (ageYears ?: 0) >= ELDERLY_AGE_THRESHOLD
    val hasCardiacRiskCondition = healthConditions.any { it in CARDIAC_RISK_CONDITIONS }
    val moderate = if (isElderly || hasCardiacRiskCondition) (baseline * 0.75).roundToInt() else baseline
    val severity = when {
        totalMinutesToday >= moderate * 2 -> OvertrainingSeverity.HIGH
        totalMinutesToday >= moderate     -> OvertrainingSeverity.MODERATE
        else -> return null
    }
    val riskTypes = buildSet {
        add(ActivityRiskType.OVERUSE_INJURY)
        if (isElderly || hasCardiacRiskCondition) add(ActivityRiskType.CARDIAC_STRAIN)
        if (type in HIGH_IMPACT_TYPES || totalMinutesToday >= moderate * 1.5) add(ActivityRiskType.DEHYDRATION)
        if ("diabetes" in healthConditions) add(ActivityRiskType.HYPOGLYCEMIA)
    }
    return OvertrainingWarning(type, totalMinutesToday, severity, riskTypes)
}
