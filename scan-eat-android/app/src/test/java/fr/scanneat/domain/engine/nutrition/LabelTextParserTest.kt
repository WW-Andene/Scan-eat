package fr.scanneat.domain.engine.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelTextParserTest {

    /** Lays lines out top-to-bottom, each [h] px tall, starting at x = [left]. */
    private fun stack(vararg texts: String, left: Int = 10, top: Int = 0, h: Int = 20): List<OcrLine> =
        texts.mapIndexed { i, t -> OcrLine(t, left, top + i * (h + 4), left + 300, top + i * (h + 4) + h) }

    @Test
    fun `reads ingredients, traces and inline nutrition values`() {
        val lines = stack(
            "BISCUITS NAPPÉS",
            "Ingrédients : farine de blé, sucre, huile de",
            "palme, lait écrémé en poudre, émulsifiant (E322),",
            "sel. Peut contenir des traces de fruits à coque.",
            "Valeurs nutritionnelles pour 100 g",
            "Énergie 2010 kJ / 480 kcal",
            "Matières grasses 21 g",
            "dont acides gras saturés 9,5 g",
            "Glucides 65 g",
            "dont sucres 32 g",
            "Fibres alimentaires 2,1 g",
            "Protéines 6,4 g",
            "Sel 0,45 g",
        )
        val r = parseLabelText(lines)
        val n = r.product.nutrition
        assertEquals(480.0, n.energyKcal, 0.01)
        assertEquals(21.0, n.fatG, 0.01)
        assertEquals(9.5, n.saturatedFatG, 0.01)
        assertEquals(65.0, n.carbsG, 0.01)
        assertEquals(32.0, n.sugarsG, 0.01)
        assertEquals(2.1, n.fiberG, 0.01)
        assertEquals(6.4, n.proteinG, 0.01)
        assertEquals(0.45, n.saltG, 0.01)

        val names = r.product.ingredients.map { it.name }
        assertTrue(names.toString(), "farine de blé" in names)
        assertTrue(names.toString(), "lait écrémé en poudre" in names)
        assertTrue(names.toString(), names.any { it.startsWith("huile de palme") })
        assertTrue(r.product.ingredients.any { it.eNumber == "E322" })
        assertFalse("traces must not be listed as ingredients", names.any { "coque" in it })
        assertEquals(listOf("en:nuts"), r.product.declaredTracesTags)
        assertEquals("BISCUITS NAPPÉS", r.product.name)
        assertTrue(r.ingredientsRead)
    }

    @Test
    fun `reads a table OCR'd as separate label and value columns`() {
        val labels = stack("Énergie", "Matières grasses", "dont saturés", "Glucides", "dont sucres", "Protéines", "Sel", top = 100)
        // Per-100 g column, then per-portion column further right.
        val per100 = stack("1046 kJ / 250 kcal", "12 g", "4,1 g", "28 g", "3 g", "8,5 g", "1,2 g", left = 400, top = 100)
        val perPortion = stack("523 kJ / 125 kcal", "6 g", "2 g", "14 g", "1,5 g", "4,2 g", "0,6 g", left = 700, top = 100)
        val r = parseLabelText(labels + per100 + perPortion)
        val n = r.product.nutrition
        assertEquals(250.0, n.energyKcal, 0.01)
        assertEquals(12.0, n.fatG, 0.01)
        assertEquals(4.1, n.saturatedFatG, 0.01)
        assertEquals(28.0, n.carbsG, 0.01)
        assertEquals(3.0, n.sugarsG, 0.01)
        assertEquals(8.5, n.proteinG, 0.01)
        assertEquals(1.2, n.saltG, 0.01)
        assertFalse(r.ingredientsRead)
    }

    @Test
    fun `sodium in mg converts to salt`() {
        val r = parseLabelText(stack("Sodium 400 mg"))
        assertEquals(1.0, r.product.nutrition.saltG, 0.01)
        assertTrue("salt" in r.nutrientsRead)
    }

    @Test
    fun `English label`() {
        val r = parseLabelText(stack("Ingredients: oats, honey, almonds.", "Energy 1650 kJ / 395 kcal", "Fat 9 g", "Protein 10 g"), lang = "en")
        assertEquals(395.0, r.product.nutrition.energyKcal, 0.01)
        assertEquals(9.0, r.product.nutrition.fatG, 0.01)
        assertEquals(listOf("oats", "honey", "almonds"), r.product.ingredients.map { it.name })
    }

    @Test
    fun `kJ-only energy converts`() {
        assertEquals(200.0, parseLabelText(stack("Énergie 836,8 kJ")).product.nutrition.energyKcal, 0.1)
    }

    @Test
    fun `valid barcode is picked up, invalid ignored`() {
        assertEquals("3017620422003", parseLabelText(stack("3 017620 422003")).barcode)
        assertNull(parseLabelText(stack("3017620422004")).barcode)
    }

    @Test
    fun `nothing readable yields empty draft`() {
        val r = parseLabelText(emptyList())
        assertTrue(r.nutrientsRead.isEmpty())
        assertFalse(r.ingredientsRead)
    }
}

