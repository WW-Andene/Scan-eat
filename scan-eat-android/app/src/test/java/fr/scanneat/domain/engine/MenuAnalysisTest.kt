package fr.scanneat.domain.engine

import fr.scanneat.domain.engine.menu.analyzeMenuText
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.Profile
import org.junit.Assert.*
import org.junit.Test

/** Roadmap N3. */
class MenuAnalysisTest {
    private val menu = """
        ENTRÉES
        Salade de chèvre chaud, noix et miel  12,50 €
        Velouté de potimarron  9 €
        PLATS
        Pavé de saumon, beurre blanc  21 €
        Risotto aux champignons  18 €
        24,00
    """.trimIndent()

    @Test fun `allergen named in a dish is flagged, price-only lines dropped`() {
        val dishes = analyzeMenuText(menu, Profile(allergens = setOf("nuts", "fish")))
        assertTrue(dishes.none { it.text == "24,00" })
        assertTrue(dishes.single { it.text.startsWith("Salade") }.allergens.isNotEmpty())
        assertTrue(dishes.single { it.text.startsWith("Pavé") }.allergens.isNotEmpty())
        assertTrue(dishes.single { it.text.startsWith("Velouté") }.allergens.isEmpty())
    }

    @Test fun `vegetarian conflict on fish, nothing on risotto`() {
        val veg = DietKey.entries.first { it.key == "vegetarian" }
        val dishes = analyzeMenuText(menu, Profile(diet = veg))
        assertNotNull(dishes.single { it.text.startsWith("Pavé") }.dietConflict)
        assertNull(dishes.single { it.text.startsWith("Risotto") }.dietConflict)
    }
}
