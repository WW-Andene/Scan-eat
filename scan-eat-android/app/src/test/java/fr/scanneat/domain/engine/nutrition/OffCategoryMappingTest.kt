package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// Regression locks: mapCategory matched raw substrings of the joined tag string, so short drink/nut
// words matched inside unrelated compounds (vinegars, liquorice, crumbles, coconut, rapeseed, cheesecakes).
class OffCategoryMappingTest {

    private fun cat(vararg tags: String) = mapCategory(tags.toList())

    @Test
    fun `vinegars are condiments, not alcoholic drinks`() {
        assertEquals(ProductCategory.CONDIMENT, cat("en:vinegars"))
        assertEquals(ProductCategory.CONDIMENT, cat("en:wine-vinegars"))
        assertEquals(ProductCategory.CONDIMENT, cat("fr:vinaigres"))
    }

    @Test
    fun `sweets and baked goods with drink-like substrings are not alcoholic`() {
        assertNotEquals(ProductCategory.ALCOHOLIC_BEVERAGE, cat("en:confectioneries", "en:liquorices"))
        assertNotEquals(ProductCategory.ALCOHOLIC_BEVERAGE, cat("en:crumbles"))
        assertNotEquals(ProductCategory.ALCOHOLIC_BEVERAGE, cat("en:wine-gums"))
    }

    @Test
    fun `real alcoholic drinks are still recognised`() {
        assertEquals(ProductCategory.ALCOHOLIC_BEVERAGE, cat("en:beverages", "en:alcoholic-beverages", "en:wines"))
        assertEquals(ProductCategory.ALCOHOLIC_BEVERAGE, cat("en:red-wines"))
        assertEquals(ProductCategory.ALCOHOLIC_BEVERAGE, cat("en:beers"))
        assertEquals(ProductCategory.ALCOHOLIC_BEVERAGE, cat("fr:vins-rouges"))
    }

    @Test
    fun `nut and seed words match whole tokens only`() {
        assertEquals(ProductCategory.NUTS_SEEDS, cat("en:mixed-nuts"))
        assertEquals(ProductCategory.NUTS_SEEDS, cat("en:peanut-butters"))
        assertEquals(ProductCategory.OIL_FAT, cat("en:rapeseed-oils"))
        assertNotEquals(ProductCategory.NUTS_SEEDS, cat("en:beverages", "en:coconut-waters"))
        assertNotEquals(ProductCategory.NUTS_SEEDS, cat("en:doughnuts"))
    }

    @Test
    fun `cheesecake is not cheese`() {
        assertEquals(ProductCategory.CHEESE, cat("en:cheeses"))
        assertNotEquals(ProductCategory.CHEESE, cat("en:cheesecakes"))
    }
}
