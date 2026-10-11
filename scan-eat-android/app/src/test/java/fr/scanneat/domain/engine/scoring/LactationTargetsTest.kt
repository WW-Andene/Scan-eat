package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LactationTargetsTest {
    private val base = Profile(sex = Sex.FEMALE, ageYears = 30, heightCm = 165.0, weightKg = 60.0, goal = Goal.LOSE)
    private val nursing = base.copy(healthConditions = setOf("breastfeeding"))

    @Test fun `breastfeeding adds EFSA energy and protein and skips the deficit`() {
        val plain = dailyTargets(base.copy(goal = Goal.MAINTAIN))!!
        val plainProtein = dailyTargets(base)!!.proteinGTarget  // protein is goal-aware (LOSE)
        val t = dailyTargets(nursing)!!
        assertEquals(plain.kcal + 330.0, t.kcal, 0.01)
        assertEquals(plainProtein + 19.0, t.proteinGTarget, 0.01)
        assertEquals(16.0, t.ironMgTarget, 0.0)
        assertEquals(1300.0, t.vitAUgTarget, 0.0)
        assertEquals(155.0, t.vitCMgTarget, 0.0)
        assertEquals(500.0, t.b9UgTarget, 0.0)
    }

    @Test fun `pregnancy takes precedence over breastfeeding`() {
        val both = nursing.copy(healthConditions = setOf("pregnancy", "breastfeeding"))
        assertFalse(isLactating(both))
    }

    @Test fun `kcal override keeps the lactation bonus`() {
        val t = dailyTargets(nursing)!!.withKcalOverride(2000.0, Goal.LOSE, null, true)
        assertEquals(2330.0, t.kcal, 0.01)
    }

    @Test fun `breastfeeding cautions alcohol`() {
        val adj = mutableListOf<PersonalAdjustment>()
        val product = Product("Bière", ProductCategory.ALCOHOLIC_BEVERAGE, NovaClass.PROCESSED, emptyList(), NutritionPer100g(43.0, 0.0, 0.0, 3.6, 0.0, fiberG = 0.0, proteinG = 0.5, saltG = 0.0))
        checkBreastfeedingCondition(product, setOf("breastfeeding"), "en", true, adj)
        assertTrue(adj.any { it.points < 0 && !it.veto })
    }
}
