package fr.scanneat.domain.engine.nonconsumable

import android.content.Context

// ============================================================================
// NON-CONSUMABLE PRODUCT LOOKUP DB — sourced from Open Products Facts
// (world.openproductsfacts.org, same non-profit org as Open Food Facts;
// community-maintained barcode database covering household/chemical/
// non-food products). Built from OPF's own public bulk CSV export
// (world.openproductsfacts.org/data/en.openproductsfacts.org.products.csv.gz,
// fetched 2026-07-13), filtered to products tagged under OPF's own
// bleach/laundry/household-cleaning category hierarchy — i.e. every row
// here is a real, community-verified OPF entry, not a guess.
//
// Deliberately carries NO per-product toxicology/treatment data - OPF
// doesn't provide verified poison-control-grade safety information, and
// fabricating "if ingested, do X" guidance for a specific chemical would be
// a real harm risk. Exposure guidance in the UI (see ScanViewModel/
// ScanScreen) instead always defers to real emergency services, the only
// medically-honest answer here.
//
// Bundled as assets/nonconsumables_opf.csv (~3,125 rows) and lazily parsed,
// same rationale as MedicationLookupDb's asset-backed approach.
//
// Widened 2026-07-17 beyond the original bleach/laundry/cleaning slice to
// also cover OPF's Health & Beauty, Household Chemicals, Tobacco Products,
// Batteries, Baby & Toddler / Feminine Sanitary Supplies, and Pet Supplies
// category trees (same live API v2 search endpoint, one query per category)
// — a shampoo, toothpaste, cigarette pack, or button battery barcode
// previously matched nothing here and fell straight through to food
// scoring. Any product whose own category tags also indicate it's a
// genuine food/beverage/supplement/medicine (e.g. a magnesium supplement
// filed under OPF's "Health & Beauty" tree) was excluded from this
// widening — flagging something actually meant to be swallowed as "not a
// food product" would be actively wrong, not just imprecise.
enum class NonConsumableCategory {
    BLEACH, CLEANING_PRODUCT, LAUNDRY,
    PERSONAL_CARE, HOUSEHOLD_CHEMICAL, TOBACCO, BATTERY, HYGIENE_PRODUCT, PET_SUPPLY,
    OTHER,
}

data class NonConsumableDbEntry(
    val barcode: String,
    val name: String,
    val brand: String,
    val category: NonConsumableCategory,
)

private object NonConsumableStore {
    @Volatile private var cache: Map<String, NonConsumableDbEntry>? = null

    fun get(context: Context): Map<String, NonConsumableDbEntry> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val map = HashMap<String, NonConsumableDbEntry>(2_000)
            context.assets.open("nonconsumables_opf.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val cols = parseCsvLine(line)
                    if (cols.size < 4) continue
                    val category = runCatching { NonConsumableCategory.valueOf(cols[3]) }.getOrDefault(NonConsumableCategory.OTHER)
                    val entry = NonConsumableDbEntry(barcode = cols[0], name = cols[1], brand = cols[2], category = category)
                    map[entry.barcode] = entry
                }
            }
            cache = map
            return map
        }
    }
}

/** Same minimal RFC4180-style parser as MedicationLookupDb (kept file-local to avoid a cross-module dependency for one tiny helper). */
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

fun findNonConsumableByBarcode(context: Context, barcode: String): NonConsumableDbEntry? =
    NonConsumableStore.get(context)[barcode.filter { it.isDigit() }]

private fun normalizeForMatch(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")
        .lowercase()
        .filter { it.isLetterOrDigit() || it == ' ' }
        .trim()

private fun tokenize(s: String): List<String> = s.split(' ').filter { it.isNotBlank() }

/**
 * Same OCR-name fallback as MedicationLookupDb.findMedicationByName, against
 * OPF's ~3,125 household/chemical/personal-care/tobacco/battery/hygiene/pet
 * entries — and the same token-overlap fix for the same reason: OCR text off
 * a box front rarely matches "brand + name" as one contiguous substring
 * (word order, spacing, or a missing/extra word), even when most of the
 * meaningful tokens are clearly the same product. See
 * MedicationLookupDb.findMedicationByName's own comment for the full
 * rationale — kept duplicated here rather than shared, same as
 * normalizeForMatch/parseCsvLine above.
 */
fun findNonConsumableByName(context: Context, name: String): NonConsumableDbEntry? {
    val query = normalizeForMatch(name)
    if (query.length < 3) return null
    val queryTokens = tokenize(query)
    if (queryTokens.isEmpty()) return null
    var best: NonConsumableDbEntry? = null
    var bestScore = 0.0
    for (entry in NonConsumableStore.get(context).values) {
        val candidateTokens = tokenize(normalizeForMatch("${entry.brand} ${entry.name}"))
        val matched = queryTokens.count { qt -> candidateTokens.any { ct -> ct.contains(qt) || qt.contains(ct) } }
        val score = matched.toDouble() / queryTokens.size
        if (score > bestScore) { bestScore = score; best = entry }
    }
    return if (bestScore >= 0.6) best else null
}
