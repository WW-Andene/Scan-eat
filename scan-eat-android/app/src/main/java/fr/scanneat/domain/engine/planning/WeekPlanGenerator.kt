package fr.scanneat.domain.engine.planning

import fr.scanneat.domain.engine.scoring.normalizeForMatching
import java.time.LocalDate

/** A saved recipe the profile may eat (already filtered for allergens/diet by the caller). */
data class PlanCandidate(val id: String, val name: String, val ingredientNames: List<String>)

/**
 * Roadmap D8: fills the EMPTY [meals] slots of [days] with the user's own
 * eligible recipes. Recipes using an ingredient that expires soon in the
 * pantry go first (D2); otherwise each recipe is used once before any repeats,
 * and the same recipe never fills two meals of the same day.
 * Returns (date, meal) -> candidate for the slots it filled.
 */
fun generateWeekPlan(
    candidates: List<PlanCandidate>,
    days: List<LocalDate>,
    meals: List<String>,
    occupied: Set<Pair<LocalDate, String>>,
    expiringSoon: Set<String>,
): Map<Pair<LocalDate, String>, PlanCandidate> {
    if (candidates.isEmpty()) return emptyMap()
    val expiringKeys = expiringSoon.map { normalizeForMatching(it) }.filter { it.isNotBlank() }
    fun usesExpiring(c: PlanCandidate) = c.ingredientNames.any { ing ->
        val n = normalizeForMatching(ing); expiringKeys.any { k -> n.contains(k) || k.contains(n) }
    }
    val ordered = candidates.sortedWith(compareByDescending<PlanCandidate> { usesExpiring(it) }.thenBy { it.name })
    val result = LinkedHashMap<Pair<LocalDate, String>, PlanCandidate>()
    var cursor = 0
    days.forEach { day ->
        val usedToday = mutableSetOf<String>()
        meals.forEach { meal ->
            val slot = day to meal
            if (slot in occupied) return@forEach
            var pick: PlanCandidate? = null
            for (k in ordered.indices) {
                val c = ordered[(cursor + k) % ordered.size]
                if (c.id !in usedToday) { pick = c; cursor = (cursor + k + 1) % ordered.size; break }
            }
            pick?.let { result[slot] = it; usedToday += it.id }
        }
    }
    return result
}
