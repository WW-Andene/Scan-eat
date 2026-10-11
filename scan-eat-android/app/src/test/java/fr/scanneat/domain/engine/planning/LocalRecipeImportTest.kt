package fr.scanneat.domain.engine.planning

import fr.scanneat.domain.engine.nutrition.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaOrgRecipeParserTest {
    private fun html(jsonLd: String) = """<html><head><script type="application/ld+json">$jsonLd</script></head></html>"""

    @Test
    fun `extracts name, ingredients, steps and source`() {
        val r = parseSchemaOrgRecipe(
            html("""{"@type":"Recipe","name":"Tarte aux pommes","recipeYield":"6 personnes","recipeIngredient":["300g farine","2 pommes"],"recipeInstructions":[{"@type":"HowToStep","text":"Mélanger la farine."}]}"""),
            "https://example.com/tarte",
        )
        assertNotNull(r)
        assertEquals("Tarte aux pommes", r!!.name)
        assertEquals(listOf("300g farine", "2 pommes"), r.ingredients)
        assertEquals(listOf("Mélanger la farine."), r.steps)
        assertEquals("6", r.servings)
        assertEquals("https://example.com/tarte", r.sourceUrl)
    }

    @Test
    fun `servings stay null when the page declares none`() {
        assertNull(parseSchemaOrgRecipe(html("""{"@type":"Recipe","name":"Soupe"}"""), "u")!!.servings)
    }

    @Test
    fun `ISO durations become minutes`() {
        assertEquals(90, parseSchemaOrgRecipe(html("""{"@type":"Recipe","name":"R","totalTime":"PT1H30M"}"""), "u")!!.cookTimeMinutes)
    }

    @Test
    fun `nutrition values with units are parsed`() {
        val r = parseSchemaOrgRecipe(html("""{"@type":"Recipe","name":"S","nutrition":{"calories":"350 calories","proteinContent":"12g","fatContent":"8g","carbohydrateContent":"40g"}}"""), "u")!!
        assertEquals(350.0, r.kcal!!, 0.01)
        assertEquals(12.0, r.proteinG!!, 0.01)
        assertEquals(8.0, r.fatG!!, 0.01)
        assertEquals(40.0, r.carbsG!!, 0.01)
    }

    // A "PT0M" placeholder is non-null, so totalTime ?: cookTime never reached the real cookTime.
    @Test
    fun `zero totalTime falls back to cookTime`() {
        assertEquals(25, parseSchemaOrgRecipe(html("""{"@type":"Recipe","name":"R","totalTime":"PT0M","cookTime":"PT25M"}"""), "u")!!.cookTimeMinutes)
    }

    // "500 g" used to become 500 servings and "0" a zero divisor for per-serving figures.
    @Test
    fun `non-serving yields are ignored`() {
        assertNull(parseSchemaOrgRecipe(html("""{"@type":"Recipe","name":"R","recipeYield":"500 g"}"""), "u")!!.servings)
        assertNull(parseSchemaOrgRecipe(html("""{"@type":"Recipe","name":"R","recipeYield":"0"}"""), "u")!!.servings)
    }

    @Test
    fun `html entities and tags are cleaned and IRI types are recognised`() {
        val r = parseSchemaOrgRecipe(
            html("""{"@type":"https://schema.org/Recipe","name":"Tarte &amp; cr&egrave;me","recipeIngredient":["1 &frac12; tasse"],"recipeInstructions":["Cuire.<br>Servir &#039;chaud&#039;."]}"""),
            "u",
        )!!
        assertEquals("Tarte & crème", r.name)
        assertEquals(listOf("1 ½ tasse"), r.ingredients)
        assertEquals(listOf("Cuire. Servir 'chaud'."), r.steps)
    }

    @Test
    fun `finds a Recipe nested in @graph and steps grouped in sections`() {
        val r = parseSchemaOrgRecipe(
            html("""{"@graph":[{"@type":"WebPage","name":"Blog"},{"@type":["Recipe"],"name":"Gratin","recipeInstructions":[{"@type":"HowToSection","itemListElement":[{"@type":"HowToStep","text":"Couper."},{"@type":"HowToStep","text":"Cuire."}]}]}]}"""),
            "u",
        )
        assertEquals("Gratin", r?.name)
        assertEquals(listOf("Couper.", "Cuire."), r?.steps)
    }

    @Test
    fun `no Recipe on the page returns null`() {
        assertNull(parseSchemaOrgRecipe(html("""{"@type":"WebPage","name":"x"}"""), "u"))
    }
}

