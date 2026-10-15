package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.engine.medication.checkFoodDrugInteractions
import fr.scanneat.domain.engine.medication.conditionMedicationCautions
import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AddictionAndSugarTest {
    private val n0 = NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0)
    private fun p(name: String, vararg ing: String, nutrition: NutritionPer100g = n0, cat: ProductCategory = ProductCategory.OTHER) =
        Product(name = name, category = cat, novaClass = NovaClass.PROCESSED,
            ingredients = ing.map { Ingredient(name = it, category = IngredientCategory.FOOD) }, nutrition = nutrition)

    private fun run(product: Product, vararg cond: String): Pair<String?, List<PersonalAdjustment>> {
        val adj = mutableListOf<PersonalAdjustment>()
        val veto = checkAddictionConditions(product, cond.toSet(), "fr", productContainsAlcohol(product), adj)
        return veto to adj
    }

    @Test fun `quitting alcohol vetoes hidden alcohol, not vinegar`() {
        assertNotNull(run(p("Tiramisu", "mascarpone", "marsala"), "alcohol_quit").first)
        assertNull(run(p("Vinaigrette", "vinaigre de vin", "huile"), "alcohol_quit").first)
    }

    @Test fun `alcohol-free beer is a caution when quitting alcohol`() {
        val (veto, adj) = run(p("Bière sans alcool", "eau", "malt d'orge"), "alcohol_quit")
        assertNull(veto)
        assertTrue(adj.any { "1,2 % vol" in it.reason })
    }

    @Test fun `caffeine caution after quitting tobacco`() {
        assertTrue(run(p("Café moulu", "café"), "tobacco_quit").second.any { "caféine" in it.reason })
        assertTrue(run(p("Café moulu", "café"), "smoker").second.isEmpty())
    }

    @Test fun `beta-carotene supplement for smokers, not the colour`() {
        assertTrue(run(p("Complément alimentaire vision", "bêta-carotène", "gélule"), "smoker").second.any { "EFSA 2012" in it.reason })
        assertTrue(run(p("Jus de carotte", "carotte", "colorant : bêta-carotène"), "smoker").second.isEmpty())
    }

    @Test fun `no free sugars diet follows WHO definition`() {
        assertFalse(checkDiet(p("Biscuit", "farine", "sucre"), DietKey.NO_FREE_SUGARS).compliant)
        assertFalse(checkDiet(p("Yaourt", "lait", "miel"), DietKey.NO_FREE_SUGARS).compliant)
        assertFalse(checkDiet(p("Boisson", "eau", "jus de pomme"), DietKey.NO_FREE_SUGARS).compliant)
        assertTrue(checkDiet(p("Compote sans sucres ajoutés", "pommes"), DietKey.NO_FREE_SUGARS).compliant)
        assertTrue(checkDiet(p("Yaourt nature", "lait", "ferments"), DietKey.NO_FREE_SUGARS).compliant)
    }

    @Test fun `disulfiram reacts to wine vinegar`() {
        assertTrue(checkFoodDrugInteractions("Vinaigrette", listOf(Ingredient(name = "vinaigre de vin", category = IngredientCategory.FOOD)), setOf("Esperal 500"), "fr").any { "antabuse" in it })
    }

    @Test fun `medication cautions for quitting tobacco and alcohol`() {
        assertTrue(conditionMedicationCautions(setOf("tobacco_quit"), listOf("Clozapine 100 mg"), "fr").any { "Clozapine" in it })
        assertTrue(conditionMedicationCautions(setOf("tobacco_quit"), listOf("Doliprane"), "fr").isEmpty())
        assertTrue(conditionMedicationCautions(setOf("alcohol_quit"), emptyList(), "fr").any { "0 980 980 930" in it })
    }
}
