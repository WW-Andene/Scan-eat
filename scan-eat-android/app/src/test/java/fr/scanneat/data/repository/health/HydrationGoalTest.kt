package fr.scanneat.data.repository.health

import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationGoalTest {

    @Test
    fun `unspecified sex uses the default goal plus bonuses`() {
        assertEquals(HYD_DEFAULT_GOAL_ML, hydrationGoalMl(Sex.NOT_SPECIFIED, ActivityLevel.SEDENTARY))
        assertEquals(HYD_DEFAULT_GOAL_ML + 300, hydrationGoalMl(Sex.NOT_SPECIFIED, ActivityLevel.SEDENTARY, setOf("pregnancy")))
        assertEquals(HYD_DEFAULT_GOAL_ML + 600, hydrationGoalMl(Sex.NOT_SPECIFIED, ActivityLevel.SEDENTARY, exerciseMinutesToday = 60))
    }

    // Regression lock: the pregnancy bonus was skipped on the "sex not specified" early return.
    @Test
    fun `pregnancy adds exactly 300 mL for every sex`() {
        for (sex in Sex.entries) {
            val base = hydrationGoalMl(sex, ActivityLevel.MODERATELY_ACTIVE, weightKg = 65.0)
            val pregnant = hydrationGoalMl(sex, ActivityLevel.MODERATELY_ACTIVE, setOf("pregnancy"), weightKg = 65.0)
            assertEquals("$sex", 300, pregnant - base)
        }
    }

    @Test
    fun `exercise bonus is 10 mL per minute capped at 1500 and ignores negatives`() {
        val base = hydrationGoalMl(Sex.FEMALE, ActivityLevel.LIGHTLY_ACTIVE, weightKg = 60.0)
        assertEquals(base + 300, hydrationGoalMl(Sex.FEMALE, ActivityLevel.LIGHTLY_ACTIVE, exerciseMinutesToday = 30, weightKg = 60.0))
        assertEquals(base + 1500, hydrationGoalMl(Sex.FEMALE, ActivityLevel.LIGHTLY_ACTIVE, exerciseMinutesToday = 600, weightKg = 60.0))
        assertEquals(base, hydrationGoalMl(Sex.FEMALE, ActivityLevel.LIGHTLY_ACTIVE, exerciseMinutesToday = -20, weightKg = 60.0))
    }

    @Test
    fun `sex-specific goals are plausible and ordered by activity`() {
        val sed = hydrationGoalMl(Sex.MALE, ActivityLevel.SEDENTARY, weightKg = 80.0)
        val extra = hydrationGoalMl(Sex.MALE, ActivityLevel.EXTRA_ACTIVE, weightKg = 80.0)
        assertTrue("$sed", sed in 1500..4500)
        assertTrue("$sed <= $extra", sed <= extra)
    }
}
