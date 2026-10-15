package fr.scanneat.domain.engine.withdrawal

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WithdrawalTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val plan = TobaccoPlan(start, baselinePerDay = 10, packPriceEuros = 12.0, cigarettesPerPack = 20)

    @Test fun `smoke-free streak, avoided cigarettes and money`() {
        val now = LocalDateTime.of(2026, 10, 5, 18, 0)   // days 1..5 of the plan
        val log = mapOf(start to 4, start.plusDays(1) to 1)
        val s = tobaccoStats(plan, log, now, lastSmokeAt = LocalDateTime.of(2026, 10, 2, 9, 0))
        assertEquals(3, s.smokeFreeDays)                 // 3, 4 and 5 Oct (today) at zero
        assertEquals(10 * 5 - 5, s.cigarettesAvoided)
        assertEquals(45.0 / 20 * 12.0, s.moneySavedEuros, 0.001)
        assertFalse(s.todayOverTarget)
    }

    @Test fun `reduction target and today over it`() {
        val p = plan.copy(targetPerDay = 5)
        val now = LocalDateTime.of(2026, 10, 1, 20, 0)
        val s = tobaccoStats(p, mapOf(start to 6), now, null)
        assertTrue(s.todayOverTarget)
        assertEquals(0, s.smokeFreeDays)
    }

    @Test fun `milestones from the last cigarette`() {
        val last = LocalDateTime.of(2026, 10, 1, 8, 0)
        assertEquals(listOf(QuitMilestone.HEART_RATE), reachedMilestones(last, last.plusMinutes(30)))
        assertEquals(QuitMilestone.CARBON_MONOXIDE, nextMilestone(last, last.plusMinutes(30)))
        assertTrue(reachedMilestones(last, last.minusMinutes(1)).isEmpty())
    }

    @Test fun `taper current and next step, never invents one`() {
        val t = TaperPlan("X", listOf(TaperStep(start.plusDays(14), "1/2 cp"), TaperStep(start, "1 cp"), TaperStep(start.plusDays(28), "1/4 cp")))
        val st = taperStatus(t, start.plusDays(20))
        assertEquals("1/2 cp", st.current?.dose)
        assertEquals("1/4 cp", st.next?.dose)
        assertEquals(1, st.stepIndex)
        assertNull(taperStatus(t, start.minusDays(1)).current)
        assertNull(taperStatus(t, start.plusDays(40)).next)
        assertEquals("1/2 cp", taperStepStartsOn(t, start.plusDays(14))?.dose)
        assertNull(taperStepStartsOn(t, start.plusDays(15)))
    }
}
