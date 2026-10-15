package fr.scanneat.domain.engine.nutrition

/**
 * Likely product names on a front-of-pack photo: the product name is usually
 * the biggest text. Lines that are mostly digits, legal mentions or the
 * nutrition table are skipped; two stacked lines of the same size are joined
 * ("Pâtes à la" / "bolognaise").
 */
private val NOT_A_NAME = Regex("""(?i)\b(ingr[eé]dients?|valeurs? nutritionnelles?|nutrition|kcal|kj|[eé]nergie|pour 100|per 100|poids net|net weight|[aà] conserver|dlc|ddm|best before|lot\b|www\.|\.fr\b|\.com\b|e\d{3})""")

fun productNameCandidates(lines: List<OcrLine>, max: Int = 3): List<String> {
    val usable = lines.filter { l ->
        val t = l.text.trim()
        val letters = t.count(Char::isLetter)
        letters >= 4 && letters >= t.length * 0.6 && !NOT_A_NAME.containsMatchIn(t)
    }
    if (usable.isEmpty()) return emptyList()
    val byHeight = usable.sortedByDescending { it.height }
    val out = mutableListOf<String>()
    for (l in byHeight) {
        // Join with the line just below when it has about the same size (a name on two lines).
        val next = usable.filter { it !== l && it.top >= l.bottom - l.height / 4 && it.top - l.bottom < l.height &&
            it.height >= l.height * 0.75 && it.height <= l.height * 1.33 }.minByOrNull { it.top }
        val name = (if (next != null) "${l.text.trim()} ${next.text.trim()}" else l.text.trim()).replace(Regex("\\s+"), " ")
        if (out.none { it.equals(name, ignoreCase = true) }) out += name
        if (out.size >= max) break
    }
    return out
}
