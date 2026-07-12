package fr.scanneat.domain.engine.planning

import java.text.Normalizer
import kotlin.math.roundToInt

// ============================================================================
// GROCERY LIST — port of public/features/grocery-list.js
//
// Pure functions — no I/O, no dependencies.
// Aggregates recipe components into a deduplicated shopping list.
// ============================================================================

data class GroceryItem(
    val name: String,
    val grams: Int,
    val sources: List<String>,   // recipe names that need this ingredient
    // Stable identity independent of which recipe's spelling of this
    // ingredient happened to be the first one aggregated - checked-off state
    // (GroceryCheckedRepository) persists against this, not `name`, so a
    // renamed recipe or a different row-fetch order can't silently orphan it.
    val key: String,
)

data class GroceryRecipeInput(
    val name: String,
    val components: List<GroceryComponent>,
)

data class GroceryComponent(
    val productName: String,
    val grams: Double,
)

private fun normalizeKey(name: String): String =
    Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")

/**
 * Aggregate recipes into a deduplicated, alphabetically-sorted grocery list.
 * Port of aggregateGroceryList() from grocery-list.js.
 */
fun aggregateGroceryList(recipes: List<GroceryRecipeInput>): List<GroceryItem> {
    data class Acc(val name: String, var grams: Double, val sources: MutableList<String>)

    val acc = mutableMapOf<String, Acc>()
    for (recipe in recipes) {
        for (component in recipe.components) {
            val rawName = component.productName.trim()
            if (rawName.isEmpty()) continue
            val key = normalizeKey(rawName)
            val entry = acc.getOrPut(key) { Acc(rawName, 0.0, mutableListOf()) }
            entry.grams += component.grams.coerceAtLeast(0.0)
            val recipeName = recipe.name.trim().ifEmpty { "—" }
            if (recipeName !in entry.sources) entry.sources += recipeName
        }
    }
    // Round rather than truncate — .toInt() always rounds toward zero, so
    // e.g. three recipes each needing 0.4g of an ingredient summed to 1.2g
    // and then silently displayed as "1 g", and several sub-0.5g totals
    // (0.4g, 0.6g, ...) got truncated down to 0g and vanished from the list
    // entirely once formatGroceryList() hides items with grams <= 0.
    return acc.entries
        .map { (key, entry) -> GroceryItem(entry.name, entry.grams.roundToInt(), entry.sources.toList(), key = key) }
        .sortedBy { it.key }
}

/**
 * Format grocery list as plain text for clipboard / share.
 * markdown=true → GitHub-style task-list checkboxes.
 * Port of formatGroceryList() from grocery-list.js.
 */
fun formatGroceryList(items: List<GroceryItem>, markdown: Boolean = false): String {
    val prefix = if (markdown) "- [ ] " else "- "
    return items.joinToString("\n") { item ->
        "$prefix${item.name}${if (item.grams > 0) " · ${item.grams} g" else ""}"
    }
}
