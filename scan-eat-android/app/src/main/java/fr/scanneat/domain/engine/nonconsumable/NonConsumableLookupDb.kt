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
// Bundled as assets/nonconsumables_opf.csv (~1,250 rows) and lazily parsed,
// same rationale as MedicationLookupDb's asset-backed approach.
// ============================================================================

enum class NonConsumableCategory { BLEACH, CLEANING_PRODUCT, LAUNDRY, OTHER }

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
