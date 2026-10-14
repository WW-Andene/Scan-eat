package fr.scanneat.domain.engine.planning

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Roadmap D8. */
class WeekPlanGeneratorTest {
    private val days = (0L until 7L).map { LocalDate.of(2026, 10, 12).plusDays(it) }
    private val a = PlanCandidate("a", "Gratin", listOf("pommes de terre", "lait"))
    private val b = PlanCandidate("b", "Ratatouille", listOf("courgette", "tomate"))
    private val c = PlanCandidate("c", "Lentilles", listOf("lentilles"))

    @Test fun `fills only empty slots, expiring first, no repeat in a day`() {
        val occupied = setOf(days[0] to "lunch")
        val plan = generateWeekPlan(listOf(a, b, c), days, listOf("lunch", "dinner"), occupied, setOf("Courgette"))
        assertEquals(13, plan.size)
        assertFalse((days[0] to "lunch") in plan)
        assertEquals("b", plan.getValue(days[0] to "dinner").id)
        days.forEach { d -> assertNotEquals(plan[d to "lunch"]?.id ?: "x", plan[d to "dinner"]?.id) }
    }

    @Test fun `no candidate, nothing filled`() {
        assertTrue(generateWeekPlan(emptyList(), days, listOf("lunch"), emptySet(), emptySet()).isEmpty())
    }
}
