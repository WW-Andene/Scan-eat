package fr.scanneat.service

import fr.scanneat.model.ImageDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

// ============================================================================
// SCORE SERVICE TESTS
//
// Covers scoreBarcode()'s three outcomes: OFF miss, OFF-only (non-sparse
// record, no augmentation needed), and OFF+LLM merge (sparse record with
// images supplied). Both OffService and GroqService accept an injectable
// HttpClientEngine (see OffService.kt / GroqService.kt) purely so tests can
// substitute MockEngine instead of the real network calls.
// ============================================================================

// Minimal stand-ins for GroqService's private wire types (ChatRequest/Choice/
// AssistantMsg), used only to build a well-formed, correctly-escaped mocked
// Groq chat-completion response body.
@Serializable
private data class TestChatResponse(val choices: List<TestChoice>)

@Serializable
private data class TestChoice(val message: TestMsg)

// role has no default - kotlinx.serialization's default Json config omits
// fields equal to their default value, and GroqService's own AssistantMsg
// requires "role" (no default there), so a defaulted-and-omitted role here
// would fail to decode.
@Serializable
private data class TestMsg(val role: String, val content: String)

private fun mockChatCompletion(content: String): String =
    Json.encodeToString(TestChatResponse(listOf(TestChoice(TestMsg(role = "assistant", content = content)))))

/** A GroqService whose engine fails any request - used where augmentation must not be attempted. */
private fun unusedGroqService() = GroqService(MockEngine { respond("unexpected Groq call", HttpStatusCode.InternalServerError) })

private fun jsonResponding(body: String) = MockEngine { _ ->
    respond(content = body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
}

class ScoreServiceTest {

    @Test
    fun `scoreBarcode returns OffMiss when OFF has no match`() = runBlocking {
        val offService = OffService(jsonResponding("""{"status":0}"""))
        val scoreService = ScoreService(offService, unusedGroqService())

        val outcome = scoreService.scoreBarcode("1234567890123", emptyList(), { null }, "fr", DEFAULT_GROQ_MODEL)

        assertEquals(BarcodeScoreOutcome.OffMiss, outcome)
    }

    @Test
    fun `scoreBarcode returns OFF-only data when the OFF record is not sparse`() = runBlocking {
        val offJson = """
            {"status":1,"product":{
                "product_name":"Corn Flakes",
                "categories_tags":["en:breakfast-cereals"],
                "ingredients_text":"corn, sugar, salt",
                "nova_group":4,
                "nutriments":{"energy-kcal_100g":360,"proteins_100g":7,"carbohydrates_100g":84,"sugars_100g":8,"fat_100g":1,"fiber_100g":3,"salt_100g":1.1}
            }}
        """.trimIndent()
        val offService = OffService(jsonResponding(offJson))
        val scoreService = ScoreService(offService, unusedGroqService())

        // No images supplied either, so augmentation couldn't be attempted even if
        // the record were sparse - this isolates the "not sparse" branch.
        val outcome = scoreService.scoreBarcode("1234567890123", emptyList(), { null }, "fr", DEFAULT_GROQ_MODEL)

        assertIs<BarcodeScoreOutcome.Success>(outcome)
        assertEquals("openfoodfacts", outcome.response.source)
        assertEquals("Corn Flakes", outcome.response.product.name)
    }

    @Test
    fun `scoreBarcode merges a sparse OFF record with LLM augmentation when images are supplied`() = runBlocking {
        // Sparse: only a product name - no nutriments/ingredients/category, so
        // isOffSparse() is true and augmentation is attempted.
        val offService = OffService(jsonResponding("""{"status":1,"product":{"product_name":"Mystery Snack"}}"""))

        val labelJson = """
            {"name":"Mystery Snack","category":"snack_salty","nova_class":4,
             "ingredients":[{"name":"potato","percentage":null,"e_number":null,"category":"food","is_whole_food":true}],
             "nutrition":{"energy_kcal":520,"fat_g":30,"saturated_fat_g":3,"carbs_g":55,"sugars_g":1,"fiber_g":4,"protein_g":6,"salt_g":1.5},
             "organic":false,"whole_grain_primary":false,"fermented":false,"has_health_claims":false,
             "has_misleading_marketing":false,"named_oils":null,"origin":null,"weight_g":null,"barcode":null,
             "allergen_declarations":[]}
        """.trimIndent()
        val groqService = GroqService(jsonResponding(mockChatCompletion(labelJson)))
        val scoreService = ScoreService(offService, groqService)

        val images = listOf(ImageDto(base64 = "dGVzdA==", mime = "image/jpeg"))
        val outcome = scoreService.scoreBarcode("1234567890123", images, { "fake-groq-key" }, "fr", DEFAULT_GROQ_MODEL)

        assertIs<BarcodeScoreOutcome.Success>(outcome)
        assertEquals("merged", outcome.response.source)
        assertEquals("Mystery Snack", outcome.response.product.name)
    }
}
