package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.Profile
import org.junit.Assert.*
import org.junit.Test

/** Roadmap D10: per-profile verdicts on one scan. */
class FamilyCheckTest {

    private val nutrition = NutritionPer100g(
        energyKcal = 120.0, fatG = 2.0, saturatedFatG = 0.5, carbsG = 20.0,
        sugarsG = 2.0, fiberG = 4.0, proteinG = 6.0, saltG = 0.1,
    )

    private fun product(ingredients: List<String>, traces: List<String> = emptyList()) = Product(
        name = "Test", category = ProductCategory.OTHER, novaClass = NovaClass.UNPROCESSED,
        ingredients = ingredients.map { Ingredient(name = it, category = IngredientCategory.FOOD) },
        nutrition = nutrition, declaredTracesTags = traces,
    )

    private val parent = Profile(id = "default", name = "Parent")
    private val child = Profile(id = "kid", name = "Enfant", allergens = setOf("peanuts"))
    private val sibling = Profile(id = "sib", name = "Ado", allergens = setOf("nuts"))

    @Test
    fun `allergic child is blocked while parent is ok`() {
        val p = product(listOf("flocons d'avoine", "arachides"))
        val v = familyVerdicts(scoreProduct(p), p, listOf(parent, child)).associateBy { it.profileId }
        assertEquals(FamilyStatus.OK, v.getValue("default").status)
        assertEquals(FamilyStatus.BLOCKED, v.getValue("kid").status)
        assertTrue(v.getValue("kid").reason!!.contains("Arachides"))
        assertEquals(0, v.getValue("kid").score)
    }

    @Test
    fun `traces give a caution, not a block`() {
        val p = product(listOf("flocons d'avoine"), traces = listOf("en:nuts"))
        val v = familyVerdicts(scoreProduct(p), p, listOf(parent, sibling)).associateBy { it.profileId }
        assertEquals(FamilyStatus.CAUTION, v.getValue("sib").status)
        assertEquals(FamilyStatus.OK, v.getValue("default").status)
    }

    @Test
    fun `no ingredient list is unverified for profiles with allergens or a diet`() {
        val p = product(emptyList())
        val vegan = Profile(id = "v", name = "V", diet = DietKey.entries.first { it != DietKey.NONE })
        val v = familyVerdicts(scoreProduct(p), p, listOf(parent, child, vegan)).associateBy { it.profileId }
        assertEquals(FamilyStatus.OK, v.getValue("default").status)
        assertEquals(FamilyStatus.UNVERIFIED, v.getValue("kid").status)
        assertNotEquals(FamilyStatus.OK, v.getValue("v").status)
    }
}
