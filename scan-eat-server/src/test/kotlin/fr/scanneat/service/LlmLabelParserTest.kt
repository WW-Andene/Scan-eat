package fr.scanneat.service

import fr.scanneat.model.ImageDto
import fr.scanneat.shared.IngredientCategory
import fr.scanneat.shared.NovaClass
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

// ============================================================================
// LLM LABEL PARSER TESTS
//
// M-14: LlmProductDto/LlmIngredientDto/LlmNutritionDto now use camelCase
// Kotlin property names with @SerialName pinning each to the snake_case JSON
// key the label prompt actually asks the model for. These tests round-trip a
// snake_case JSON payload (as Groq would actually return it) through
// GroqService.parseLabel() and assert every field landed on the right domain
// property, so a mismatched/missing @SerialName would fail loudly instead of
// silently reading as null.
// ============================================================================

// Named distinctly from ScoreServiceTest.kt's identical helper types - Kotlin
// `private` top-level classes are still package-scoped at the bytecode level,
// so two files in the same package declaring a class of the same name collide.
@Serializable
private data class LabelTestChatResponse(val choices: List<LabelTestChoice>)

@Serializable
private data class LabelTestChoice(val message: LabelTestMsg)

@Serializable
private data class LabelTestMsg(val role: String, val content: String)

private fun mockChatCompletion(content: String): String =
    Json.encodeToString(LabelTestChatResponse(listOf(LabelTestChoice(LabelTestMsg(role = "assistant", content = content)))))

private fun groqReturning(content: String) = GroqService(MockEngine { _ ->
    respond(
        content = mockChatCompletion(content),
        status  = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
})

class LlmLabelParserTest {

    // Every snake_case key here mirrors exactly what labelPrompt() in
    // LlmLabelParser.kt asks the model to emit.
    private val labelJson = """
        {
          "name": "Pain complet",
          "category": "bread",
          "nova_class": 3,
          "ingredients": [
            { "name": "farine complete", "percentage": 60.0, "e_number": null, "category": "food", "is_whole_food": true },
            { "name": "E322", "percentage": null, "e_number": "E322", "category": "additive", "is_whole_food": false }
          ],
          "nutrition": {
            "energy_kcal": 245, "fat_g": 3.2, "saturated_fat_g": 0.6, "carbs_g": 41,
            "sugars_g": 2.5, "added_sugars_g": 1.0, "fiber_g": 6.5, "protein_g": 9.5,
            "salt_g": 1.1, "trans_fat_g": 0.0, "iron_mg": 2.1, "calcium_mg": 30,
            "magnesium_mg": 45, "potassium_mg": 210, "zinc_mg": 1.2,
            "vit_a_ug": 0, "vit_c_mg": 0, "vit_d_ug": 0, "vit_e_mg": 0.5,
            "vit_k_ug": 3, "b12_ug": 0
          },
          "organic": true,
          "whole_grain_primary": true,
          "fermented": false,
          "has_health_claims": true,
          "has_misleading_marketing": false,
          "named_oils": null,
          "origin": "France",
          "weight_g": 500,
          "barcode": "3245678901234",
          "allergen_declarations": ["gluten"]
        }
    """.trimIndent()

    @Test
    fun `snake_case JSON from the label prompt round-trips onto the camelCase Product fields`() = runBlocking {
        val groqService = groqReturning(labelJson)

        val result = groqService.parseLabel(images = listOf(ImageDto("dGVzdA==")), apiKey = "test-key")
        val product = result.product

        assertEquals("Pain complet", product.name)
        assertEquals(NovaClass.PROCESSED, product.novaClass)
        assertEquals(2, product.ingredients.size)
        assertEquals("E322", product.ingredients[1].eNumber)
        assertEquals(IngredientCategory.ADDITIVE, product.ingredients[1].category)
        assertTrue(product.ingredients[0].isWholeFood == true)

        // nutrition: snake_case JSON keys -> camelCase NutritionPer100g fields
        assertEquals(245.0, product.nutrition.energyKcal)
        assertEquals(3.2, product.nutrition.fatG)
        assertEquals(41.0, product.nutrition.carbsG)
        assertEquals(9.5, product.nutrition.proteinG)
        assertEquals(1.1, product.nutrition.saltG)
        assertEquals(2.1, product.nutrition.ironMg)
        assertEquals(210.0, product.nutrition.potassiumMg)

        assertTrue(product.organic)
        assertTrue(product.wholeGrainPrimary)
        assertTrue(product.hasHealthClaims)
        assertEquals("France", product.origin)
        assertEquals(500.0, product.weightG)
        assertEquals(listOf("en:gluten"), product.declaredAllergenTags)
        assertEquals("3245678901234", result.barcode)
    }
}
