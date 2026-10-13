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
}
