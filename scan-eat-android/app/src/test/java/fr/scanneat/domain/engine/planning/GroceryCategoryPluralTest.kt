package fr.scanneat.domain.engine.planning

import org.junit.Assert.assertEquals
import org.junit.Test

// Shopping-list entries are usually plural ("tomates", "oeufs") while the
// keyword list is singular, so they fell into OTHER.
class GroceryCategoryPluralTest {
    @Test
    fun `plural item names land in their aisle`() {
        val expected = mapOf(
            "Tomates" to GroceryCategory.PRODUCE, "pommes" to GroceryCategory.PRODUCE, "carottes" to GroceryCategory.PRODUCE,
            "choux" to GroceryCategory.PRODUCE, "oeufs" to GroceryCategory.MEAT_FISH, "yaourts" to GroceryCategory.DAIRY,
            "saucisses" to GroceryCategory.MEAT_FISH, "lentilles" to GroceryCategory.PANTRY, "bananas" to GroceryCategory.PRODUCE,
        )
        val wrong = expected.filter { (name, cat) -> groceryCategoryFor(name) != cat }.keys
        assertEquals(emptySet<String>(), wrong)
    }
}
