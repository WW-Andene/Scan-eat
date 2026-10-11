package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

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
}

/** Builds the `where` clause for an exact GTIN match — Opendatasoft ODSQL
 *  string-equality syntax (`field="value"`). The barcode is purely numeric
 *  (validated by the caller before this is built), so no escaping is needed. */
fun rappelConsoGtinWhereClause(barcode: String): String = "gtin=\"$barcode\""

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
