package fr.scanneat.domain.engine.planning

import org.junit.Assert.assertEquals
import org.junit.Test

// Regression locks for compound items whose form (juice, sauce, tin, nut butter, dried) decides the
// aisle, and for the oe-ligature producing duplicate shopping-list rows.
class GroceryAisleOverridesTest {

    @Test
    fun `compound items land in the aisle of their form`() {
        val expected = mapOf(
            "jus d'orange" to GroceryCategory.BEVERAGES,
            "jus de viande" to GroceryCategory.MEAT_FISH,
            "sauce tomate" to GroceryCategory.PANTRY,
            "tomates pelées en boîte" to GroceryCategory.PANTRY,
            "beurre de cacahuète" to GroceryCategory.PANTRY,
            "peanut butter" to GroceryCategory.PANTRY,
            "raisins secs" to GroceryCategory.PANTRY,
            "beurre" to GroceryCategory.DAIRY,
            "vin sec" to GroceryCategory.BEVERAGES,
            "saucisson sec" to GroceryCategory.MEAT_FISH,
            "œufs" to GroceryCategory.MEAT_FISH,
        )
        val wrong = expected.filter { (name, cat) -> groceryCategoryFor(name) != cat }.keys
        assertEquals(emptySet<String>(), wrong)
    }

    @Test
    fun `oe ligature and plain oe aggregate into one row`() {
        val items = aggregateGroceryList(
            listOf(
                GroceryRecipeInput("Quiche", listOf(GroceryComponent("œufs", 150.0))),
                GroceryRecipeInput("Crêpes", listOf(GroceryComponent("Oeufs", 100.0))),
            ),
        )
        assertEquals(1, items.size)
        assertEquals(250, items.single().grams)
        assertEquals(listOf("Quiche", "Crêpes"), items.single().sources)
    }
}
