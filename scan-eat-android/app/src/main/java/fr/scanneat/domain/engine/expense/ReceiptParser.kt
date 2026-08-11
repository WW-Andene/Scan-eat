package fr.scanneat.domain.engine.expense

/** One candidate line extracted from a receipt's OCR text. */
data class ReceiptLine(val rawName: String, val priceEuros: Double)

// Lines that are never a purchased item, even though they often carry a
// trailing amount that looks exactly like a price (a total, a tax line, the
// change given back...). Matched as a whole-line substring check, case/accent
// insensitive - receipt printers abbreviate these inconsistently (TOTAL,
// TOTAL TTC, S/TOTAL, MONTANT DU, etc.) so this errs toward a short common
// stem rather than an exact phrase.
private val EXCLUDED_STEMS = listOf(
    "total", "s/total", "sous total", "montant", "tva", "eco-part", "eco part",
    "carte", "especes", "cheque", "cb ", "rendu", "a payer", "du client",
    "nb article", "nombre article",
)

// A trailing price: 1-4 digits, decimal separator (, or .), 2 digits,
// optionally followed by a currency mark and/or an item count/discount code
// receipts commonly print after the amount (e.g. "1,99 E" or "1,99 *").
private val TRAILING_PRICE = Regex("""(\d{1,4}[.,]\d{2})\s*[A-Za-z€]{0,2}\*?\s*$""")

private fun stripAccents(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

/**
 * Extracts (name, price) candidates from a receipt's raw OCR text, one line
 * at a time. Deliberately conservative: a line with no parseable trailing
 * price, an empty name once the price is stripped, or a recognizable
 * total/tax/payment-method line is simply skipped rather than guessed at -
 * the caller (ReceiptScanViewModel) always shows the result as an editable
 * review list, never auto-logs anything unreviewed.
 */
fun parseReceiptLines(ocrText: String): List<ReceiptLine> {
    return ocrText.lines().mapNotNull { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@mapNotNull null
        val normalized = stripAccents(line).lowercase()
        if (EXCLUDED_STEMS.any { normalized.contains(it) }) return@mapNotNull null

        val match = TRAILING_PRICE.find(line) ?: return@mapNotNull null
        val price = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        if (price <= 0.0 || price > 500.0) return@mapNotNull null // a single receipt line above 500€ is far more likely a misread total than a real item

        val name = line.substring(0, match.range.first).trim(' ', '-', '.', '*')
        if (name.isEmpty() || name.length < 2) return@mapNotNull null

        ReceiptLine(rawName = name, priceEuros = price)
    }
}
