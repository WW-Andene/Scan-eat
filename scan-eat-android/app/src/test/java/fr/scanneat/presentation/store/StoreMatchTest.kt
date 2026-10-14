package fr.scanneat.presentation.store

import fr.scanneat.domain.engine.planning.GroceryItem
import fr.scanneat.domain.engine.planning.normalizeKey
import org.junit.Assert.*
import org.junit.Test

/** Roadmap N7. */
class StoreMatchTest {
    private fun item(n: String) = GroceryItem(name = n, grams = 0, sources = emptyList(), key = normalizeKey(n))

    @Test fun `scanned product ticks the matching item by whole word`() {
        val list = listOf(item("riz"), item("Lait"), item("tomates"))
        assertEquals("Lait", matchGroceryItem("Lait demi-écrémé Lactel 1L", list)?.name)
        assertEquals("tomates", matchGroceryItem("Tomate cerise", list)?.name)
        assertNull(matchGroceryItem("Chorizo doux", list)) // "riz" inside "chorizo" must not match
    }
}
