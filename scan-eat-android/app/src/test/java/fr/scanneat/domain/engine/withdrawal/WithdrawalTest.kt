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

    @Test fun `a cigarette today ends the smoke-free run`() {
        val now = LocalDateTime.of(2026, 10, 5, 18, 0)
        assertEquals(0, tobaccoStats(plan, mapOf(start.plusDays(4) to 1), now, null).smokeFreeDays)
        assertEquals(5, tobaccoStats(plan, emptyMap(), now, null).smokeFreeDays)
    }

    @Test fun `caffeine cap goes down linearly to the target`() {
        val t = CaffeineTaper(start, 400, 100, 3)
        assertEquals(400, t.capOn(start))
        assertEquals(257, t.capOn(start.plusDays(10)))   // 400 - 300 * 10/21
        assertEquals(100, t.capOn(start.plusDays(21)))
        assertEquals(100, t.capOn(start.plusDays(40)))
    }

    @Test fun `plan cap replaces the EFSA limit only when lower`() {
        val p = fr.scanneat.domain.model.Profile(ageYears = 30)
        assertEquals(250.0, fr.scanneat.domain.engine.nutrition.caffeineBudget(p, emptyList(), 250)!!.limitMg, 0.0)
        assertTrue(fr.scanneat.domain.engine.nutrition.caffeineBudget(p, emptyList(), 250)!!.fromReductionPlan)
        assertEquals(400.0, fr.scanneat.domain.engine.nutrition.caffeineBudget(p, emptyList(), 500)!!.limitMg, 0.0)
        assertNull(fr.scanneat.domain.engine.nutrition.caffeineBudget(p, emptyList(), null))
    }

    @Test fun `alcohol challenge counts alcohol-free days from the diary`() {
        val n0 = fr.scanneat.domain.model.NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0)
        fun e(day: Long, vol: Double?) = fr.scanneat.domain.model.DiaryEntry(date = start.plusDays(day), mealSlot = fr.scanneat.domain.model.MealSlot.DINNER,
            productName = "x", portionG = 250.0, nutrition = n0.copy(alcoholPercentVol = vol), source = fr.scanneat.domain.model.ScanSource.MANUAL)
        val c = AlcoholChallenge(start, 7)
        val st = alcoholChallengeStatus(c, listOf(e(1, 5.0), e(2, 0.0), e(3, null)), start.plusDays(4))
        assertEquals(5, st.dayNumber)
        assertEquals(4, st.alcoholFreeDays)
        assertEquals(3, st.currentStreak)       // days 2, 3, 4
        assertFalse(st.finished)
        assertTrue(alcoholChallengeStatus(c, emptyList(), start.plusDays(10)).finished)
    }

    @Test fun `kcal avoided from the diary baseline, else declared drinks`() {
        val n = fr.scanneat.domain.model.NutritionPer100g(energyKcal = 40.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0)
        fun e(day: Long, vol: Double?) = fr.scanneat.domain.model.DiaryEntry(date = start.plusDays(day), mealSlot = fr.scanneat.domain.model.MealSlot.DINNER,
            productName = "x", portionG = 250.0, nutrition = n.copy(alcoholPercentVol = vol), source = fr.scanneat.domain.model.ScanSource.MANUAL)
        // 28 days before: a 100 kcal beer every day -> 100 kcal/day baseline.
        val history = (1L..28L).map { e(-it, 5.0) }
        val st = alcoholChallengeStatus(AlcoholChallenge(start, 7), history, start.plusDays(3))
        assertTrue(st.baselineFromDiary)
        assertEquals(400, st.kcalAvoided)           // 4 days x 100 kcal
        // Too little history: declared 7 drinks/week = 70 kcal/day.
        val st2 = alcoholChallengeStatus(AlcoholChallenge(start, 7, drinksPerWeek = 7), listOf(e(-1, 5.0)), start.plusDays(1))
        assertFalse(st2.baselineFromDiary)
        assertEquals(140, st2.kcalAvoided)
        assertNull(alcoholChallengeStatus(AlcoholChallenge(start, 7), emptyList(), start).kcalAvoided)
    }
}
