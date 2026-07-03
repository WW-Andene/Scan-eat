package app.scaneat

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test fun `score endpoint scores provided product`() = testApplication {
        application { scanEatModule() }
        val response = client.post("/api/score") {
            contentType(ContentType.Application.Json)
            setBody("""
                {"product":{"name":"Yaourt nature","category":"yogurt","nova_class":1,"ingredients":[{"name":"lait"}],"nutrition":{"energy_kcal":65,"protein_g":5,"sugars_g":4,"saturated_fat_g":1,"salt_g":0.1}}}
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("audit"))
    }

    @Test fun `invalid barcode is rejected`() = testApplication {
        application { scanEatModule() }
        val response = client.post("/api/score") {
            contentType(ContentType.Application.Json)
            setBody("""{"barcode":"not-a-barcode"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test fun `native image identify always returns a fallback result`() = testApplication {
        application { scanEatModule() }
        val response = client.post("/api/identify") {
            contentType(ContentType.Application.Json)
            setBody("""{"images":[{"base64":"AAAA","mime":"image/jpeg"}]}""")
        }
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("Produit scanné par photo"))
        assertTrue(body.contains("native_image_fallback"))
        assertTrue(body.contains("OCR/IA non configuré côté Kotlin natif"))
    }

    @Test fun `pwa shell is not served by the kotlin native api`() = testApplication {
        application { scanEatModule() }
        val manifest = client.get("/manifest.webmanifest")
        val index = client.get("/index.html")
        assertEquals(HttpStatusCode.NotFound, manifest.status)
        assertEquals(HttpStatusCode.NotFound, index.status)
    }
}
