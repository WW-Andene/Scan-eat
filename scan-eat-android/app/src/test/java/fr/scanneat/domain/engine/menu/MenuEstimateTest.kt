package fr.scanneat.domain.engine.menu

import fr.scanneat.domain.model.Profile
import org.junit.Assert.*
import org.junit.Test

class MenuEstimateTest {
    @Test fun `dish with price gets an estimate and its usual-recipe allergens`() {
        val d = analyzeMenuText("Spaghetti bolognaise 14,50 €", Profile(allergens = setOf("gluten")), "fr").single()
        assertNotNull(d.estimate)
        assertTrue(d.typicalRecipeAllergens.isNotEmpty())   // gluten from the pasta, not written on the menu
    }

    @Test fun `composed dish is estimated, unknown one is not`() {
        val ds = analyzeMenuText("Penne sauce tomate\nPlat du chef zorglub", Profile(), "fr")
        assertTrue(ds[0].estimate!!.basis.contains("sauce tomate"))
        assertNull(ds[1].estimate)
    }
}
