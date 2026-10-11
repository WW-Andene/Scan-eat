package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Test

// Coverage audit: plural forms (blocked by the trailing word-boundary
// lookahead), English label terms, and common derivative ingredients for each
// EU Annex II allergen - plus known false positives (cocoa butter, cream of
// tartar) that aren't the allergen at all.
class AllergenCoverageTest {

    private val zero = NutritionPer100g(
        energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0,
        sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0,
    )

    private fun keys(ingredient: String): Set<String> = detectAllergens(
        Product(
            name = "t", category = ProductCategory.OTHER, novaClass = NovaClass.UNPROCESSED,
            ingredients = listOf(Ingredient(name = ingredient, category = IngredientCategory.FOOD)),
            nutrition = zero,
        ),
    ).map { it.key }.toSet()

    private val mustDetect = mapOf(
        "peanuts" to listOf("arachides", "cacahuètes", "peanuts", "huile d'arachide", "groundnut oil"),
        "nuts" to listOf("almonds", "almond", "hazelnuts", "walnuts", "cashews", "pistachio", "brazil nuts",
            "pecans", "praliné", "pâte de pralin", "massepain", "gianduja", "frangipane"),
        "eggs" to listOf("egg", "eggs", "egg yolk", "whole egg powder"),
        "lactose" to listOf("milk", "skimmed milk powder", "butter", "cream", "cheese", "caséinate de sodium",
            "sodium caseinate", "babeurre", "buttermilk", "ghee", "mozzarella", "parmesan", "emmental",
            "ricotta", "mascarpone", "lactosérum", "yogurt", "whey protein"),
        "gluten" to listOf("wheat flour", "barley malt", "rye", "oats", "spelt"),
        "soy" to listOf("soy lecithin", "soya", "soybean oil"),
        "fish" to listOf("fish", "fish sauce", "sardines", "maquereaux", "anchovies", "salmon", "tuna", "cod", "nuoc mam"),
        "sulfites" to listOf("sulphites", "sulfur dioxide", "sulphur dioxide", "disulfite de potassium", "E 220"),
        "celery" to listOf("celeriac", "céleri-rave"),
        "lupin" to listOf("lupine flour"),
        "mustard" to listOf("moutarde de Dijon"),
        "sesame" to listOf("graines de sésame", "sesame seeds"),
    )

    private val mustNotDetect = mapOf(
        "lactose" to listOf("beurre de cacao", "cocoa butter", "crème de tartre", "cream of tartar", "lait de coco", "coconut milk",
            "lait d'amande", "beurre de karité", "peanut butter"),
        "nuts" to listOf("noix de coco", "noix de muscade", "coconut", "nutmeg"),
        "eggs" to listOf("eggplant"),
    )

    @Test
    fun `every Annex II term and derivative is detected`() {
        val misses = mustDetect.flatMap { (key, terms) -> terms.filter { key !in keys(it) }.map { "$key <- $it" } }
        assertEquals("missed allergens", emptyList<String>(), misses)
    }

    @Test
    fun `non-allergen look-alikes are not flagged`() {
        val falseHits = mustNotDetect.flatMap { (key, terms) -> terms.filter { key in keys(it) }.map { "$key <- $it" } }
        assertEquals("false positives", emptyList<String>(), falseHits)
    }
}

class AllergenFruitsACoqueTest {
    @Test
    fun `fruits a coque is the EU French term for tree nuts`() {
        val p = fr.scanneat.domain.model.Product(
            name = "t", category = fr.scanneat.domain.model.ProductCategory.OTHER,
            novaClass = fr.scanneat.domain.model.NovaClass.UNPROCESSED,
            ingredients = listOf(fr.scanneat.domain.model.Ingredient(name = "fruits à coque", category = fr.scanneat.domain.model.IngredientCategory.FOOD)),
            nutrition = fr.scanneat.domain.model.NutritionPer100g(0.0, 0.0, 0.0, 0.0, 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0),
        )
        org.junit.Assert.assertTrue(detectAllergens(p).any { it.key == "nuts" })
    }
}
