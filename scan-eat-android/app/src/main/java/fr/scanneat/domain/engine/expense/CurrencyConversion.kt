package fr.scanneat.domain.engine.expense

// ============================================================================
// CURRENCY CONVERSION — static approximate rates for Settings' currency-symbol
// switch (see PriceRepository.convertAllPrices).
//
// Deliberately a small, hand-maintained table, not a live FX API - Currency
// Settings' own doc comment already documents that this app only ever
// displays a symbol, never fetches real-time rates. A rate here is only ever
// "roughly right at the time this was written", which is exactly why the
// caller must show it to the user before applying it (see
// SettingsScreen's currency-change confirmation dialog) rather than silently
// rescaling financial history on a number nobody agreed to.
// ============================================================================

/** EUR value of 1 unit of the given symbol - the only two presets CurrencySection offers. */
private val RATES_TO_EUR: Map<String, Double> = mapOf(
    "€" to 1.0,
    "$" to 0.92, // ~1 USD in EUR, approximate
)

/**
 * Conversion factor to multiply every existing price by when switching from
 * [from] to [to] - null when either symbol has no known rate (e.g. "Autre"
 * free-text), in which case the caller must fall back to a plain relabel
 * with no conversion, the previous behavior for every symbol.
 */
fun currencyConversionFactor(from: String, to: String): Double? {
    if (from == to) return 1.0
    val fromRate = RATES_TO_EUR[from] ?: return null
    val toRate = RATES_TO_EUR[to] ?: return null
    // fromRate EUR per 1 `from` unit, toRate EUR per 1 `to` unit -
    // amount_in_to = amount_in_from * fromRate / toRate.
    return fromRate / toRate
}
