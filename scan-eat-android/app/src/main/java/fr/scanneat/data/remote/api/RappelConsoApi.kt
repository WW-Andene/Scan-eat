package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query
import fr.scanneat.util.barcodeCandidates

// ============================================================================
// RAPPELCONSO — official French product-recall open data (DGCCRF/DGAL/DGEC/
// DGPR via data.economie.gouv.fr, Opendatasoft Explore API v2.1). Since
// 2024-11-29 the dataset carries a GTIN (barcode) column, so a scanned
// barcode can be checked against real, government-published recall notices —
// unlike the score/hint personalization elsewhere in this package, this is
// population-level, not Profile.healthConditions-gated: a genuine safety
// recall applies to anyone who has this exact product, not just users with a
// declared condition.
//
// Field names below are the dataset's real column names (verified against a
// live API response), not guessed:
// gtin, libelle (product name), motif_rappel (recall reason),
// risques_encourus (risks), date_publication (recall date),
// conduites_a_tenir_par_le_consommateur (what to do), liens_vers_les_images.
// ============================================================================

interface RappelConsoApi {
    @GET("api/explore/v2.1/catalog/datasets/rappelconso-v2-gtin-trie/records")
    suspend fun findByGtin(
        @Query("where") whereClause: String,
        @Query("limit") limit: Int = 5,
    ): RappelConsoResponse

    /** Roadmap C3: every recall published since a date, newest first, for the
     *  daily background sync (RecallSyncWorker). Opendatasoft caps `limit` at 100. */
    @GET("api/explore/v2.1/catalog/datasets/rappelconso-v2-gtin-trie/records")
    suspend fun findPublishedSince(
        @Query("where") whereClause: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("order_by") orderBy: String = "date_publication desc",
    ): RappelConsoResponse
}

/** ODSQL clause for recalls published on/after [isoDate] (yyyy-MM-dd). */
fun rappelConsoPublishedSinceClause(isoDate: String): String = "date_publication>=\"$isoDate\""

/** Leading zeros are not significant in a GTIN, and RappelConso's `gtin`
 *  column is a JSON number (a UPC printed as 0208028000000 comes back as
 *  208028000000) - compare codes on this key. */
fun gtinMatchKey(code: String): String = code.trim().trimStart('0')

/**
 * Pure matcher for the background sync: which of the user's [ownedBarcodes]
 * (scan history + pantry) appear in [records]. Every encoding of each owned
 * code (UPC-E expansion, GTIN-14 case code...) is tried. Returns owned barcode
 * → first matching record.
 */
fun matchRecallsToOwnedBarcodes(
    records: List<RappelConsoRecordDto>,
    ownedBarcodes: Collection<String>,
): Map<String, RappelConsoRecordDto> {
    val byKey = HashMap<String, RappelConsoRecordDto>()
    records.forEach { r -> r.gtin?.let { g -> byKey.putIfAbsent(gtinMatchKey(g), r) } }
    if (byKey.isEmpty()) return emptyMap()
    val result = LinkedHashMap<String, RappelConsoRecordDto>()
    ownedBarcodes.filter { b -> b.isNotBlank() && b.all { it.isDigit() } }.forEach { owned ->
        barcodeCandidates(owned).firstNotNullOfOrNull { byKey[gtinMatchKey(it)] }?.let { result[owned] = it }
    }
    return result
}

/** Builds the `where` clause matching the scanned barcode in every plausible
 *  GTIN encoding (UPC-A/EAN-13 padding, expanded UPC-E, GTIN-14 case code) -
 *  an exact match on the code as printed missed a recall listed under the
 *  other encoding of the same product. Opendatasoft ODSQL string equality
 *  (`field="value"`) joined with OR; candidates are purely numeric (the caller
 *  validates digits), so no escaping is needed. */
fun rappelConsoGtinWhereClause(barcode: String): String =
    barcodeCandidates(barcode).filter { c -> c.all { it.isDigit() } }.distinct()
        .joinToString(" OR ") { "gtin=\"$it\"" }

@JsonClass(generateAdapter = true)
data class RappelConsoResponse(
    @Json(name = "total_count") val totalCount: Int,
    val results: List<RappelConsoRecordDto>?,
)

@JsonClass(generateAdapter = true)
data class RappelConsoRecordDto(
    val gtin: String?,
    val libelle: String?,
    @Json(name = "motif_rappel") val motifRappel: String?,
    @Json(name = "risques_encourus") val risquesEncourus: String?,
    @Json(name = "date_publication") val datePublication: String?,
    @Json(name = "conduites_a_tenir_par_le_consommateur") val conduitesATenir: String?,
    @Json(name = "liens_vers_les_images") val liensVersLesImages: String?,
    @Json(name = "lien_vers_la_fiche_rappel") val lienVersLaFicheRappel: String?,
)
