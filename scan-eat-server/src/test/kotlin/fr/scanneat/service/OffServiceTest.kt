package fr.scanneat.service

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

// ============================================================================
// OFF SERVICE TESTS
//
// Covers barcodeCandidates()'s GTIN-14 -> EAN-13 and UPC-E -> UPC-A checksum
// math (pure functions, no network), and the first-match-wins concurrent
// lookup behavior fixed under F-HIGH-08. OffService now accepts an injectable
// HttpClientEngine (see OffService.kt) purely so tests can substitute
// MockEngine instead of hitting the real Open Food Facts API.
// ============================================================================

class OffServiceTest {

    // ---- barcodeCandidates(): checksum/conversion math -------------------

    @Test
    fun `UPC-E core expands to the correct UPC-A and EAN-13 candidates`() {
        // "425261" is a well-known real UPC-E core (Kellogg's Corn Flakes) whose
        // published UPC-A expansion is "042100005264".
        val candidates = barcodeCandidates("425261")
        assertEquals(listOf("425261", "042100005264", "0042100005264"), candidates)
    }

    @Test
    fun `an 8-digit UPC-E (number system + core + check digit) expands to the same UPC-A as the bare core`() {
        // "0" (number system) + "425261" (core) + "4" (check digit, discarded and
        // recomputed) - the number system and trailing check digit must not change
        // the recovered core, so this reaches the same UPC-A as the bare 6-digit form.
        val candidates = barcodeCandidates("04252614")
        assertTrue("042100005264" in candidates, "expected 042100005264 in $candidates")
    }

    @Test
    fun `GTIN-14 case code strips the packaging indicator and recomputes the EAN-13 check digit`() {
        val candidates = barcodeCandidates("10012345678905")
        assertEquals(listOf("10012345678905", "00012345678905", "0012345678905"), candidates)
        // The last candidate must be a genuinely valid 13-digit EAN-13: recomputing
        // the standard mod-10 check digit over its own first 12 digits must
        // reproduce its own last digit.
        val ean13 = candidates.last()
        assertEquals(13, ean13.length)
        assertEquals(ean13.last().digitToInt(), eanCheckDigitFor(ean13.dropLast(1)))
    }

    /**
     * Independent re-implementation of the standard EAN-13 mod-10 check digit,
     * used only to validate the fixture data above without relying on the
     * production code under test for its own verification.
     */
    private fun eanCheckDigitFor(payload12: String): Int {
        val sum = payload12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - (sum % 10)) % 10
    }

    // ---- fetchProduct(): first-match-wins concurrency (F-HIGH-08) --------

    @Test
    fun `fetchProduct returns as soon as the first candidate matches, without waiting for a slower losing candidate`() = runBlocking {
        // A 12-digit barcode with no leading zero produces exactly two candidates:
        // itself, then its "0"-prefixed EAN-13 form (see barcodeCandidates()).
        val barcode = "042100005264"
        val slowCandidatePathFragment = "0$barcode.json"

        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains(slowCandidatePathFragment)) {
                // The losing, lower-priority candidate - must get cancelled instead
                // of being waited on once the first candidate below already matched.
                delay(5_000)
                respond(
                    content = """{"status":0}""",
                    status  = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = """{"status":1,"product":{"product_name":"Corn Flakes"}}""",
                    status  = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val service = OffService(engine)

        val start = System.currentTimeMillis()
        val product = withTimeout(2_000) { service.fetchProduct(barcode) }
        val elapsedMs = System.currentTimeMillis() - start

        assertEquals("Corn Flakes", product?.productName)
        assertTrue(
            elapsedMs < 2_000,
            "fetchProduct should return promptly instead of waiting on the cancelled slow candidate (took ${elapsedMs}ms)",
        )
    }
}
