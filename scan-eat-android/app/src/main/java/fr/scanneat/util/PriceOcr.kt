package fr.scanneat.util

// Shared with PriceEntryCard's manual "scan this price tag" button and
// ScanViewModel's live "price near the barcode" detection - one pattern/bound
// so the two paths can't quietly drift into accepting different values.
// Deliberately simple (no currency-symbol anchoring, no picking the "right"
// one among several prices on a promo tag e.g. a crossed-out old price) -
// both call sites treat this as a pre-fill/best-guess, never an
// authoritative value the user can't see or correct.
private val PRICE_PATTERN = Regex("""\d{1,4}[.,]\d{2}""")

fun extractPriceFromText(text: String): Double? =
    PRICE_PATTERN.find(text)?.value?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.01..9999.99 }
