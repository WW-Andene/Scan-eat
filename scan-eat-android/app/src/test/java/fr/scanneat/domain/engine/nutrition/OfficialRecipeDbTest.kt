package fr.scanneat.domain.engine.nutrition

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OfficialRecipeDb.sum() silently substitutes 0.0 for any ingredient whose
 * foodName doesn't exact-match (case-insensitive) an entry in FOOD_DB -
 * by design, so a genuinely unmatched ingredient never crashes live scoring.
 * That means a typo introduced when adding/editing a recipe here would
 * silently understate its nutrition totals with no error anywhere, rather
 * than fail loudly - this test is the fail-loud check instead, run at
 * build time against every ingredient this static, developer-curated list
 * currently references.
 */
class OfficialRecipeDbTest {

    @Test
    fun `every official recipe ingredient resolves in FOOD_DB`() {
        val missing = OFFICIAL_RECIPE_DB.flatMap { recipe ->
            recipe.ingredients
                .filter { ing -> FOOD_DB.none { it.name.equals(ing.foodName, ignoreCase = true) } }
                .map { ing -> "${recipe.nameFr} -> \"${ing.foodName}\"" }
        }
        assertTrue(
            "The following official-recipe ingredients have no matching FOOD_DB entry " +
                "and would silently contribute 0 kcal/macros to their recipe's totals: $missing",
            missing.isEmpty(),
        )
    }
}
