package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Ingredient

// ============================================================================
// OFF INGREDIENT PARSING — split out of OffMapper.kt: turns OFF's raw
// ingredients text and additives tags into our domain Ingredient list.
// ============================================================================

private val E_NUMBER_IN_TEXT = Regex("""(?<![A-Za-z0-9])[Ee][- ]?(\d{3,4}[a-z]?)(?![0-9a-z])""")

private val ADDED_PHRASE = Regex("""\s+avec adjonction de\s+|\s+with added\s+""")
// Legacy splitter: a separator not followed by a closing paren before any opening one.
// Right for flat lists and for unbalanced OCR text, wrong for nested parentheses.
private val FLAT_SPLIT = Regex("""[,;]\s*(?![^(]*\))|\s+avec adjonction de\s+|\s+with added\s+""")

/**
 * Splits on top-level commas/semicolons only, so "sauce (tomate, oignon (frais, bio)), sel"
 * yields two ingredients, not the garbled "sauce (tomate" / "oignon (frais, bio))" the flat
 * lookahead regex produced for nested groups. Unbalanced parentheses (common in OCR text)
 * fall back to the flat regex rather than swallowing the rest of the list into one entry.
 */
private fun splitTopLevel(text: String): List<String> {
    var depth = 0
    val parts = mutableListOf<String>()
    val cur = StringBuilder()
    for (c in text) {
        when {
            c == '(' -> { depth++; cur.append(c) }
            c == ')' -> { if (depth == 0) return text.split(FLAT_SPLIT); depth--; cur.append(c) }
            (c == ',' || c == ';') && depth == 0 -> { parts += cur.toString(); cur.setLength(0) }
            else -> cur.append(c)
        }
    }
    if (depth != 0) return text.split(FLAT_SPLIT)
    parts += cur.toString()
    return parts.flatMap { it.split(ADDED_PHRASE) }
}

internal fun parseIngredients(text: String?): List<Ingredient> {
    if (text.isNullOrBlank()) return emptyList()
    // Split on commas/semicolons, and on "avec adjonction de"/"with added" -
    // common OFF mineral-water phrasing ("Eau de Source avec adjonction de
    // gaz carbonique") has no comma at all, so the whole sentence previously
    // became ONE ingredient. That single "ingredient" then matched the E290
    // (gaz carbonique) additive alias, which excluded it from
    // IngredientIntegrityPillar's recognizability count entirely - the
    // genuinely whole-food "Eau de Source" was never counted, producing a
    // bogus "0% recognizable ingredients" deduction on top of the real,
    // separately-scored E290 additive hit. Splitting these apart lets both
    // pillars see "Eau de Source" (a whole food) and "gaz carbonique" (an
    // additive) as the two distinct things they actually are.
    return splitTopLevel(text)
        .flatMap { raw ->
            val name = raw.trim().trim('*', ' ')
            if (name.isBlank()) return@flatMap emptyList()
            // Every E-number in the segment, 3 or 4 digits, optionally spaced/hyphenated
            // ("E 330", "E-202"). Previously only the first, and \d{3} truncated
            // E1422 (modified starch) to E142 (Green S colorant). A grouped
            // ingredient ("émulsifiants (E322, E471)") keeps the first E-number;
            // each further one becomes its own synthetic ingredient, the same
            // shape additiveTagsToIngredients uses.
            val eNumbers = E_NUMBER_IN_TEXT.findAll(name).map { "E${it.groupValues[1]}" }.distinct().toList()
            listOf(Ingredient(name = name, eNumber = eNumbers.firstOrNull())) +
                eNumbers.drop(1).map { Ingredient(name = it, eNumber = it) }
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
    // S6: dedupe on the RESOLVED additive, not the raw string - "lécithine de
    // soja" in the text and the tag "en:e322i" are the same additive, and
    // counting it twice inflated the additive count (NOVA estimate) and the
    // ingredient count (">10 ingredients" penalty).
    val existingAdditives = existing.mapNotNull { fr.scanneat.domain.engine.scoring.findAdditive(it.eNumber, it.name, it.category)?.eNumber }.toSet()
    return tags.mapNotNull { tag ->
        val m = Regex("""en:e(\d{3,4}[a-z]*)""").find(tag) ?: return@mapNotNull null
        val eNum = "E${m.groupValues[1].uppercase()}"
        val resolved = fr.scanneat.domain.engine.scoring.findAdditive(eNum, eNum, null)?.eNumber
        if (resolved != null && resolved in existingAdditives) return@mapNotNull null
        Ingredient(name = eNum, eNumber = eNum)
    }.distinctBy { fr.scanneat.domain.engine.scoring.findAdditive(it.eNumber, it.name, null)?.eNumber ?: it.eNumber }
}
