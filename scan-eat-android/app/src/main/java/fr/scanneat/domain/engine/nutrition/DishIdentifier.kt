package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.planning.findFoodByName
import fr.scanneat.domain.engine.planning.findFoodExact
import fr.scanneat.domain.model.ProductCategory
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * What a dish or product NAME (typed, read on a menu or on a pack) maps to in
 * the food table. Never presents a part of the name as the whole dish: before
 * this, "pâtes à la bolognaise" became plain "pâtes cuites", sauce forgotten.
 */
sealed class DishMatch {
    /** The whole name is a food or dish of the table (or one of its aliases). */
    data class Exact(val food: FoodEntry) : DishMatch()
    /** Built from several foods of the table with typical proportions - an estimate. */
    data class Composed(val food: FoodEntry, val parts: List<Pair<FoodEntry, Double>>) : DishMatch()
    /** Only part of the name was recognised; [missing] are the words left out. */
    data class Partial(val food: FoodEntry, val missing: List<String>) : DishMatch()
}

// Linking words that separate a dish's components. "sauce" is kept as a prefix
// of the part that follows it ("sauce tomate").
private val CONNECTORS = Regex("""\s+(?:a la|a l'|au|aux|avec|et|facon|with|and|in|&|\+)\s+|\s*[,/+]\s*""")
private val STOP = setOf("de", "d", "du", "des", "la", "le", "les", "l", "a", "au", "aux", "et", "avec", "the", "of", "with", "and",
    "maison", "frais", "fraiche", "bio", "cuisine", "cuisinee", "plat", "recette", "classique", "traditionnel", "traditionnelle")

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    .replace("’", "'").replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()

/** Share of the plate per component role, before normalising to 100 %. */
private fun roleWeight(f: FoodEntry): Double = when {
    norm(f.name).startsWith("sauce") || f.category == ProductCategory.CONDIMENT -> 0.3
    f.category == ProductCategory.GRAIN || f.category == ProductCategory.BREAD || norm(f.name).startsWith("pomme de terre") -> 0.6
    else -> 0.3
}

private fun significantWords(s: String) = norm(s).split(' ').filter { it.length > 2 && it !in STOP }

fun identifyDish(name: String): DishMatch? {
    val n = norm(name)
    if (n.isBlank()) return null
    findFoodExact(n)?.let { return DishMatch.Exact(it) }

    // Split into components; a bare sauce word after "sauce"/"à la" ("bolognaise", "tomate")
    // is tried as "sauce X" first.
    val rawParts = n.split(CONNECTORS).map { it.trim() }.filter { it.isNotEmpty() }
        .flatMap { p -> if (p.startsWith("sauce ")) listOf(p) else p.split(Regex("""\s+sauce\s+""")).let { s -> if (s.size == 2) listOf(s[0], "sauce " + s[1]) else s } }
    val parts = rawParts.mapNotNull { p ->
        findFoodExact(p) ?: findFoodExact("sauce $p") ?: findFoodByName(p)?.takeIf { f -> significantWords(p).any { w -> w in norm(f.name) || f.aliases.any { w in norm(it) } } }
    }.distinctBy { it.name }
    if (rawParts.size >= 2 && parts.size >= 2) {
        val total = parts.sumOf(::roleWeight)
        val weighted = parts.map { it to roleWeight(it) / total }
        return DishMatch.Composed(blend(name.trim(), weighted), weighted)
    }

    val single = findFoodByName(n) ?: return null
    val covered = (significantWords(single.name) + single.aliases.flatMap(::significantWords)).toSet()
    val missing = significantWords(n).filter { w -> covered.none { c -> c.startsWith(w.take(4)) || w.startsWith(c.take(4)) } }
    return if (missing.isEmpty()) DishMatch.Exact(single) else DishMatch.Partial(single, missing)
}

/** Weighted per-100 g mix of [parts]; ingredients are the components' own. */
private fun blend(name: String, parts: List<Pair<FoodEntry, Double>>): FoodEntry {
    fun mix(sel: (FoodEntry) -> Double) = parts.sumOf { (f, w) -> sel(f) * w }
    return FoodEntry(
        name = name, kcal = mix { it.kcal }, proteinG = mix { it.proteinG }, carbsG = mix { it.carbsG }, fatG = mix { it.fatG },
        fiberG = mix { it.fiberG }, saltG = mix { it.saltG }, saturatedFatG = mix { it.saturatedFatG }, sugarsG = mix { it.sugarsG },
        ironMg = mix { it.ironMg }, calciumMg = mix { it.calciumMg }, vitCMg = mix { it.vitCMg },
        category = ProductCategory.READY_MEAL,
        typicalIngredients = parts.flatMap { (f, _) -> f.typicalIngredients.ifEmpty { listOf(f.name) } }.distinct(),
    )
}

/** "pâtes cuites 67 % + sauce bolognaise 33 %" */
fun DishMatch.Composed.describe(): String = parts.joinToString(" + ") { (f, w) -> "${f.name} ${(w * 100).roundToInt()} %" }
