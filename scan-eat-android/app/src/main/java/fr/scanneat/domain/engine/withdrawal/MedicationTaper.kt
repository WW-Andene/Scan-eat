package fr.scanneat.domain.engine.withdrawal

import java.time.LocalDate

/**
 * A dose-reduction plan PRESCRIBED by the user's doctor, entered by the user:
 * the app stores and reminds, it never proposes or changes a dose. HAS (2015,
 * "Arrêt des benzodiazépines et médicaments apparentés"): stopping is gradual,
 * over weeks to months, with medical follow-up - never abrupt.
 */
data class TaperStep(val startDate: LocalDate, val dose: String)

data class TaperPlan(val medicationName: String, val steps: List<TaperStep>, val note: String = "") {
    val sortedSteps: List<TaperStep> get() = steps.sortedBy { it.startDate }
}

data class TaperStatus(val current: TaperStep?, val next: TaperStep?, val stepIndex: Int, val stepCount: Int)

fun taperStatus(plan: TaperPlan, today: LocalDate): TaperStatus {
    val s = plan.sortedSteps
    val idx = s.indexOfLast { !it.startDate.isAfter(today) }
    return TaperStatus(current = s.getOrNull(idx), next = s.getOrNull(idx + 1), stepIndex = idx, stepCount = s.size)
}

/** True when a new step starts on [today] - the day to remind the user. */
fun taperStepStartsOn(plan: TaperPlan, today: LocalDate): TaperStep? = plan.steps.firstOrNull { it.startDate == today }
