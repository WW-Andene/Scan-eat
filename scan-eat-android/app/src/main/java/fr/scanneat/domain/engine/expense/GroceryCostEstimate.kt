package fr.scanneat.domain.engine.expense

import fr.scanneat.domain.engine.scoring.normalizeForMatching
import java.time.LocalDate

/** Roadmap D3: what the shopping list should cost, from the user's own last prices. */
data class GroceryCostEstimate(val total: Double, val pricedCount: Int, val unpricedCount: Int)

/**
 * For each item, the most recent price the user paid for a product whose
 * name is the item or contains it as a whole word ("tomates" -> "Tomates
 * cerises bio"). Items never bought are counted as unpriced, not guessed.
 */
fun estimateGroceryCost(itemNames: List<String>, prices: List<Triple<String, LocalDate, Double>>): GroceryCostEstimate {
    val byRecency = prices.sortedByDescending { it.second }.map { normalizeForMatching(it.first) to it.third }
    var total = 0.0; var priced = 0; var unpriced = 0
    itemNames.forEach { name ->
        val key = normalizeForMatching(name).trim()
        val hit = if (key.isEmpty()) null else byRecency.firstOrNull { (p, _) ->
            p == key || Regex("""\b${Regex.escape(key)}s?\b""").containsMatchIn(p)
        }
        if (hit != null) { total += hit.second; priced++ } else unpriced++
    }
    return GroceryCostEstimate(total, priced, unpriced)
}
