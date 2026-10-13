package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/** Score-engine audit S2-S10 (Roadmap.md): each test pins a fixed veracity defect. */
class ScoreVeracityTest {

    private fun n(
        kcal: Double = 100.0, fat: Double = 1.0, sat: Double = 0.2, carbs: Double = 10.0, sugars: Double = 2.0,
        fiber: Double = 2.0, protein: Double = 3.0, salt: Double = 0.1, added: Double? = null, trans: Double? = null,
        caffeine: Double? = null,
    ) = NutritionPer100g(energyKcal = kcal, fatG = fat, saturatedFatG = sat, carbsG = carbs, sugarsG = sugars,
        fiberG = fiber, proteinG = protein, saltG = salt, addedSugarsG = added, transFatG = trans, caffeineMg = caffeine)

    private fun p(
        name: String = "Produit", cat: ProductCategory = ProductCategory.OTHER, nova: NovaClass = NovaClass.PROCESSED,
        ings: List<String> = emptyList(), nutrition: NutritionPer100g = n(), declared: Boolean = true,
    ) = Product(name, cat, nova, ings.map { Ingredient(it) }, nutrition, novaDeclared = declared)

    private fun allReasons(a: ScoreAudit) = with(a.pillars) {
        listOf(processing, nutritionalDensity, negativeNutrients, additiveRisk, ingredientIntegrity)
            .flatMap { it.deductions + it.bonuses }.map { it.reason }
    } + (a.globalBonuses + a.globalPenalties).map { it.reason } + a.veto.reason

    // ---- S3: absence of data must not produce positive claims -------------
    @Test fun `no ingredient list - no hidden-sugar or additive-free claim and reduced coverage`() {
        val a = scoreProduct(p(declared = true), "fr")
        val reasons = allReasons(a)
        assertFalse(reasons.any { "Aucun sucre caché" in it })
        assertEquals(0.0, a.pillars.additiveRisk.score, 0.0)
        assertTrue(a.evidenceCoverage < 100)
        assertTrue(a.warnings.any { "Note établie sur" in it })
    }

    @Test fun `no ingredient list - no IBS-friendly bonus and diet not claimed compliant`() {
        val prod = p()
        val profile = Profile(healthConditions = setOf("ibs"), diet = DietKey.VEGAN)
        val r = computePersonalScore(scoreProduct(prod, "fr"), prod, profile, "fr")
        assertFalse(r.adjustments.any { "adapté au SII" in it.reason })
        assertTrue(r.adjustments.any { "non vérifiable" in it.reason })
    }

    @Test fun `full data keeps full coverage`() {
        val a = scoreProduct(p(ings = listOf("tomates", "sel")), "fr")
        assertEquals(100, a.evidenceCoverage)
    }

    // ---- S6: OFF sub-indexed E-numbers ------------------------------------
    @Test fun `sub-indexed E-numbers resolve to their parent additive`() {
        assertEquals("E322", findAdditive("E322I", "E322I", null)?.eNumber)
        assertEquals("E500", findAdditive("E500II", "E500II", null)?.eNumber)
        assertEquals("E472", findAdditive("E472E", "E472E", null)?.eNumber)
        assertEquals("E450", findAdditive("E450I", "E450I", null)?.eNumber)
    }

    @Test fun `an additive named in the text and tagged by OFF is counted once`() {
        val existing = listOf(Ingredient("lécithine de soja"))
        val extra = fr.scanneat.domain.engine.nutrition.additiveTagsToIngredients(listOf("en:e322i"), existing)
        assertTrue(extra.isEmpty())
    }

    // ---- S7: no penalty for additives without a safety concern ------------
    @Test fun `vitamin C and pectin cost no point`() {
        val withVitC = scoreProduct(p(ings = listOf("pommes", "acide ascorbique", "pectine")), "fr")
        assertEquals(15.0, withVitC.pillars.additiveRisk.score, 0.0)
    }

    @Test fun `nitrite is described with its real IARC group`() {
        val nitrite = findAdditive("E250", "nitrite de sodium", null)!!
        assertTrue("2A" in nitrite.concern)
    }
}
