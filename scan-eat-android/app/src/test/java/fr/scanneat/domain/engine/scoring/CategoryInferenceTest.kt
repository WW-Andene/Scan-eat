package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryInferenceTest {

    // Regression lock: the beef pattern was \bb[oœ]uf\b, which matches "bœuf" but
    // not the equally common keyboard spelling "boeuf" (b + o + e + uf).
    @Test
    fun `beef is fresh meat with either spelling`() {
        assertEquals(ProductCategory.FRESH_MEAT, inferCategoryFromName("Bœuf haché"))
        assertEquals(ProductCategory.FRESH_MEAT, inferCategoryFromName("Boeuf haché"))
    }
}
