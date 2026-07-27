package fr.scanneat.domain.engine.scoring

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

// ============================================================================
// ADDITIVES DATABASE — port of scoring-engine.ts SECTION 2
// Source citations preserved verbatim from the original TS engine.
//
// Data split across sibling files in this package (models + per-tier
// entries); this file assembles ADDITIVES_DB and holds the lookup logic:
//   AdditiveModels.kt                    - enums, AdditiveInfo, COSMETIC_ADDITIVE_CATEGORIES
//   AdditivesTier1.kt                    - Tier 1 (serious concern)
//   AdditivesTier2.kt                    - Tier 2 (moderate concern)
//   AdditivesTier3Core.kt                - Tier 3 core set
//   AdditivesTier3NaturalColorants.kt     - Tier 3 natural colorants + misc
//   AdditivesTier3BakeryAndGases.kt       - Tier 3 bakery/packaging-gas set
// ============================================================================

val ADDITIVES_DB: List<AdditiveInfo> =
    ADDITIVES_TIER1 +
        ADDITIVES_TIER2 +
        ADDITIVES_TIER3_CORE +
        ADDITIVES_TIER3_NATURAL_COLORANTS +
        ADDITIVES_TIER3_BAKERY_AND_GASES

// ============================================================================
// Lookup helpers
// ============================================================================

/** Normalize for matching: lowercase, strip accents, collapse spaces. */
fun normalizeForMatching(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9\\s-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Find additive info from an ingredient E-number or name. Null if unknown.
 *
 * Memoized: ProcessingPillar, AdditiveRiskPillar, and IngredientIntegrityPillar
 * each independently call this for the same ingredient during a single
 * scoreProduct() run, so without a cache the same O(n) linear + synonym-
 * substring scan over ADDITIVES_DB runs up to 3x per ingredient. The cache is
 * local to this function (no call-site or signature changes needed in any
 * pillar) and keyed on the exact 3 inputs, which fully determine the result -
 * a Ingredient-shaped sentinel (NOT_FOUND) distinguishes a cached miss from
 * "not yet looked up" since ConcurrentHashMap can't store null values.
 */
private val NOT_FOUND = AdditiveInfo("", emptyList(), AdditiveTier.THREE, AdditiveCategory.ANTICAKING, "", "")
private val additiveLookupCache = ConcurrentHashMap<Triple<String?, String, fr.scanneat.domain.model.IngredientCategory?>, AdditiveInfo>()

// Guards the check-then-clear eviction in findAdditive() below - additiveLookupCache
// being a ConcurrentHashMap only makes each individual get/put atomic, not the "if
// size >= max then clear" sequence itself. Without this lock, many concurrent
// findAdditive() calls near the size threshold could each observe the cache full and
// all call clear() around the same time, repeatedly wiping entries other callers just
// inserted (a cache-cold stampede) instead of evicting once. Mirrors the server's
// identical fix (scan-eat-server's AdditivesDb.kt).
private val additiveCacheEvictionLock = Any()

// Keyed on the raw ingredient `name` string from scanned products, which is
// free-text and not drawn from a fixed, small vocabulary - distinct
// spellings/languages/OCR variants accumulate over a long-running app session
// as scan volume grows, so left unchecked this cache would grow without bound.
// Mirrors the server's identical cap (scan-eat-server's AdditivesDb.kt) -
// that fix was applied there but never ported back to this copy. There's no
// per-entry expiry need (the mapping is pure/deterministic), just a safety
// valve: once the cache would grow past a bound, drop it and start fresh.
private const val MAX_ADDITIVE_CACHE_ENTRIES = 20_000

/**
 * ADDITIVES_DB's synonyms, pre-normalized once at class-init time instead of on
 * every computeFindAdditive() call. normalizeForMatching() runs a lowercase +
 * Normalizer.normalize() + 3 regex passes, and computeFindAdditive() used to
 * call it on every synonym of every one of the ~90 additives (roughly 200
 * synonym strings) each time it ran a name-based scan - all on data that never
 * changes at runtime. The outer additiveLookupCache above already dedupes
 * repeat lookups for the *same* ingredient, but distinct ingredient names each
 * still paid the full re-normalization cost of the entire synonym table.
 * Mirrors the server's identical optimization (scan-eat-server's
 * AdditivesDb.kt) - present there but never ported back to this copy, the
 * same gap MAX_ADDITIVE_CACHE_ENTRIES above already had before its own fix.
 */
private val NORMALIZED_ADDITIVES: List<Pair<AdditiveInfo, List<String>>> =
    ADDITIVES_DB.map { it to it.names.map(::normalizeForMatching) }

fun findAdditive(eNumber: String?, name: String, category: fr.scanneat.domain.model.IngredientCategory?): AdditiveInfo? {
    val key = Triple(eNumber, name, category)
    additiveLookupCache[key]?.let { return if (it === NOT_FOUND) null else it }
    val result = computeFindAdditive(eNumber, name, category)
    synchronized(additiveCacheEvictionLock) {
        if (additiveLookupCache.size >= MAX_ADDITIVE_CACHE_ENTRIES) additiveLookupCache.clear()
    }
    additiveLookupCache[key] = result ?: NOT_FOUND
    return result
}

private fun computeFindAdditive(eNumber: String?, name: String, category: fr.scanneat.domain.model.IngredientCategory?): AdditiveInfo? {
    // Direct E-number match
    if (eNumber != null) {
        val norm = eNumber.uppercase().replace("\\s".toRegex(), "")
        ADDITIVES_DB.find { it.eNumber == norm }?.let { return it }
    }

    // Name-based match — longest matching synonym wins, not simply the first
    // ADDITIVES_DB entry (in declaration order) with any substring hit. E150's
    // own synonym "caramel" is itself a substring of E150a's "caramel
    // ordinaire"/"plain caramel" and E150b's "caramel caustique" - both
    // genuinely Tier 3 ("no 4-MEI concern") - so a first-match scan silently
    // shadowed them behind generic E150 (Tier 2, "commercial caramel in dark
    // sodas... 4-MEI concern"), applying the harsher deduction to a plain
    // caramel colorant that doesn't carry that concern at all. A longest-
    // synonym-wins rule picks the most specific match regardless of where
    // either entry sits in the list, and generalizes to any future additive
    // with the same kind of generic-name-is-a-substring-of-specific-name
    // collision (verified against the whole DB: also fixes E223 vs E221 and
    // E942 vs E941 the same way).
    val normName = normalizeForMatching(name)
    var bestMatch: AdditiveInfo? = null
    var bestSynonymLength = 0
    for ((additive, normSynonyms) in NORMALIZED_ADDITIVES) {
        for (normSynonym in normSynonyms) {
            if (normSynonym.isNotEmpty() && normName.contains(normSynonym) && normSynonym.length > bestSynonymLength) {
                bestMatch = additive
                bestSynonymLength = normSynonym.length
            }
        }
    }
    if (bestMatch != null) return bestMatch

    // Context-aware match for natural colorants
    if (category == fr.scanneat.domain.model.IngredientCategory.ADDITIVE) {
        val naturalColorants = mapOf(
            "curcuma" to "E100", "curcumin" to "E100",
            "paprika" to "E160c", "betterave" to "E162",
            "carmin" to "E120", "cochenille" to "E120",
            "caramel" to "E150",
        )
        for ((keyword, eNum) in naturalColorants) {
            if (normName.contains(keyword)) {
                ADDITIVES_DB.find { it.eNumber == eNum }?.let { return it }
            }
        }
    }
    return null
}
