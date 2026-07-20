package fr.scanneat.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

// ============================================================================
// GROQ SERVICE TESTS
//
// GroqService now accepts an injectable HttpClientEngine (see GroqService.kt)
// purely so these tests can substitute MockEngine instead of hitting the real,
// paid Groq API over the network.
// ============================================================================

class GroqServiceTest {

    private fun chatResponseJson(content: String) =
        """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""

    @Test
    fun `successful call returns the assistant message content`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = chatResponseJson("bonjour"),
                status  = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val service = GroqService(engine)

        val result = service.complete(prompt = "hi", apiKey = "test-key")

        assertEquals("bonjour", result)
    }

    // F-CRIT-05 regression test. CancellationException is a subtype of Exception, so
    // the retry loop's `runCatching { ... }.onFailure { ... }` previously caught it the
    // same as any real failure - a cancelled request (client disconnect mid-call) fell
    // through to the retry logic and got retried against the paid Groq API instead of
    // propagating immediately. See the `if (e is CancellationException) throw e` check
    // added at the top of complete()'s onFailure block.
    @Test
    fun `cancellation from the network call is not retried and propagates immediately`() = runBlocking {
        var callCount = 0
        val engine = MockEngine { _ ->
            callCount++
            throw CancellationException("simulated client disconnect")
        }
        val service = GroqService(engine)

        assertFailsWith<CancellationException> {
            service.complete(prompt = "hi", apiKey = "test-key", maxRetries = 3)
        }
        assertEquals(1, callCount, "a cancelled request must not be retried against Groq")
    }
}
