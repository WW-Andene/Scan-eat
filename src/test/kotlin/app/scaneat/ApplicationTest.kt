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
}
