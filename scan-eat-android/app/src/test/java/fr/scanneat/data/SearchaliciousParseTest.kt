package fr.scanneat.data

import com.squareup.moshi.Moshi
import fr.scanneat.data.remote.api.SearchaliciousResponse
import fr.scanneat.data.remote.api.salCategoryQuery
import org.junit.Assert.*
import org.junit.Test

/** Roadmap A8: a real Search-a-licious response (captured 2026-10-14) parses into the v2 product shape. */
class SearchaliciousParseTest {
    @Test fun `live sample parses, brands list joined`() {
        val json = javaClass.classLoader!!.getResource("searchalicious_sample.json")!!.readText()
        val resp = Moshi.Builder().build().adapter(SearchaliciousResponse::class.java).fromJson(json)!!
        val dto = resp.hits!!.first().toOffProductDto()
        assertNotNull(dto.code); assertNotNull(dto.productName)
        assertNotNull(dto.brands); assertFalse(dto.brands!!.startsWith("["))
        assertTrue(dto.nutriments!!.containsKey("energy-kcal_100g"))
    }

    @Test fun `category query is a quoted Lucene filter`() {
        assertEquals("categories_tags:\"en:plain-yogurts\"", salCategoryQuery("en:plain-yogurts"))
    }
}
