package fr.scanneat.domain.engine.symptom

import fr.scanneat.domain.engine.scoring.detectAllergens
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Roadmap D4: a guided avoidance trial for one Annex II allergen key the app
 * can detect ("lactose", "gluten"): a baseline window before the start, an
 * avoidance phase, then a reintroduction phase. Durations are the user's
 * choice (defaults 14 + 7 days), not a medical protocol - the screen says to
 * run it with a doctor or dietitian.
 */
data class EliminationTrial(val target: String, val start: LocalDate, val avoidDays: Int = 14, val reintroDays: Int = 7) {
    val reintroStart: LocalDate get() = start.plusDays(avoidDays.toLong())
    val end: LocalDate get() = reintroStart.plusDays(reintroDays.toLong())
}

enum class TrialPhase { AVOIDANCE, REINTRODUCTION, FINISHED }

/** Share of days with at least one symptom, over the days of the window that are already past. */
data class WindowRate(val symptomDays: Int, val days: Int) {
    val percent: Int? get() = if (days == 0) null else symptomDays * 100 / days
}

data class TrialReport(
    val phase: TrialPhase,
    val daysLeftInPhase: Int,
    val baseline: WindowRate,
    val avoidance: WindowRate,
    val reintroduction: WindowRate,
    /** Diary entries logged during avoidance that contain the target - "écarts". */
    val slips: List<String>,
)

fun trialPhase(t: EliminationTrial, today: LocalDate): TrialPhase = when {
    today.isBefore(t.reintroStart) -> TrialPhase.AVOIDANCE
    today.isBefore(t.end) -> TrialPhase.REINTRODUCTION
    else -> TrialPhase.FINISHED
}

private fun rate(symptomDates: Set<LocalDate>, from: LocalDate, toExclusive: LocalDate, today: LocalDate): WindowRate {
    val last = minOf(toExclusive, today.plusDays(1))
    if (!last.isAfter(from)) return WindowRate(0, 0)
    val days = ChronoUnit.DAYS.between(from, last).toInt()
    return WindowRate(symptomDates.count { !it.isBefore(from) && it.isBefore(last) }, days)
}

fun trialReport(t: EliminationTrial, symptomDates: Set<LocalDate>, diary: List<DiaryEntry>, today: LocalDate, lang: String = "fr"): TrialReport {
    val phase = trialPhase(t, today)
    val left = when (phase) {
        TrialPhase.AVOIDANCE -> ChronoUnit.DAYS.between(today, t.reintroStart).toInt()
        TrialPhase.REINTRODUCTION -> ChronoUnit.DAYS.between(today, t.end).toInt()
        TrialPhase.FINISHED -> 0
    }
    val slips = diary.filter { !it.date.isBefore(t.start) && it.date.isBefore(t.reintroStart) && !it.date.isAfter(today) }
        .filter { e ->
            val p = Product(name = e.productName, category = ProductCategory.OTHER, novaClass = NovaClass.PROCESSED,
                ingredients = e.ingredients, nutrition = e.nutrition)
            detectAllergens(p, lang).any { it.key == t.target && !it.isTraceOnly }
        }
        .map { "${it.date} — ${it.productName}" }
    return TrialReport(
        phase = phase, daysLeftInPhase = left,
        baseline = rate(symptomDates, t.start.minusDays(14), t.start, today),
        avoidance = rate(symptomDates, t.start, t.reintroStart, today),
        reintroduction = rate(symptomDates, t.reintroStart, t.end, today),
        slips = slips,
    )
}
