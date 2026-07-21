package fr.scanneat.util

/**
 * Every plausible GTIN encoding for [barcode], most-likely-first, deduplicated —
 * scanners hand back the code as printed (12-digit UPC-A on many North American
 * cans, 13-digit EAN-13 elsewhere, compressed UPC-E on small packaging like soda
 * cans and candy, or a 14-digit case/pallet GTIN), but OFF only indexes the
 * expanded UPC-A/EAN-13 form, so a compressed or differently-padded code misses
 * even though the product is in the database.
 */
fun barcodeCandidates(barcode: String): List<String> {
    val candidates = LinkedHashSet<String>()
    candidates += barcode

    upcEToUpcA(barcode)?.let { upcA ->
        candidates += upcA
        candidates += "0$upcA" // EAN-13 form of the expanded UPC-A
    }

    gtin14ToEan13(barcode)?.let { ean13 ->
        candidates += ean13
        if (ean13.startsWith("0")) candidates += ean13.substring(1) // UPC-A form
    }

    when {
        barcode.length == 12 -> candidates += "0$barcode"
        barcode.length == 13 && barcode.startsWith("0") -> candidates += barcode.substring(1)
    }

    return candidates.toList()
}

/**
 * Strips a GTIN-14 case/pallet code's leading packaging-indicator digit
 * (0–8) down to the consumer-unit EAN-13, recomputing the check digit
 * over the remaining payload — case codes on bulk/wholesale packaging
 * aren't indexed in OFF under their 14-digit form, only under the
 * underlying retail GTIN.
 */
private fun gtin14ToEan13(code: String): String? {
    if (code.length != 14 || !code.all { it.isDigit() }) return null
    val payload12 = code.substring(1, 13)
    return "0$payload12" + ean13CheckDigit(payload12)
}

/** Standard mod-10 EAN-13 check digit for a 12-digit payload. */
private fun ean13CheckDigit(payload12: String): Int {
    val sum = payload12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
    return (10 - (sum % 10)) % 10
}

/**
 * Expands a compressed UPC-E code (6 core digits, optionally with a
 * leading number-system digit and/or trailing check digit — 6, 7, or 8
 * chars total) to its 12-digit UPC-A form, per the standard GS1
 * expansion rules keyed on the last core digit. Only called as a
 * fallback after the code as printed already 404'd, so a false-positive
 * guess (e.g. an EAN-8 code that happens to also parse as UPC-E) just
 * fails its own lookup too — it can't return the wrong product.
 */
private fun upcEToUpcA(code: String): String? {
    if (!code.all { it.isDigit() }) return null
    val (numberSystem, core) = when (code.length) {
        6 -> '0' to code
        7 -> '0' to code.take(6)
        8 -> code[0] to code.substring(1, 7)
        else -> return null
    }
    if (numberSystem != '0' && numberSystem != '1') return null

    val d = core.map { it - '0' }
    val (manufacturer, product) = when (d[5]) {
        0, 1, 2 -> "${d[0]}${d[1]}${d[5]}00" to "00${d[2]}${d[3]}${d[4]}"
        3       -> "${d[0]}${d[1]}${d[2]}00" to "000${d[3]}${d[4]}"
        4       -> "${d[0]}${d[1]}${d[2]}${d[3]}0" to "0000${d[4]}"
        else    -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}" to "0000${d[5]}"
    }
    val upcA11 = "$numberSystem$manufacturer$product"
    return upcA11 + upcCheckDigit(upcA11)
}

/** Standard mod-10 UPC/EAN check digit for an 11-digit UPC-A payload. */
private fun upcCheckDigit(payload11: String): Int {
    val sum = payload11.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
    return (10 - (sum % 10)) % 10
}
