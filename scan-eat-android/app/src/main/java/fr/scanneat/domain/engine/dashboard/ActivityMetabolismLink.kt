package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.model.ActivityLevel

/**
 * User-requested: "are logged activities properly connected to metabolism?"
 * Answer was no - the Biolism/PAL TDEE computation used only the profile's
 * declared [ActivityLevel], and logged Activity kcal was purely informational
 * (see CalorieBalance.exerciseKcal's own prior doc comment for why: adding
 * logged kcal on top of an already-active PAL risked double-counting the same
 * exercise twice, once via the declared lifestyle level and once via the
 * actual session).
 *
 * User's chosen fix: add only the EXCESS logged kcal beyond what the declared
 * activity level already implies for a typical day. A "very active" user's
 * normal hard workout contributes nothing extra to today's budget (it's
 * already priced into their TDEE) - but an exceptional day (a long race, a
 * rest-day user who went for an unusually hard run) still gets credited.
 *
 * These per-tier values are a rough, deliberately conservative daily-average
 * estimate of "purposeful exercise kcal" implied by each FAO/WHO/UNU 2004 PAL
 * tier (see [ActivityLevel]'s own doc comment for the PAL multipliers this
 * mirrors) - not a measured quantity, since PAL itself doesn't decompose into
 * "how much of this was specifically exercise" on its own. Deliberately on
 * the low side so this only ever adds credit for genuinely exceptional
 * volume, never quietly shrinks it for an ordinary day.
 */
private val EXPECTED_EXERCISE_KCAL_BY_LEVEL: Map<ActivityLevel, Int> = mapOf(
    ActivityLevel.SEDENTARY to 0,
    ActivityLevel.LIGHTLY_ACTIVE to 100,
    ActivityLevel.MODERATELY_ACTIVE to 250,
    ActivityLevel.VERY_ACTIVE to 400,
    ActivityLevel.EXTRA_ACTIVE to 600,
)

/** Never negative - a day with less exercise than the declared level implies
 *  isn't penalized here (that's exactly what the PAL multiplier already
 *  accounts for on average), it just contributes nothing extra. */
fun extraExerciseKcal(activityLevel: ActivityLevel, loggedExerciseKcal: Int): Int =
    (loggedExerciseKcal - (EXPECTED_EXERCISE_KCAL_BY_LEVEL[activityLevel] ?: 0)).coerceAtLeast(0)
