package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Ingredient

// ============================================================================
// OFF INGREDIENT PARSING — split out of OffMapper.kt: turns OFF's raw
// ingredients text and additives tags into our domain Ingredient list.
// ============================================================================

internal fun parseIngredients(text: String?): List<Ingredient> {
    if (text.isNullOrBlank()) return emptyList()
    // Split on commas and semicolons that are not inside parentheses
    return text.split(Regex("""[,;]\s*(?![^(]*\))"""))
        .mapNotNull { raw ->
            val name = raw.trim().trim('*', ' ')
            if (name.isBlank()) return@mapNotNull null
            val eNumber = Regex("""[Ee](\d{3}[a-zA-Z]?)""").find(name)?.let { "E${it.groupValues[1]}" }
            Ingredient(name = name, eNumber = eNumber)
        }
        .filter { it.name.length > 1 }
}

// OFF's own curated additives_tags (e.g. "en:e322") - manufacturer-verified,
// catches additives OFF's own parser found even when the label doesn't spell
// out an explicit "E" prefix. Only adds a synthetic ingredient for a tag not
// already covered by the ingredient-text regex above, never duplicating or
// overriding what the label actually says.
internal fun additiveTagsToIngredients(tags: List<String>?, existing: List<Ingredient>): List<Ingredient> {
    if (tags.isNullOrEmpty()) return emptyList()
    val existingENumbers = existing.mapNotNull { it.eNumber }.toSet()
    return tags.mapNotNull { tag ->
        val m = Regex("""en:e(\d{3}[a-z]?)""").find(tag) ?: return@mapNotNull null
        val eNum = "E${m.groupValues[1].uppercase()}"
        if (eNum in existingENumbers) return@mapNotNull null
        Ingredient(name = eNum, eNumber = eNum)
    }.distinctBy { it.eNumber }
}
