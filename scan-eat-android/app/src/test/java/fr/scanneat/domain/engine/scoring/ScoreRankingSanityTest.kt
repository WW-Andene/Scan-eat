package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.engine.nutrition.parseIngredients
import fr.scanneat.domain.engine.nutrition.withEstimatedMicronutrients
import fr.scanneat.domain.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ranking sanity: products every nutrition reference orders the same way. */
class ScoreRankingSanityTest {
    private fun p(name: String, cat: ProductCategory, nova: NovaClass, ingr: String, kcal: Double, fat: Double, sat: Double, carbs: Double, sug: Double, fib: Double, prot: Double, salt: Double) =
        Product(name, cat, nova, parseIngredients(ingr), NutritionPer100g(kcal, fat, sat, carbs, sug, fiberG = fib, proteinG = prot, saltG = salt))

    @Test fun `scores rank common products sensibly`() {
        val items = listOf(
            p("Eau minérale", ProductCategory.BEVERAGE_WATER, NovaClass.UNPROCESSED, "eau minérale naturelle", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            p("Pomme", ProductCategory.FRESH_PRODUCE, NovaClass.UNPROCESSED, "pomme", 52.0, 0.2, 0.0, 14.0, 10.0, 2.4, 0.3, 0.0),
            p("Yaourt nature", ProductCategory.YOGURT, NovaClass.PROCESSED, "lait entier, ferments lactiques", 61.0, 3.3, 2.1, 4.7, 4.7, 0.0, 3.5, 0.1),
            p("Flocons d'avoine", ProductCategory.BREAKFAST_CEREAL, NovaClass.UNPROCESSED, "flocons d'avoine complets", 375.0, 7.0, 1.3, 59.0, 1.0, 10.0, 13.0, 0.0),
            p("Pain blanc", ProductCategory.BREAD, NovaClass.PROCESSED, "farine de blé, eau, sel, levure", 270.0, 1.5, 0.3, 54.0, 3.0, 2.5, 9.0, 1.2),
            p("Cola", ProductCategory.BEVERAGE_SOFT, NovaClass.ULTRA_PROCESSED, "eau gazéifiée, sucre, colorant E150d, acidifiant E338, arômes, caféine", 42.0, 0.0, 0.0, 10.6, 10.6, 0.0, 0.0, 0.0),
            p("Chips", ProductCategory.SNACK_SALTY, NovaClass.PROCESSED, "pommes de terre, huile de tournesol, sel", 540.0, 34.0, 3.0, 50.0, 0.5, 4.5, 6.0, 1.3),
            p("Barre chocolatée", ProductCategory.SNACK_SWEET, NovaClass.ULTRA_PROCESSED, "sucre, sirop de glucose, huile de palme, lait écrémé en poudre, beurre de cacao, émulsifiant E322, arômes", 480.0, 23.0, 12.0, 62.0, 55.0, 1.0, 4.5, 0.4),
        )
        val score = items.associate { raw ->
            val it = raw.copy(nutrition = raw.nutrition.withEstimatedMicronutrients(raw.category))
            it.name to scoreProduct(it, "fr").score
        }
        fun below(lower: String, higher: String) =
            assertTrue("$lower (${score[lower]}) should score below $higher (${score[higher]})", score.getValue(lower) < score.getValue(higher))
        // Energy-dense crisps were scored A, level with an apple (no cross-category energy penalty).
        below("Chips", "Pomme"); below("Chips", "Yaourt nature"); below("Chips", "Flocons d'avoine")
        below("Cola", "Eau minérale"); below("Cola", "Pain blanc")
        below("Barre chocolatée", "Chips"); below("Barre chocolatée", "Pain blanc")
    }
}
