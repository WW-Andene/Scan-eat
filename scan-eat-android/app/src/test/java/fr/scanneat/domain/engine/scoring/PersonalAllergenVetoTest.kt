package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.AllergenSeverity
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.Profile
import org.junit.Assert.*
import org.junit.Test

/** Roadmap C10: a confirmed allergen must zero the personal score. */
class PersonalAllergenVetoTest {

    private val nutrition = NutritionPer100g(
        energyKcal = 120.0, fatG = 2.0, saturatedFatG = 0.5, carbsG = 20.0,
        sugarsG = 2.0, fiberG = 4.0, proteinG = 6.0, saltG = 0.1,
    )

    private fun product(ingredients: List<String>, traces: List<String> = emptyList()) = Product(
        name = "Test",
        category = ProductCategory.OTHER,
        novaClass = NovaClass.UNPROCESSED,
        ingredients = ingredients.map { Ingredient(name = it, category = IngredientCategory.FOOD) },
        nutrition = nutrition,
        declaredTracesTags = traces,
    )

    @Test
    fun `confirmed allergen vetoes the personal score with a Contient reason`() {
        val p = product(listOf("flocons d'avoine", "arachides"))
        val result = computePersonalScore(scoreProduct(p), p, Profile(allergens = setOf("peanuts")))
        assertTrue(result.veto)
        assertEquals(0, result.personalScore)
        assertTrue(result.dietReason!!.startsWith("Contient : Arachides"))
    }

    @Test
    fun `english reason when lang is en`() {
        val p = product(listOf("peanuts"))
        val result = computePersonalScore(scoreProduct(p), p, Profile(allergens = setOf("peanuts")), lang = "en")
        assertTrue(result.dietReason!!.startsWith("Contains: Peanuts"))
    }

    @Test
    fun `trace-only hit stays informational`() {
        val p = product(listOf("flocons d'avoine"), traces = listOf("en:peanuts"))
        val result = computePersonalScore(scoreProduct(p), p, Profile(allergens = setOf("peanuts")))
        assertFalse(result.veto)
        assertTrue(result.allergenHits.any { it.key == "peanuts" && it.isTraceOnly })
        assertTrue(result.personalScore > 0)
    }

    @Test
    fun `allergen not declared by the profile has no effect`() {
        val p = product(listOf("arachides"))
        val result = computePersonalScore(scoreProduct(p), p, Profile(allergens = setOf("lactose")))
        assertFalse(result.veto)
    }

    // ---- N6: severity ----

    @Test
    fun `severe allergy also vetoes on traces`() {
        val p = product(listOf("flocons d'avoine"), traces = listOf("en:peanuts"))
        val profile = Profile(allergens = setOf("peanuts"), allergenSeverities = mapOf("peanuts" to AllergenSeverity.SEVERE))
        val result = computePersonalScore(scoreProduct(p), p, profile)
        assertTrue(result.veto)
        assertTrue(result.dietReason!!.contains("allergie sévère"))
    }

    @Test
    fun `intolerance lowers the score without veto`() {
        val p = product(listOf("flocons d'avoine", "lait"))
        val audit = scoreProduct(p)
        val profile = Profile(allergens = setOf("lactose"), allergenSeverities = mapOf("lactose" to AllergenSeverity.INTOLERANCE))
        val result = computePersonalScore(audit, p, profile)
        assertFalse(result.veto)
        assertTrue(result.adjustments.any { it.reason.contains("intolérance") && it.points < 0 })
        assertTrue(result.personalScore < audit.score)
    }

    @Test
    fun `intolerance traces have no effect`() {
        val p = product(listOf("flocons d'avoine"), traces = listOf("en:milk"))
        val profile = Profile(allergens = setOf("lactose"), allergenSeverities = mapOf("lactose" to AllergenSeverity.INTOLERANCE))
        assertFalse(computePersonalScore(scoreProduct(p), p, profile).veto)
    }
}
