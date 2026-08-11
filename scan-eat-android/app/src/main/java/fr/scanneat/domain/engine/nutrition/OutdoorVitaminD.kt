package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ConsumedNutrition

/**
 * Rough per-day vitamin D credit for a logged outdoor activity (see
 * ActivityEntity.wasOutdoors) - deliberately conservative, roughly a third of
 * the EFSA adult DRV (15µg/day), reflecting typical casual midday sun
 * exposure rather than a targeted "vitamin D sunbathing" session.
 *
 * Shared between Dashboard (DashboardHeavyState) and the Diary's own macro
 * summary (DiaryViewModel.summary) - previously only the former applied it,
 * so an outdoor activity's vitD credit showed on Dashboard but not on the
 * exact same day's Journal totals, and neither actually reacted to a new
 * activity log (both read ActivityRepository as a one-shot fetch instead of
 * a combined Flow input).
 */
const val VITD_OUTDOOR_UG = 5.0

/** Flat per-day estimate (not scaled by minutes or activity count - see
 *  [VITD_OUTDOOR_UG]'s own doc comment) applied once regardless of how many
 *  outdoor activities were logged that day. */
fun ConsumedNutrition.withOutdoorVitD(hadOutdoorActivity: Boolean): ConsumedNutrition =
    if (hadOutdoorActivity) copy(vitDUg = vitDUg + VITD_OUTDOOR_UG) else this
