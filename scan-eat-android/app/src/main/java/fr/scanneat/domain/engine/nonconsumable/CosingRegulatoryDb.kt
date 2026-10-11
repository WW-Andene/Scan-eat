package fr.scanneat.domain.engine.nonconsumable

import android.content.Context
import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// COSING REGULATORY DB — real EU Regulation (EC) No 1223/2009 substance
// status, not a fabricated toxicology database (see CosmeticTransparencyScore.kt's
// own header on why this app won't invent per-ingredient hazard verdicts).
//
// Source: the official CosIng "Annex II" (LIST OF SUBSTANCES PROHIBITED IN
// COSMETIC PRODUCTS) and "Annex III" (LIST OF SUBSTANCES WHICH COSMETIC
// PRODUCTS MUST NOT CONTAIN EXCEPT SUBJECT TO THE RESTRICTIONS LAID DOWN)
// exports, downloaded directly from the European Commission's own CosIng
// tool (ec.europa.eu/growth/tools-databases/cosing) and provided by the app
// owner on 12/08/2026, since this environment's network egress cannot reach
// ec.europa.eu itself. Bundled as assets/cosing_annex_ii_prohibited.csv
// (1,490 matchable names, from 2,481 Annex II reference entries) and
// assets/cosing_annex_iii_restricted.csv (922 matchable names, from 1,760
// Annex III entries), each row (name, CAS number, reference number)
// extracted from the "Identified INGREDIENTS or substances e.g." column
// (Annex III also falls back to "Name of Common Ingredients Glossary")
// - the actual INCI-style label name(s), not just the CAS/IUPAC chemical
// name, since that's what a product's own ingredients_text declares.
//
// IMPORTANT — what "restricted" (Annex III) does NOT mean: Annex III lists
// substances cosmetic products are legally ALLOWED to contain, subject to a
// concentration/usage condition (e.g. Retinol, Kojic Acid, Silver, Hexyl
// Salicylate are all real, common, legally-used ingredients in this list) -
// it is a "regulated with conditions" status, not a hazard flag. Only Annex
// II is a genuine prohibition. The UI must keep this distinction explicit,
// not collapse both into one generic "watch out" signal.
// ============================================================================

data class CosingMatch(val name: String, val cas: String, val referenceNumber: String)

private data class CosingRow(val name: String, val cas: String, val ref: String)

private object CosingStore {
    @Volatile private var prohibited: Map<String, List<CosingRow>>? = null
    @Volatile private var restricted: Map<String, List<CosingRow>>? = null

    fun getProhibited(context: Context): Map<String, List<CosingRow>> {
        prohibited?.let { return it }
        synchronized(this) {
            prohibited?.let { return it }
            val map = load(context, "cosing_annex_ii_prohibited.csv")
            prohibited = map
            return map
        }
    }

    fun getRestricted(context: Context): Map<String, List<CosingRow>> {
        restricted?.let { return it }
        synchronized(this) {
            restricted?.let { return it }
            val map = load(context, "cosing_annex_iii_restricted.csv")
            restricted = map
            return map
        }
    }

    private fun load(context: Context, asset: String): Map<String, List<CosingRow>> {
        val map = HashMap<String, MutableList<CosingRow>>(2_500)
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val cols = parseCsvLine(line)
                if (cols.size < 3 || cols[0].isBlank()) continue
                val row = CosingRow(name = cols[0], cas = cols[1], ref = cols[2])
                val key = normalizeForMatching(row.name)
                if (key.isBlank()) continue
                map.getOrPut(key) { mutableListOf() }.add(row)
            }
        }
        return map
    }
}

/** Same minimal RFC4180-style parser already duplicated in MedicationLookupDb/NonConsumableLookupDb (kept file-local, same rationale). */
private fun parseCsvLine(line: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
            else -> sb.append(c)
        }
        i++
    }
    out.add(sb.toString())
    return out
}

/**
 * Matches a product's declared INCI ingredients (see
 * CosmeticTransparencyScore.parseIngredientsText) against Annex II/III by
 * exact normalized name - not a substring/word-boundary scan across the
 * whole ingredients_text blob, since a short Annex entry name (e.g. "Silver")
 * could otherwise false-positive inside an unrelated longer ingredient name.
 * Each declared ingredient is looked up as its own normalized token.
 */
private fun matchAgainst(ingredients: List<String>, db: Map<String, List<CosingRow>>): List<CosingMatch> {
    val seen = LinkedHashSet<String>() // by reference number, preserves first-seen order
    val result = mutableListOf<CosingMatch>()
    for (ingredient in ingredients) {
        val key = normalizeForMatching(ingredient)
        val rows = db[key] ?: continue
        for (row in rows) {
            if (seen.add(row.ref)) result.add(CosingMatch(row.name, row.cas, row.ref))
        }
    }
    return result
}

/** Returns declared ingredients matching an EU Annex II PROHIBITED substance - genuinely rare (Annex II lists substances that shouldn't legally appear at all), but a real product data error or an older/non-EU formulation can still match. */
fun findProhibitedSubstances(context: Context, ingredientsText: String?): List<CosingMatch> {
    if (ingredientsText.isNullOrBlank()) return emptyList()
    return matchAgainst(parseIngredientsText(ingredientsText), CosingStore.getProhibited(context))
}

/** Returns declared ingredients matching an EU Annex III RESTRICTED-WITH-CONDITIONS substance - see this file's own header on why this is a "regulated" status, not a hazard flag. */
fun findRestrictedSubstances(context: Context, ingredientsText: String?): List<CosingMatch> {
    if (ingredientsText.isNullOrBlank()) return emptyList()
    return matchAgainst(parseIngredientsText(ingredientsText), CosingStore.getRestricted(context))
}

/**
 * Forces both Annex II/III CSV assets to parse and cache now, off whatever
 * dispatcher the caller is already on - see ScanViewModel's init block for
 * why this exists (a Compose-side lookup has no dispatcher of its own to
 * push the first-ever parse cost to).
 */
fun warmCosingCache(context: Context) {
    CosingStore.getProhibited(context)
    CosingStore.getRestricted(context)
}
