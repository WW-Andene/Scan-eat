package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Grade
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
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
    fun `dailyTargets gives a menstruating woman a higher iron target`() {
        // Iron target is keyed on the profile's own isMenstruating answer,
        // not an age-range guess — see PersonalScoreEngine.kt's dailyTargets().
        val targets = dailyTargets(femaleProfile().copy(isMenstruating = true))!!
        assertEquals(16.0, targets.ironMgTarget, 0.01)    // EFSA 2015: menstruating women
        assertEquals(7.5, targets.zincMgTarget, 0.01)
        assertEquals(300.0, targets.magnesiumMgTarget, 0.01)
    }

    @Test
    fun `dailyTargets regression - a non-menstruating woman in the fertile age range gets the standard iron target`() {
        // Before the fix, any female profile aged 13-50 got 16 mg regardless of
        // what she answered on the isMenstruating checkbox (menopause, pregnancy,
        // hormonal contraception, amenorrhea all left unaccounted for).
        val targets = dailyTargets(femaleProfile().copy(isMenstruating = false))!!
        assertEquals(11.0, targets.ironMgTarget, 0.01)
    }

    @Test
    fun `dailyTargets raises vitamin D target for the elderly`() {
        val elderly = femaleProfile().copy(ageYears = 80)
        val targets = dailyTargets(elderly)!!
        assertEquals(20.0, targets.vitDUgTarget, 0.01)
    }

    // ---- computePersonalScore: SEX iron bonus gated on isMenstruating ----

    private fun productDeclaringIron(kcal: Double = 200.0, proteinG: Double = 5.0) = Product(
        name = "Test product",
        category = ProductCategory.OTHER,
        novaClass = NovaClass.UNPROCESSED,
        ingredients = listOf(Ingredient(name = "farine de blé", category = IngredientCategory.FOOD)),
        nutrition = NutritionPer100g(
            energyKcal = kcal, fatG = 5.0, saturatedFatG = 1.0, carbsG = 20.0,
            sugarsG = 5.0, fiberG = 2.0, proteinG = proteinG, saltG = 0.3,
        ),
        declaredMicronutrients = listOf("iron"),
    )

    @Test
    fun `computePersonalScore applies the iron bonus only when isMenstruating is true`() {
        val product = productDeclaringIron()
        val audit = scoreProduct(product)

        val menstruating = femaleProfile().copy(isMenstruating = true)
        val result = computePersonalScore(audit, product, menstruating)
        assertTrue(result.adjustments.any { it.category == AdjustmentCategory.SEX })

        val notMenstruating = femaleProfile().copy(isMenstruating = false)
        val result2 = computePersonalScore(audit, product, notMenstruating)
        assertFalse(result2.adjustments.any { it.category == AdjustmentCategory.SEX })
    }

    // ---- computePersonalScore: GOAL adjustments ----

    @Test
    fun `computePersonalScore penalizes energy-dense high-satfat products for a weight-loss goal`() {
        val denseProduct = Product(
            name = "Dense product",
            category = ProductCategory.OTHER,
            novaClass = NovaClass.UNPROCESSED,
            ingredients = emptyList(),
            nutrition = NutritionPer100g(
                energyKcal = 500.0, fatG = 30.0, saturatedFatG = 15.0, carbsG = 40.0,
                sugarsG = 10.0, fiberG = 1.0, proteinG = 5.0, saltG = 0.3,
            ),
        )
        val audit = scoreProduct(denseProduct)
        val result = computePersonalScore(audit, denseProduct, maleProfile().copy(goal = Goal.LOSE))
        val goalAdjustment = result.adjustments.firstOrNull { it.category == AdjustmentCategory.GOAL }
        assertNotNull(goalAdjustment)
        assertTrue(goalAdjustment!!.points < 0)
    }

    @Test
    fun `computePersonalScore rewards calorie- and protein-dense products for a weight-gain goal`() {
        val product = productDeclaringIron(kcal = 350.0, proteinG = 20.0)
        val audit = scoreProduct(product)
        val result = computePersonalScore(audit, product, maleProfile().copy(goal = Goal.GAIN))
        val goalAdjustment = result.adjustments.firstOrNull { it.category == AdjustmentCategory.GOAL }
        assertNotNull(goalAdjustment)
        assertTrue(goalAdjustment!!.points > 0)
    }

    @Test
    fun `computePersonalScore applies no GOAL adjustment for maintain`() {
        val product = productDeclaringIron(kcal = 500.0, proteinG = 20.0)
        val audit = scoreProduct(product)
        val result = computePersonalScore(audit, product, maleProfile().copy(goal = Goal.MAINTAIN))
        assertFalse(result.adjustments.any { it.category == AdjustmentCategory.GOAL })
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
