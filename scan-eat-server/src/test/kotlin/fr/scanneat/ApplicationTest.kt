package fr.scanneat

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun `health endpoint returns 200`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ok"), "Health body: $body")
    }

    @Test
    fun `score endpoint rejects empty body with 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.post("/api/score") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        // No images + no barcode → 400
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `identify endpoint returns 503 when no Groq key set`() = testApplication {
        // Clear GROQ_API_KEY for this test by not setting it (env is clean in CI)
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.post("/api/identify") {
            contentType(ContentType.Application.Json)
            setBody("""{"images":[{"base64":"dGVzdA==","mime":"image/jpeg"}]}""")
        }
        // Without a key we expect 503 or 400
        assertTrue(
            response.status in listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.BadRequest),
            "Expected 503 or 400, got ${response.status}"
        )
    }
}
