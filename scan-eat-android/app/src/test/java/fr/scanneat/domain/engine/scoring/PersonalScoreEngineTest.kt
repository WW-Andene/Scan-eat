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

    // ---- dailyTargets: Goal kcal adjustment ----

    @Test
    fun `dailyTargets subtracts 500 kcal from TDEE for a weight-loss goal`() {
        // Baseline TDEE (Goal.MAINTAIN) is 2492.0 - see the "derives macro budgets" test above.
        val targets = dailyTargets(maleProfile().copy(goal = Goal.LOSE))!!
        assertEquals(2492.0 - 500.0, targets.kcal, 0.01)
    }

    @Test
    fun `dailyTargets adds 500 kcal to TDEE for a weight-gain goal`() {
        val targets = dailyTargets(maleProfile().copy(goal = Goal.GAIN))!!
        assertEquals(2492.0 + 500.0, targets.kcal, 0.01)
    }

    @Test
    fun `dailyTargets fat and carbs targets are derived from the goal-adjusted kcal, not raw TDEE`() {
        val lose = dailyTargets(maleProfile().copy(goal = Goal.LOSE))!!
        // 30% of the goal-adjusted (not raw) kcal at 9 kcal/g
        assertEquals(0.30 * lose.kcal / 9.0, lose.fatGTarget, 0.01)
    }

    // ---- withKcalOverride: must re-apply the same Goal adjustment ----
    //
    // Regression coverage for the bug where Dashboard/Diary/Widget swap in Biolism's
    // richer *maintenance* TDEE estimate via withKcalOverride(), which previously set
    // `kcal` to that raw value directly - silently discarding the +-500 kcal Lose/Gain
    // adjustment dailyTargets() itself applies. A Lose-goal user with a valid Biolism
    // profile (the common case - it auto-populates from the main Profile) was shown
    // maintenance calories as their "target" instead of a deficit.

    @Test
    fun `withKcalOverride re-applies the weight-loss deficit onto the raw override kcal`() {
        val base = dailyTargets(maleProfile().copy(goal = Goal.LOSE))!!
        val rawMaintenanceTdee = 3000.0 // e.g. Biolism's computeMetabolics().tdeeDay - a plain maintenance estimate
        val overridden = base.withKcalOverride(rawMaintenanceTdee, Goal.LOSE)
        assertEquals(rawMaintenanceTdee - 500.0, overridden.kcal, 0.01)
    }

    @Test
    fun `withKcalOverride re-applies the weight-gain surplus onto the raw override kcal`() {
        val base = dailyTargets(maleProfile().copy(goal = Goal.GAIN))!!
        val rawMaintenanceTdee = 3000.0
        val overridden = base.withKcalOverride(rawMaintenanceTdee, Goal.GAIN)
        assertEquals(rawMaintenanceTdee + 500.0, overridden.kcal, 0.01)
    }

    @Test
    fun `withKcalOverride leaves maintenance kcal untouched`() {
        val base = dailyTargets(maleProfile().copy(goal = Goal.MAINTAIN))!!
        val rawMaintenanceTdee = 3000.0
        val overridden = base.withKcalOverride(rawMaintenanceTdee, Goal.MAINTAIN)
        assertEquals(rawMaintenanceTdee, overridden.kcal, 0.01)
    }

    @Test
    fun `withKcalOverride rescales fat and carbs proportionally to the new goal-adjusted kcal`() {
        val base = dailyTargets(maleProfile().copy(goal = Goal.LOSE))!!
        val overridden = base.withKcalOverride(3000.0, Goal.LOSE)
        val ratio = overridden.kcal / base.kcal
        assertEquals(base.fatGTarget * ratio, overridden.fatGTarget, 0.01)
        // Protein is per-kg body weight, not kcal-derived - must NOT be rescaled.
        assertEquals(base.proteinGTarget, overridden.proteinGTarget, 0.01)
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

    // ---- computePersonalScore: BMI — category-aware thresholds ----
    //
    // Regression coverage for a reported inconsistency: raw mozzarella (naturally
    // high in saturated fat, as all cheese is) got the BMI "amplified" penalty on
    // every single scan, while a boxed/ultra-processed pasta meal (refined carbs,
    // low fat, low sugar, but calorie-dense) got none at all — the old code
    // compared everything against one flat 5g-sat-fat/15g-sugar bar regardless of
    // category, instead of the same category-relative thresholds
    // NegativeNutrientsPillar already uses for the base score.

    private fun obeseProfile() = maleProfile().copy(weightKg = 110.0) // BMI ~33.9 -> OBESE_1

    @Test
    fun `Mozzarella with moderate sat fat gets no BMI penalty once cheese's own category threshold is used`() {
        // 9g sat fat/100g is above the flat 5g bar the old code used, but below
        // cheese's own category threshold (12g "moderate" tier) — this is an
        // unremarkable amount of fat for a cheese, not something that should be
        // flagged as extra-risky just because it's cheese.
        val mozzarella = Product(
            name = "Mozzarella fraîche", category = ProductCategory.CHEESE, novaClass = NovaClass.PROCESSED,
            ingredients = listOf(Ingredient(name = "lait", category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(
                energyKcal = 280.0, fatG = 17.0, saturatedFatG = 9.0, carbsG = 2.0,
                sugarsG = 1.0, fiberG = 0.0, proteinG = 22.0, saltG = 0.6,
            ),
        )
        val audit = scoreProduct(mozzarella)
        // lang="en" so the reason text below matches the English phrasing being
        // asserted on (computePersonalScore defaults to "fr").
        val result = computePersonalScore(audit, mozzarella, obeseProfile(), lang = "en")
        // AdjustmentCategory.BMI is also reused by the unrelated PROTEIN PRI and
        // DAILY TARGET CONTEXT sections (this cheese's own protein density
        // legitimately earns a PRI bonus regardless of the fix under test here),
        // so this checks the specific sat-fat/sugar rule's own reason text
        // rather than the whole (overloaded) category tag.
        assertTrue("Moderate-sat-fat cheese should not get the BMI sat-fat/sugar penalty (was flagged via a flat 5g bar before the fix)",
            result.adjustments.none { it.reason.contains("sat fat/sugar penalty amplified", ignoreCase = true) })
    }

    @Test
    fun `Mozzarella genuinely above cheese's own category threshold still gets flagged`() {
        // The fix only removes the *unfair* flag — a cheese that's unusually
        // fatty even by cheese standards (>12g/100g) should still be caught.
        val fattyMozzarella = Product(
            name = "Mozzarella extra grasse", category = ProductCategory.CHEESE, novaClass = NovaClass.PROCESSED,
            ingredients = listOf(Ingredient(name = "lait", category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(
                energyKcal = 350.0, fatG = 28.0, saturatedFatG = 18.0, carbsG = 2.0,
                sugarsG = 1.0, fiberG = 0.0, proteinG = 22.0, saltG = 0.6,
            ),
        )
        val audit = scoreProduct(fattyMozzarella)
        val result = computePersonalScore(audit, fattyMozzarella, obeseProfile(), lang = "en")
        assertTrue(result.adjustments.any { it.reason.contains("sat fat/sugar penalty amplified", ignoreCase = true) })
    }

    @Test
    fun `Boxed ultra-processed pasta meal now gets a BMI penalty it previously never got`() {
        // Low fat, low sugar (never crosses the old flat bars at all), but
        // ultra-processed refined-carb and markedly more calorie-dense than a
        // typical ready meal (category norm 80-200 kcal/100g).
        val pastaBox = Product(
            name = "Pâtes box au fromage", category = ProductCategory.READY_MEAL, novaClass = NovaClass.ULTRA_PROCESSED,
            ingredients = listOf(
                Ingredient(name = "pâtes de blé", category = IngredientCategory.FOOD),
                Ingredient(name = "arômes", category = IngredientCategory.ADDITIVE),
            ),
            nutrition = NutritionPer100g(
                // saltG kept well clear of the unrelated DAILY TARGET CONTEXT
                // salt-budget rule's own 30%-of-5g-ceiling trigger, so this test
                // stays focused on the refined-carb/energy-density signals only.
                energyKcal = 370.0, fatG = 2.0, saturatedFatG = 1.0, carbsG = 70.0,
                sugarsG = 3.0, fiberG = 2.0, proteinG = 10.0, saltG = 1.0,
            ),
        )
        val audit = scoreProduct(pastaBox)
        val result = computePersonalScore(audit, pastaBox, obeseProfile())
        val bmiAdjustments = result.adjustments.filter { it.category == AdjustmentCategory.BMI }
        assertTrue("Boxed ultra-processed pasta meal should now get at least one BMI adjustment (refined-carb and/or energy-density signal)",
            bmiAdjustments.isNotEmpty())
        assertTrue("All new BMI signals here should be penalties, not bonuses", bmiAdjustments.all { it.points < 0 })
    }

    // ---- computePersonalScore: sugar-sweetened beverages (SSB) ----
    //
    // Regression coverage for a second reported gap: soda was never flagged by
    // any condition/BMI check (a regular cola is ~10.6g sugar/100g, under every
    // flat/category "major" bar those checks used), and a regular vs zero-sugar
    // variant were indistinguishable to the personalization layer as a result -
    // both "passed" since neither crossed 15g. isSugarSweetenedBeverage fixes
    // this by reusing checkVeto's own already-established SSB definition.

    private fun regularSoda() = Product(
        name = "Soda cola", category = ProductCategory.BEVERAGE_SOFT, novaClass = NovaClass.ULTRA_PROCESSED,
        ingredients = listOf(Ingredient(name = "eau gazéifiée", category = IngredientCategory.FOOD), Ingredient(name = "sucre", category = IngredientCategory.FOOD)),
        nutrition = NutritionPer100g(
            energyKcal = 42.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 10.6,
            sugarsG = 10.6, fiberG = 0.0, proteinG = 0.0, saltG = 0.0,
        ),
    )

    private fun zeroSoda() = Product(
        name = "Soda cola zero", category = ProductCategory.BEVERAGE_SOFT, novaClass = NovaClass.ULTRA_PROCESSED,
        ingredients = listOf(
            Ingredient(name = "eau gazéifiée", category = IngredientCategory.FOOD),
            Ingredient(name = "édulcorant : aspartame", eNumber = "E951", category = IngredientCategory.ADDITIVE),
        ),
        nutrition = NutritionPer100g(
            energyKcal = 1.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.1,
            sugarsG = 0.1, fiberG = 0.0, proteinG = 0.0, saltG = 0.0,
        ),
    )

    @Test
    fun `Regular soda gets a diabetes SSB penalty a flat 15g bar would have missed`() {
        val soda = regularSoda()
        val audit = scoreProduct(soda)
        val result = computePersonalScore(audit, soda, maleProfile().copy(healthConditions = setOf("diabetes")), lang = "en")
        assertTrue(result.adjustments.any { it.reason.contains("Sugar-sweetened beverage", ignoreCase = true) && it.category == AdjustmentCategory.CONDITION })
    }

    @Test
    fun `Zero-sugar soda does not get the SSB penalty - regular and zero are now distinguishable`() {
        val soda = zeroSoda()
        val audit = scoreProduct(soda)
        val diabetic = maleProfile().copy(healthConditions = setOf("diabetes"))
        val result = computePersonalScore(audit, soda, diabetic, lang = "en")
        assertTrue("Zero-sugar soda should not trigger the SSB-specific diabetes penalty",
            result.adjustments.none { it.reason.contains("Sugar-sweetened beverage", ignoreCase = true) })
    }

    @Test
    fun `Regular soda gets the SSB penalty amplified for an obese BMI`() {
        val soda = regularSoda()
        val audit = scoreProduct(soda)
        val result = computePersonalScore(audit, soda, obeseProfile(), lang = "en")
        assertTrue(result.adjustments.any { it.reason.contains("Sugar-sweetened beverage", ignoreCase = true) && it.category == AdjustmentCategory.BMI })
    }

    @Test
    fun `Regular soda gets the under-18 sugar penalty a flat 15g bar would have missed`() {
        val soda = regularSoda()
        val audit = scoreProduct(soda)
        val minor = maleProfile().copy(ageYears = 15)
        val result = computePersonalScore(audit, soda, minor, lang = "en")
        assertTrue(result.adjustments.any { it.category == AdjustmentCategory.AGE && it.reason.contains("under-18", ignoreCase = true) })
    }

    @Test
    fun `Regular soda gets the depression sugar penalty a flat 15g bar would have missed`() {
        val soda = regularSoda()
        val audit = scoreProduct(soda)
        val result = computePersonalScore(audit, soda, maleProfile().copy(healthConditions = setOf("depression")), lang = "en")
        assertTrue(result.adjustments.any { it.category == AdjustmentCategory.CONDITION && it.reason.contains("Knüppel", ignoreCase = true) })
    }

    @Test
    fun `A non-SSB beverage like water is unaffected by the SSB checks`() {
        val water = Product(
            name = "Eau minérale", category = ProductCategory.BEVERAGE_WATER, novaClass = NovaClass.UNPROCESSED,
            ingredients = listOf(Ingredient(name = "eau minérale naturelle", isWholeFood = true, category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0),
        )
        val audit = scoreProduct(water)
        val result = computePersonalScore(audit, water, obeseProfile(), lang = "en")
        assertTrue(result.adjustments.none { it.reason.contains("Sugar-sweetened beverage", ignoreCase = true) })
    }

    // ---- computePersonalScore: pregnancy caffeine ----
    //
    // Regression coverage for a third gap: ANSES's pregnancy caffeine limit was
    // already cited in the hint panel but never actually affected the score -
    // a caffeinated product scored identically to a decaf one for a pregnant
    // profile.

    private fun pregnantProfile() = femaleProfile().copy(healthConditions = setOf("pregnancy"))

    @Test
    fun `Caffeinated product gets a pregnancy-specific penalty`() {
        val cola = Product(
            name = "Soda cola", category = ProductCategory.BEVERAGE_SOFT, novaClass = NovaClass.ULTRA_PROCESSED,
            ingredients = listOf(Ingredient(name = "eau gazéifiée", category = IngredientCategory.FOOD), Ingredient(name = "caféine", category = IngredientCategory.ADDITIVE)),
            nutrition = NutritionPer100g(energyKcal = 42.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 10.6, sugarsG = 10.6, fiberG = 0.0, proteinG = 0.0, saltG = 0.0),
        )
        val audit = scoreProduct(cola)
        val result = computePersonalScore(audit, cola, pregnantProfile(), lang = "en")
        assertTrue(result.adjustments.any { it.category == AdjustmentCategory.CONDITION && it.reason.contains("caffeine", ignoreCase = true) })
    }

    @Test
    fun `Decaf product gets no pregnancy caffeine penalty`() {
        val decaf = Product(
            name = "Limonade", category = ProductCategory.BEVERAGE_SOFT, novaClass = NovaClass.ULTRA_PROCESSED,
            ingredients = listOf(Ingredient(name = "eau gazéifiée", category = IngredientCategory.FOOD), Ingredient(name = "sucre", category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(energyKcal = 42.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 10.6, sugarsG = 10.6, fiberG = 0.0, proteinG = 0.0, saltG = 0.0),
        )
        val audit = scoreProduct(decaf)
        val result = computePersonalScore(audit, decaf, pregnantProfile(), lang = "en")
        assertTrue(result.adjustments.none { it.reason.contains("caffeine", ignoreCase = true) })
    }

    // Regression: a plain substring check on the normalized ingredient name let
    // bare "mate" match inside "tomate" (tomato) and bare "tea" match inside
    // "steak" - firing a false pregnancy caffeine warning on completely
    // unrelated, extremely common ingredients. Word-boundary matching must not
    // trigger on either.
    @Test
    fun `Tomato-containing product does not trigger the pregnancy caffeine check`() {
        val ketchup = Product(
            name = "Ketchup", category = ProductCategory.CONDIMENT, novaClass = NovaClass.PROCESSED,
            ingredients = listOf(Ingredient(name = "tomate", category = IngredientCategory.FOOD), Ingredient(name = "sucre", category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(energyKcal = 100.0, fatG = 0.2, saturatedFatG = 0.0, carbsG = 24.0, sugarsG = 20.0, fiberG = 1.0, proteinG = 1.2, saltG = 1.0),
        )
        val audit = scoreProduct(ketchup)
        val result = computePersonalScore(audit, ketchup, pregnantProfile(), lang = "en")
        assertTrue(result.adjustments.none { it.reason.contains("caffeine", ignoreCase = true) })
    }

    @Test
    fun `Steak does not trigger the pregnancy caffeine check`() {
        val steak = Product(
            name = "Steak haché", category = ProductCategory.FRESH_MEAT, novaClass = NovaClass.UNPROCESSED,
            ingredients = listOf(Ingredient(name = "steak haché de boeuf", isWholeFood = true, category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(energyKcal = 215.0, fatG = 15.0, saturatedFatG = 6.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 20.0, saltG = 0.1),
        )
        val audit = scoreProduct(steak)
        val result = computePersonalScore(audit, steak, pregnantProfile(), lang = "en")
        assertTrue(result.adjustments.none { it.reason.contains("caffeine", ignoreCase = true) })
    }

    // Regression: "vin " (space-suffixed to avoid matching "vinaigre") missed
    // the case where "vin" is the ingredient's exact/final name with nothing
    // following it - e.g. an OFF ingredient list ending in ",vin" parses to a
    // lone Ingredient(name="vin"). The pregnancy alcohol veto must still fire.
    @Test
    fun `Product whose sole ingredient is exactly 'vin' still triggers the pregnancy alcohol veto`() {
        val coqAuVinSauce = Product(
            name = "Sauce coq au vin", category = ProductCategory.CONDIMENT, novaClass = NovaClass.PROCESSED,
            ingredients = listOf(Ingredient(name = "vin", category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(energyKcal = 80.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 3.0, sugarsG = 1.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.3),
        )
        val audit = scoreProduct(coqAuVinSauce)
        val result = computePersonalScore(audit, coqAuVinSauce, pregnantProfile(), lang = "en")
        assertTrue("Pregnancy veto should trigger for a bare 'vin' ingredient", result.veto)
        assertEquals(0, result.personalScore)
    }

    @Test
    fun `Vinaigrette (vinegar) does not falsely trigger the pregnancy alcohol veto`() {
        // Plain "vinaigre" (not "vinaigre de vin") - the latter genuinely
        // contains "vin" as its own word (wine vinegar's own name), which is a
        // separate, real ambiguity this fix doesn't attempt to resolve. This
        // test is only about the substring-vs-word-boundary distinction:
        // "vinaigre" must not match "vin" the way a naive prefix-substring
        // check would.
        val vinaigrette = Product(
            name = "Vinaigrette", category = ProductCategory.CONDIMENT, novaClass = NovaClass.PROCESSED,
            ingredients = listOf(Ingredient(name = "vinaigre", category = IngredientCategory.FOOD), Ingredient(name = "huile de tournesol", category = IngredientCategory.FOOD)),
            nutrition = NutritionPer100g(energyKcal = 300.0, fatG = 30.0, saturatedFatG = 3.0, carbsG = 2.0, sugarsG = 1.0, fiberG = 0.0, proteinG = 0.0, saltG = 1.0),
        )
        val audit = scoreProduct(vinaigrette)
        val result = computePersonalScore(audit, vinaigrette, pregnantProfile(), lang = "en")
        assertFalse("Vinaigre (vinegar) must not match the alcohol word-boundary pattern", result.veto)
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
