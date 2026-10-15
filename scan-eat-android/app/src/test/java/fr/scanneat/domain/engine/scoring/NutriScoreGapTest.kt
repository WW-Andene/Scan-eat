package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NutriScoreGapTest {
    private val n = NutritionPer100g(energyKcal = 120.0, fatG = 3.0, saturatedFatG = 1.0, carbsG = 15.0, sugarsG = 5.0, fiberG = 2.0, proteinG = 6.0, saltG = 0.2)
    private fun audit(nova: NovaClass, ns: String?, vararg ing: String) =
        scoreProduct(Product(name = "Test", category = ProductCategory.OTHER, novaClass = nova, novaDeclared = true,
            ingredients = ing.map { Ingredient(name = it, category = IngredientCategory.FOOD) }, nutrition = n), "fr").copy(nutriscoreGrade = ns)

    @Test fun `no gap without a Nutri-Score or when grades are close`() {
        val a = audit(NovaClass.PROCESSED, null, "lait")
        assertNull(nutriScoreGap(a, "fr"))
        val close = a.copy(nutriscoreGrade = when (a.grade) { Grade.A_PLUS, Grade.A -> "a"; Grade.B -> "b"; Grade.C -> "c"; Grade.D -> "d"; else -> "e" })
        assertNull(nutriScoreGap(close, "fr"))
    }

    @Test fun `stricter than an A Nutri-Score explains processing`() {
        val a = audit(NovaClass.ULTRA_PROCESSED, "a", "lait", "sirop de glucose", "arôme", "E471", "E250").copy(grade = Grade.D)
        val gap = nutriScoreGap(a, "fr")!!
        assertTrue(gap.scanEatWorse)
        assertTrue(gap.reasons.isNotEmpty())
    }

    @Test fun `better than an E Nutri-Score`() {
        val a = audit(NovaClass.UNPROCESSED, "e", "lait").copy(grade = Grade.A)
        assertFalse(nutriScoreGap(a, "fr")!!.scanEatWorse)
    }
}
