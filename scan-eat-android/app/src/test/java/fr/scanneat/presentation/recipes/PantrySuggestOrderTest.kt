package fr.scanneat.presentation.recipes

import fr.scanneat.presentation.pantry.PantryExpiryUrgency.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap D2. */
class PantrySuggestOrderTest {
    @Test fun `soon first, expired last, duplicates removed`() {
        val order = orderPantryForSuggestions(listOf("riz" to NONE, "yaourt" to EXPIRED, "courgette" to SOON, "pâtes" to OK, "courgette" to OK))
        assertEquals(listOf("courgette", "riz", "pâtes", "yaourt"), order)
    }
}