class RecipeTextParserTest {
    private fun stack(vararg texts: String, h: Int = 20): List<OcrLine> =
        texts.mapIndexed { i, t -> OcrLine(t, 10, i * (h + 4), 400, i * (h + 4) + h) }

    @Test
    fun `reads a recipe card`() {
        val lines = listOf(OcrLine("Crêpes", 10, 0, 300, 60)) + stack(
            "Pour 4 personnes - 30 min",
            "Ingrédients",
            "250 g de farine",
            "3 œufs",
            "50 cl de lait",
            "Préparation",
            "1. Mélanger la farine et les œufs.",
            "Ajouter le lait petit à petit.",
            "2. Laisser reposer 1 h.",
        ).map { it.copy(top = it.top + 80, bottom = it.bottom + 80) }
        val r = parseRecipeText(lines)!!
        assertEquals("Crêpes", r.name)
        assertEquals("4", r.servings)
        assertEquals(listOf("250 g de farine", "3 œufs", "50 cl de lait"), r.ingredients)
        assertEquals(listOf("Mélanger la farine et les œufs. Ajouter le lait petit à petit.", "Laisser reposer 1 h."), r.steps)
        assertEquals(90, r.cookTimeMinutes)
    }

    // Regression locks: "1h30" matched neither the minutes nor the hours pattern, and a title
    // starting with "Recette" was swallowed as a steps header (and could not become the name).
    @Test
    fun `reads 1h30 and keeps a title that starts with Recette`() {
        val lines = listOf(OcrLine("Recette de la tarte", 10, 0, 300, 60)) + stack(
            "Ingrédients",
            "2 pommes",
            "Préparation",
            "1. Cuire 1h30 au four.",
        ).map { it.copy(top = it.top + 80, bottom = it.bottom + 80) }
        val r = parseRecipeText(lines)!!
        assertEquals("Recette de la tarte", r.name)
        assertEquals(listOf("2 pommes"), r.ingredients)
        assertEquals(90, r.cookTimeMinutes)
    }

    @Test
    fun `absurd digit runs do not crash the import`() {
        val r = parseRecipeText(stack("Ingrédients", "2 pommes", "Préparation", "1. Attendre 99999999999 min."))
        assertNull(r!!.cookTimeMinutes)
    }

    @Test
    fun `no recipe-like text returns null`() {
        assertNull(parseRecipeText(stack("Bonjour", "Merci")))
    }
}

class OfficialRecipeSuggestionTest {
    @Test
    fun `suggests built-in recipes using the ingredient, in French or English`() {
        val fr = suggestOfficialRecipes(listOf("banane"), english = false)
        assertTrue(fr.isNotEmpty())
        assertTrue(fr.all { r -> r.ingredients.any { "banane" in it } })
        assertTrue(suggestOfficialRecipes(listOf("banana"), english = true).isNotEmpty())
    }

    @Test
    fun `pantry with more matching items ranks recipes using more of them first`() {
        val res = suggestOfficialRecipes(listOf("banane", "lait", "flocons d'avoine"), english = false)
        assertTrue(res.isNotEmpty())
        val first = res.first().ingredients.count { i -> listOf("banane", "lait", "avoine").any { it in i } }
        val last = res.last().ingredients.count { i -> listOf("banane", "lait", "avoine").any { it in i } }
        assertTrue(first >= last)
    }

    @Test
    fun `unknown ingredient gives nothing`() {
        assertTrue(suggestOfficialRecipes(listOf("zzzz"), english = false).isEmpty())
    }
}
