package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Grade
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import org.junit.Assert.*
import org.junit.Test

class PersonalScoreEngineTest {

    private fun maleProfile(activity: ActivityLevel = ActivityLevel.SEDENTARY) = Profile(
        sex = Sex.MALE,
        ageYears = 30,
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = activity,
    )

    private fun femaleProfile(activity: ActivityLevel = ActivityLevel.SEDENTARY) = Profile(
        sex = Sex.FEMALE,
        ageYears = 28,
        heightCm = 165.0,
        weightKg = 60.0,
        activityLevel = activity,
    )

    // ---- hasMinimalProfile ----

    @Test
    fun `hasMinimalProfile is false for an incomplete profile`() {
        assertFalse(hasMinimalProfile(Profile()))
        assertFalse(hasMinimalProfile(Profile(sex = Sex.MALE, ageYears = 30)))
    }

    @Test
    fun `hasMinimalProfile is true once sex, age, height and weight are all set`() {
        assertTrue(hasMinimalProfile(maleProfile()))
    }

    // ---- bmrMifflinStJeor ----

    @Test
    fun `bmrMifflinStJeor is null without a minimal profile`() {
        assertNull(bmrMifflinStJeor(Profile()))
    }

    @Test
    fun `bmrMifflinStJeor for a known male profile`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, bmrMifflinStJeor(maleProfile())!!, 0.01)
    }

    @Test
    fun `bmrMifflinStJeor for a known female profile`() {
        // 10*60 + 6.25*165 - 5*28 - 161 = 1330.25
        assertEquals(1330.25, bmrMifflinStJeor(femaleProfile())!!, 0.01)
    }

    // ---- tdeeKcal ----

    @Test
    fun `tdeeKcal applies the sedentary PAL multiplier`() {
        // 1780 * 1.40
        assertEquals(2492.0, tdeeKcal(maleProfile())!!, 0.01)
    }

    @Test
    fun `tdeeKcal scales up with a higher activity level`() {
        val sedentary = tdeeKcal(maleProfile(ActivityLevel.SEDENTARY))!!
        val veryActive = tdeeKcal(maleProfile(ActivityLevel.VERY_ACTIVE))!!
        val extraActive = tdeeKcal(maleProfile(ActivityLevel.EXTRA_ACTIVE))!!
        assertTrue(veryActive > sedentary)
        assertTrue(extraActive > veryActive)
    }

    // ---- bmi / bmiCategory ----

    @Test
    fun `bmi for a known profile`() {
        // 80 / 1.80^2 = 24.69 -> rounded to 1dp = 24.7
        assertEquals(24.7, bmi(maleProfile())!!, 0.01)
    }

    @Test
    fun `bmiCategory buckets correctly`() {
        assertEquals(BmiCategory.UNDERWEIGHT, bmiCategory(17.0))
        assertEquals(BmiCategory.NORMAL, bmiCategory(22.0))
        assertEquals(BmiCategory.OVERWEIGHT, bmiCategory(27.0))
        assertEquals(BmiCategory.OBESE_1, bmiCategory(32.0))
        assertEquals(BmiCategory.OBESE_2, bmiCategory(37.0))
        assertEquals(BmiCategory.OBESE_3, bmiCategory(42.0))
        assertNull(bmiCategory(null))
    }

    // ---- proteinPriG ----

    @Test
    fun `proteinPriG uses 0,83 g per kg under 65`() {
        // 80 * 0.83 = 66.4 -> rounds to 66
        assertEquals(66.0, proteinPriG(maleProfile())!!, 0.01)
    }

    @Test
    fun `proteinPriG uses 1,0 g per kg at 65 and over`() {
        val elderly = maleProfile().copy(ageYears = 70)
        // 80 * 1.0 = 80
        assertEquals(80.0, proteinPriG(elderly)!!, 0.01)
    }

    // ---- dailyTargets: macro target ranges ----

    @Test
    fun `dailyTargets is null without a valid TDEE`() {
        assertNull(dailyTargets(Profile()))
    }

    @Test
    fun `dailyTargets derives macro budgets from TDEE for a male profile`() {
        val targets = dailyTargets(maleProfile())!!
        assertEquals(2492.0, targets.kcal, 0.01)
        // 10% of kcal as sat-fat budget, 9 kcal/g
        assertEquals(27.69, targets.satFatGMax, 0.01)
        // 10% of kcal as sugar ceiling, 4 kcal/g
        assertEquals(62.3, targets.freeSugarsGMax, 0.01)
        assertEquals(31.15, targets.freeSugarsGIdeal, 0.01)
        assertEquals(66.0, targets.proteinGTarget, 0.01)
        assertEquals(11.0, targets.ironMgTarget, 0.01)   // non-menstruating-age iron RNI
        assertEquals(9.4, targets.zincMgTarget, 0.01)     // male zinc RNI
        assertEquals(350.0, targets.magnesiumMgTarget, 0.01) // male magnesium RNI
    }

    @Test
    fun `dailyTargets gives menstruating-age women a higher iron and lower zinc target`() {
        val targets = dailyTargets(femaleProfile())!!
        assertEquals(16.0, targets.ironMgTarget, 0.01)    // EFSA 2015: menstruating women
        assertEquals(7.5, targets.zincMgTarget, 0.01)
        assertEquals(300.0, targets.magnesiumMgTarget, 0.01)
    }

    @Test
    fun `dailyTargets raises vitamin D target for the elderly`() {
        val elderly = femaleProfile().copy(ageYears = 80)
        val targets = dailyTargets(elderly)!!
        assertEquals(20.0, targets.vitDUgTarget, 0.01)
    }

    // ---- personalGrade ----

    @Test
    fun `personalGrade maps score breakpoints to the right grade`() {
        assertEquals(Grade.A_PLUS, personalGrade(90))
        assertEquals(Grade.A, personalGrade(75))
        assertEquals(Grade.B, personalGrade(60))
        assertEquals(Grade.C, personalGrade(45))
        assertEquals(Grade.D, personalGrade(30))
        assertEquals(Grade.F, personalGrade(10))
    }
}
