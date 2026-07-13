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

fun normalizeKey(name: String): String =
    Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")

private class GroceryAcc(val name: String, var grams: Double, val sources: MutableList<String>)

/**
 * Resolves the aggregated row key a raw ingredient/item name would land on,
 * after mergePluralVariants() folds a plural spelling into its singular
 * counterpart. Used to match a manual grocery item back to the aggregated
 * [GroceryItem] it contributed to (e.g. for deletion) without re-deriving a
 * separate, possibly-out-of-sync normalization elsewhere.
 */
fun canonicalGroceryKey(name: String, existingKeys: Set<String>): String {
    val key = normalizeKey(name)
    val singular = key.dropLast(1)
    return if (key.endsWith("s") && key !in existingKeys && singular in existingKeys) singular else key
}

/**
 * Aggregate recipes into a deduplicated, alphabetically-sorted grocery list.
 * Port of aggregateGroceryList() from grocery-list.js.
 */
fun aggregateGroceryList(recipes: List<GroceryRecipeInput>): List<GroceryItem> {
    val acc = mutableMapOf<String, GroceryAcc>()
    for (recipe in recipes) {
        for (component in recipe.components) {
            val rawName = component.productName.trim()
            if (rawName.isEmpty()) continue
            val key = normalizeKey(rawName)
            val entry = acc.getOrPut(key) { GroceryAcc(rawName, 0.0, mutableListOf()) }
            entry.grams += component.grams.coerceAtLeast(0.0)
            val recipeName = recipe.name.trim().ifEmpty { "—" }
            if (recipeName !in entry.sources) entry.sources += recipeName
        }
    }
    mergePluralVariants(acc)

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
 * Merges a singular/plural pair ("tomate" from one recipe, "tomates" from
 * another) into one row instead of two separate list entries with separate
 * gram totals — previously understated how much of an ingredient is really
 * needed across the week's recipes. Deliberately conservative: only merges
 * when BOTH the bare key and its "+s" form are actually present in this
 * aggregation, never by blindly stripping a trailing "s" off every key (that
 * would mangle words that are already singular and simply end in "s", e.g.
 * French "maïs"/"houmous" have no separate singular entry to wrongly fold
 * into). The shorter (singular) key is kept as canonical so an existing
 * checked-off state for it survives a merge where possible.
 */
private fun mergePluralVariants(acc: MutableMap<String, GroceryAcc>) {
    val pluralKeys = acc.keys.filter { it.endsWith("s") && it.dropLast(1) in acc }.toList()
    for (pluralKey in pluralKeys) {
        val singularKey = pluralKey.dropLast(1)
        val plural = acc.remove(pluralKey) ?: continue
        val singular = acc.getValue(singularKey)
        singular.grams += plural.grams
        for (source in plural.sources) if (source !in singular.sources) singular.sources += source
    }
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