class LabelConsistencyAndOcrTest {
    private fun stack(vararg texts: String): List<OcrLine> =
        texts.mapIndexed { i, t -> OcrLine(t, 10, i * 24, 310, i * 24 + 20) }

    @Test
    fun `common OCR letter-for-digit confusions are corrected`() {
        val n = parseLabelText(stack("Matières grasses 1O g", "Protéines l2 g", "Sel 0,S g")).product.nutrition
        assertEquals(10.0, n.fatG, 0.01)
        assertEquals(12.0, n.proteinG, 0.01)
        assertEquals(0.5, n.saltG, 0.01)
    }

    @Test
    fun `a consistent label raises nothing`() {
        val r = parseLabelText(stack("Énergie 480 kcal", "Matières grasses 21 g", "dont saturés 9,5 g", "Glucides 65 g", "dont sucres 32 g", "Fibres 2 g", "Protéines 6,4 g"))
        assertTrue(r.inconsistencies.toString(), r.inconsistencies.isEmpty())
    }

    @Test
    fun `impossible values are flagged`() {
        // "65 g" glucides misread as "6,5 g": sugars > carbs and energy no longer adds up.
        val r = parseLabelText(stack("Énergie 480 kcal", "Matières grasses 21 g", "Glucides 6,5 g", "dont sucres 32 g", "Protéines 6,4 g"))
        assertTrue(LabelInconsistency.SUGARS_ABOVE_CARBS in r.inconsistencies)
        assertTrue(LabelInconsistency.ENERGY_MISMATCH in r.inconsistencies)
    }

    @Test
    fun `reads parent and child nutrients when OCR puts both on one line`() {
        val r = parseLabelText(stack(
            "Valeurs nutritionnelles pour 100 g",
            "Matières grasses 21 g dont acides gras saturés 9,5 g",
            "Glucides 65 g dont sucres 32 g",
        ))
        val n = r.product.nutrition
        assertEquals(21.0, n.fatG, 0.01)
        assertEquals(9.5, n.saturatedFatG, 0.01)
        assertEquals(65.0, n.carbsG, 0.01)
        assertEquals(32.0, n.sugarsG, 0.01)
    }

    @Test
    fun `ingredient percentages on a multi-label line are not read as nutrients`() {
        val r = parseLabelText(stack(
            "Ingrédients : farine de blé, sucre, sel (1,2%), levure.",
            "Valeurs nutritionnelles pour 100 g",
            "Sel 0,45 g",
        ))
        assertEquals(0.45, r.product.nutrition.saltG, 0.01)
    }

    // Regression lock: "conservation" in INGREDIENTS_END cut the list at
    // "agent de conservation", losing the preservative and every ingredient
    // after it (including the soy lecithin allergen).
    @Test
    fun `agent de conservation does not truncate the ingredient list`() {
        val r = parseLabelText(stack(
            "GÂTEAU",
            "Ingrédients : farine de blé, sucre, œufs,",
            "agent de conservation : sorbate de potassium, arôme,",
            "émulsifiant : lécithine de soja.",
            "Conditions de conservation : à conserver au sec.",
        ))
        val names = r.product.ingredients.map { it.name }
        assertTrue(names.toString(), names.any { it.contains("sorbate") })
        assertTrue(names.toString(), names.any { it.contains("lécithine de soja") })
        assertTrue(names.toString(), names.none { it.contains("au sec") })
    }
}
