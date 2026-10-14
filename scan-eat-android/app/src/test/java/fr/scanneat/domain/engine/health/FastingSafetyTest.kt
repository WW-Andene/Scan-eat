package fr.scanneat.domain.engine.health

import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import org.junit.Assert.*
import org.junit.Test

/** Roadmap C13. */
class FastingSafetyTest {
    private val adult = Profile(sex = Sex.FEMALE, ageYears = 35, heightCm = 165.0, weightKg = 60.0)

    @Test fun `healthy adult can fast and keeps the streak`() {
        val s = fastingSafety(adult)
        assertFalse(s.blocked); assertTrue(s.risks.isEmpty()); assertTrue(s.showStreak)
    }

    @Test fun `pregnancy and minors are blocked`() {
        assertTrue(fastingSafety(adult.copy(healthConditions = setOf("pregnancy"))).blocked)
        assertTrue(fastingSafety(adult.copy(ageYears = 15)).blocked)
    }

    @Test fun `diabetes and underweight warn without blocking, and hide the streak`() {
        val d = fastingSafety(adult.copy(healthConditions = setOf("diabetes")))
        assertFalse(d.blocked); assertEquals(listOf(FastingRisk.DIABETES), d.risks); assertFalse(d.showStreak)
        val u = fastingSafety(adult.copy(weightKg = 48.0)) // BMI 17.6
        assertEquals(listOf(FastingRisk.UNDERWEIGHT), u.risks)
    }
}
