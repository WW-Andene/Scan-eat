package fr.scanneat.domain.engine.medication

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import org.junit.Assert.*
import org.junit.Test

/** Roadmap A7: new sourced food-drug interactions. */
class FoodDrugA7Test {
    private fun cautions(food: String, vararg ingredients: String, med: String) =
        checkFoodDrugInteractions(food, ingredients.map { Ingredient(name = it, category = IngredientCategory.FOOD) }, setOf(med), "fr")

    @Test fun `red yeast rice with any statin, rosuvastatin included`() {
        assertTrue(cautions("Complément cholestérol", "levure de riz rouge", med = "Crestor 5 mg").any { "monacoline" in it })
        assertTrue(cautions("Complément", "levure de riz rouge", med = "atorvastatine").any { "monacoline" in it })
    }

    @Test fun `dairy with ciprofloxacin, not coconut milk`() {
        assertTrue(cautions("Yaourt nature", "lait", med = "Ciflox 500").isNotEmpty())
        assertTrue(cautions("Curry", "lait de coco", med = "ciprofloxacine").isEmpty())
    }

    @Test fun `alcohol with metronidazole, not wine vinegar`() {
        assertTrue(cautions("Vin rouge", "raisin", med = "Flagyl").any { "antabuse" in it })
        assertTrue(cautions("Vinaigrette", "vinaigre de vin", "huile", med = "Flagyl").isEmpty())
    }

    @Test fun `tyramine with linezolid`() {
        assertTrue(cautions("Parmesan", "lait", med = "Zyvoxid").any { "tyramine" in it })
    }

    @Test fun `no medication, no caution`() {
        assertTrue(checkFoodDrugInteractions("Vin rouge", emptyList(), emptySet(), "fr").isEmpty())
    }
}
