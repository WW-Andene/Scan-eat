package fr.scanneat.data.repository.scan

import fr.scanneat.domain.model.ScanResult

/**
 * Thrown when a photo had no barcode and no readable label table, but its
 * biggest text looked like a product name with matches: [options] are real
 * Open Food Facts products found by that name and/or a dish of the built-in
 * table (exact, composed or partial), for the user to pick from.
 */
class ProductNameCandidates(val query: String, val options: List<ScanResult>) : Exception("name candidates for $query")
