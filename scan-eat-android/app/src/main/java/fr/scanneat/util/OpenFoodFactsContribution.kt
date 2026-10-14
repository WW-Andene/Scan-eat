package fr.scanneat.util

/**
 * Roadmap N1: contributing to Open Food Facts goes through OFF's own product
 * page (add or edit under the user's own OFF account) - the app never handles
 * OFF credentials or writes to the shared database itself.
 */
fun offContributionUrl(barcode: String): String? =
    barcode.filter { it.isDigit() }.takeIf { it.length in 8..14 }?.let { "https://world.openfoodfacts.org/product/$it" }
