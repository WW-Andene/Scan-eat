package fr.scanneat.routing

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.*

// ============================================================================
// FETCH-RECIPE SCHEMA.ORG PARSER TESTS
//
// The previous test in ScoringEngineTest.kt asserted a fixture string
// contained a substring it itself wrote — it exercised none of the actual
// parser and could never fail. These tests call parseSchemaOrgRecipe()
// directly (made internal for this purpose) against realistic JSON-LD
// fixtures, including the F5 (servings) and F6 (nutrition units) fixes.
// ============================================================================

class FetchRecipeRouteTest {

    private fun html(jsonLd: String) = """
        <html><head>
        <script type="application/ld+json">
        $jsonLd
        </script>
        </head></html>
    """.trimIndent()

    @Test
    fun `extracts name, ingredients, and unwrapped HowToStep text`() {
        val recipe = parseSchemaOrgRecipe(
            html(
                """
                {"@type":"Recipe","name":"Tarte aux pommes","recipeYield":"6 personnes",
                 "recipeIngredient":["300g farine","2 pommes"],
                 "recipeInstructions":[{"@type":"HowToStep","text":"Mélanger la farine."}]}
                """.trimIndent()
            ),
            "https://example.com/tarte",
        )
        assertNotNull(recipe)
        assertEquals("Tarte aux pommes", recipe.name)
        assertEquals(listOf("300g farine", "2 pommes"), recipe.ingredients)
        assertEquals(listOf("Mélanger la farine."), recipe.steps)
        assertEquals("https://example.com/tarte", recipe.sourceUrl)
    }

    @Test
    fun `parses servings from a numeric string yield`() {
        val recipe = parseSchemaOrgRecipe(
            html("""{"@type":"Recipe","name":"Soupe","recipeYield":"6 personnes"}"""),
            "https://example.com",
        )
        assertEquals("6", recipe?.servings)
    }

    @Test
    fun `F5 regression - servings is null rather than a fabricated 1 when recipeYield is absent`() {
        val recipe = parseSchemaOrgRecipe(
            html("""{"@type":"Recipe","name":"Soupe"}"""),
            "https://example.com",
        )
        assertNotNull(recipe)
        assertNull(recipe.servings)
    }

    @Test
    fun `parses ISO 8601 duration into minutes`() {
        val recipe = parseSchemaOrgRecipe(
            html("""{"@type":"Recipe","name":"Ragout","totalTime":"PT1H30M"}"""),
            "https://example.com",
        )
        assertEquals(90, recipe?.cookTimeMin)
    }

    @Test
    fun `F6 regression - nutrition values with units are parsed, not discarded`() {
        val recipe = parseSchemaOrgRecipe(
            html(
                """
                {"@type":"Recipe","name":"Salade",
                 "nutrition":{"calories":"350 calories","proteinContent":"12g","fatContent":"8g","carbohydrateContent":"40g"}}
                """.trimIndent()
            ),
            "https://example.com",
        )
        val nutrition = recipe?.nutrition
        assertNotNull(nutrition)
        assertEquals(350.0, nutrition.kcal)
        assertEquals(12.0, nutrition.proteinG)
        assertEquals(8.0, nutrition.fatG)
        assertEquals(40.0, nutrition.carbsG)
    }

    @Test
    fun `finds the Recipe object nested inside an at-graph array`() {
        val recipe = parseSchemaOrgRecipe(
            html(
                """
                {"@graph":[
                  {"@type":"WebPage","name":"Blog post"},
                  {"@type":"Recipe","name":"Gratin"}
                ]}
                """.trimIndent()
            ),
            "https://example.com",
        )
        assertEquals("Gratin", recipe?.name)
    }

    @Test
    fun `returns null when no Recipe object is present`() {
        val recipe = parseSchemaOrgRecipe(
            html("""{"@type":"WebPage","name":"Not a recipe"}"""),
            "https://example.com",
        )
        assertNull(recipe)
    }

    // ========================================================================
    // SSRF GUARD — isPublicAddress / SsrfSafeDns / isFetchableUrl
    // ========================================================================

    @Test
    fun `isPublicAddress rejects loopback, private, link-local, and IPv6 unique-local addresses`() {
        assertFalse(isPublicAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(isPublicAddress(InetAddress.getByName("10.0.0.1")))
        assertFalse(isPublicAddress(InetAddress.getByName("192.168.1.1")))
        assertFalse(isPublicAddress(InetAddress.getByName("172.16.0.1")))
        // Cloud metadata endpoint (link-local range) - the canonical SSRF target.
        assertFalse(isPublicAddress(InetAddress.getByName("169.254.169.254")))
        assertFalse(isPublicAddress(InetAddress.getByName("::1")))
        // IPv6 unique-local fc00::/7 - not covered by isSiteLocalAddress, needs the explicit check.
        assertFalse(isPublicAddress(InetAddress.getByName("fd00::1")))
    }

    @Test
    fun `isPublicAddress accepts ordinary public addresses`() {
        assertTrue(isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(isPublicAddress(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun `SsrfSafeDns throws for a hostname that resolves to a non-public address`() {
        // "localhost" always resolves to a loopback address regardless of network access,
        // so this exercises the real guard without depending on external DNS/network.
        assertFailsWith<UnknownHostException> { SsrfSafeDns.lookup("localhost") }
    }

    @Test
    fun `isFetchableUrl rejects non-http(s) schemes and loopback-resolving hosts`() {
        assertFalse(isFetchableUrl("ftp://example.com/recipe"))
        assertFalse(isFetchableUrl("file:///etc/passwd"))
        assertFalse(isFetchableUrl("not a url"))
        assertFalse(isFetchableUrl("http://localhost/admin"))
        assertFalse(isFetchableUrl("http://127.0.0.1:8080/"))
    }
}
