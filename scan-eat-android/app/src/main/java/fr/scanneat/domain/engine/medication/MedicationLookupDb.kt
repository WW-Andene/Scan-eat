package fr.scanneat.domain.engine.medication

import android.content.Context

// ============================================================================
// MEDICATION LOOKUP DB — sourced from the official French public drug
// database (BDPM, ANSM / base-donnees-publique.medicaments.gouv.fr),
// joining CIS_bdpm.txt (drug identity), CIS_CIP_bdpm.txt (CIP13 barcode per
// commercial presentation), CIS_COMPO_bdpm.txt (active substance(s) per
// drug) and CIS_CPD_bdpm.txt (dispensing condition, e.g. "liste I",
// "réservé à l'usage HOSPITALIER" — blank means no restriction, i.e. sold
// over the counter). Snapshot fetched from the live BDPM flat files on
// 2026-07-13, filtered to actively marketed ("Commercialisée") drugs with
// an active presentation and a valid 13-digit CIP13, excluding
// homeopathic entries (their "denomination" is a dilution range, not a
// scannable product name). Bundled as assets/medications_bdpm.csv (~12,300
// rows) rather than a Kotlin literal — a list that size would blow past the
// JVM's 64KB per-method bytecode limit if built as one big list-of-data-
// -class-calls, and a plain-text asset is trivially diffable/re-generatable
// from the same four source files for a future refresh.
// ============================================================================

data class MedicationDbEntry(
    val barcode: String,               // CIP13, EAN-13 scannable on the box
    val name: String,                  // BDPM denomination
    val form: String,                  // pharmaceutical form (comprimé, gélule, ...)
    val route: String,                 // administration route (orale, ...)
    val holder: String,                // marketing authorization holder
    val cis: String,                   // BDPM's own drug identifier (CIS code)
    val activeSubstances: List<String>, // BDPM "dénomination commune" per active substance (may be empty for rare combination products)
    val dispensingConditions: List<String>, // e.g. "liste I", "stupéfiant" — empty means no restriction (sold OTC)
)

private object MedicationStore {
    @Volatile private var cache: Map<String, MedicationDbEntry>? = null

    fun get(context: Context): Map<String, MedicationDbEntry> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val map = HashMap<String, MedicationDbEntry>(16_000)
            context.assets.open("medications_bdpm.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val cols = parseCsvLine(line)
                    if (cols.size < 6) continue
                    val entry = MedicationDbEntry(
                        barcode = cols[0], name = cols[1], form = cols[2],
                        route = cols[3], holder = cols[4], cis = cols[5],
                        activeSubstances = cols.getOrNull(6)?.split(";")?.filter { it.isNotBlank() } ?: emptyList(),
                        dispensingConditions = cols.getOrNull(7)?.split(";")?.filter { it.isNotBlank() } ?: emptyList(),
                    )
                    map[entry.barcode] = entry
                }
            }
            cache = map
            return map
        }
    }
}

/**
 * Minimal RFC4180-style single-line CSV parser: handles double-quoted fields
 * (needed because drug names routinely contain commas, e.g. "X 500 mg,
 * comprimé"), with "" as the escaped-quote sequence inside a quoted field.
 * No embedded-newline support — the generating export never produces one.
 */
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
 * The BDPM CSV keys entries by CIP13 (13 digits) — but a 2D DataMatrix/QR on
 * the box (see ScanScreen.extractGtinFromGs1) yields a GTIN-14, formed per
 * GS1 France pharma rules by prepending a packaging-indicator digit (almost
 * always "3") to the CIP13. A direct 14-digit lookup would always miss, so
 * this also tries the CIP13 obtained by dropping that leading digit.
 */
fun findMedicationByBarcode(context: Context, barcode: String): MedicationDbEntry? {
    val digits = barcode.filter { it.isDigit() }
    val store = MedicationStore.get(context)
    store[digits]?.let { return it }
    if (digits.length == 14) store[digits.substring(1)]?.let { return it }
    return null
}

private fun normalizeForMatch(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")
        .lowercase()
        .filter { it.isLetterOrDigit() || it == ' ' }
        .trim()

private fun tokenize(s: String): List<String> = s.split(' ').filter { it.isNotBlank() }

/**
 * Fallback for when there's no barcode/DataMatrix/QR to scan at all — OCR
 * reads the drug/product name off the box (see OcrParser.identifyFood, via
 * ScanRepository.identifyOrScoreFromImages), and this matches it against
 * BDPM's ~12,300 entries by name instead. A linear
 * scan is fine here: this only runs once per explicit user action ("identify
 * without a label"), never per camera frame.
 *
 * Token-overlap scoring rather than a single whole-string containment check
 * (either direction) - OCR text off a box front rarely matches the full
 * BDPM denomination as one contiguous substring even when it's clearly the
 * same drug: a dosage like "1000mg" (OCR, no space before the unit) never
 * appears as a substring of "1000 mg" (BDPM, space-separated) despite
 * meaning the same thing, and OCR routinely drops the trailing
 * pharmaceutical-form suffix ("comprimé, boîte de 8") the official name
 * always carries. Requiring only a majority of the query's own tokens to
 * each appear (substring match, either direction, so "1000mg" still matches
 * a "1000" token) somewhere in the candidate tolerates that kind of
 * reordering/spacing noise, and keeps the single best-scoring entry instead
 * of whichever happened to be first in map iteration order to clear the
 * old any-overlap bar.
 */
fun findMedicationByName(context: Context, name: String): MedicationDbEntry? {
    val query = normalizeForMatch(name)
    if (query.length < 3) return null
    val queryTokens = tokenize(query)
    if (queryTokens.isEmpty()) return null
    var best: MedicationDbEntry? = null
    var bestScore = 0.0
    for (entry in MedicationStore.get(context).values) {
        val candidateTokens = tokenize(normalizeForMatch(entry.name))
        val matched = queryTokens.count { qt -> candidateTokens.any { ct -> ct.contains(qt) || qt.contains(ct) } }
        val score = matched.toDouble() / queryTokens.size
        if (score > bestScore) { bestScore = score; best = entry }
    }
    return if (bestScore >= 0.6) best else null
}
