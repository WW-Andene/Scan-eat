package fr.scanneat.domain.model

import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.domain.engine.dashboard.weightForecast
import org.junit.Assert.*
import org.junit.Test

/** Roadmap C14. */
class SafeGoalTest {
    private val adult = Profile(sex = Sex.FEMALE, ageYears = 35, heightCm = 165.0, weightKg = 70.0, goal = Goal.LOSE)

    @Test fun `lose kept for a healthy adult`() = assertEquals(Goal.LOSE, adult.safeGoal)

    @Test fun `lose becomes maintain for pregnancy, minors and underweight`() {
        assertEquals(Goal.MAINTAIN, adult.copy(healthConditions = setOf("pregnancy")).safeGoal)
        assertEquals(Goal.MAINTAIN, adult.copy(ageYears = 16).safeGoal)
        assertEquals(Goal.MAINTAIN, adult.copy(weightKg = 48.0).safeGoal)
        assertEquals(Goal.GAIN, adult.copy(weightKg = 48.0, goal = Goal.GAIN).safeGoal)
    }

    @Test fun `goal weight under BMI 18_5 gives no success date`() {
        assertEquals(WeightForecast.UnderweightGoal, weightForecast(60.0, 48.0, -0.5, 165.0))
        assertTrue(weightForecast(70.0, 60.0, -0.5, 165.0) is WeightForecast.Ok)
    }
}
