package fr.scanneat.data.repository.recall

import fr.scanneat.data.local.db.recall.RecallDao
import fr.scanneat.data.local.db.recall.RecallEntity
import fr.scanneat.data.remote.api.RappelConsoApi
import fr.scanneat.data.remote.api.rappelConsoGtinWhereClause
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A confirmed RappelConso match for a scanned barcode, in whichever
 *  language the caller wants displayed - the underlying dataset is
 *  French-only (a government publication, no English translation exists),
 *  so [reasonFr]/[risksFr]/[instructionsFr] are always shown even in English
 *  mode with an explanatory label, same "don't fabricate a translation of an
 *  official safety notice" discipline the rest of this app's sourced content
 *  follows. */
data class RecallEntry(
    val productLabel: String?,
    val reasonFr: String?,
    val risksFr: String?,
    val instructionsFr: String?,
    val recallSheetUrl: String?,
    val publicationDate: String?,
)

// Negative cache entries (barcode checked, no recall found) are refreshed
// after a week - RappelConso publishes new recalls continuously, and a
// product cleared a week ago could have been recalled since. Positive
// entries never expire (see RecallDao.trimStaleNegatives / RecallEntity's
// own doc comment) - once a barcode is a confirmed recall, that fact doesn't
// need re-verifying every scan.
private val NEGATIVE_CACHE_TTL_MS = TimeUnit.DAYS.toMillis(7)

/**
 * Checks a scanned barcode against RappelConso, the official French
 * government product-recall dataset (DGCCRF/DGAL/DGEC/DGPR) - population-
 * level food/product safety, not a Profile.healthConditions personalization.
 *
 * Deliberately best-effort and silent on failure, same discipline
 * ScanRepository.findNonConsumableViaOpf already established for an optional
 * side-lookup that must never block or break the main scan flow: any
 * network/parse error here returns null, exactly like "no recall found" -
 * never surfaced as an error to the user, never thrown.
 */
@Singleton
class RecallRepository @Inject constructor(
    private val api: RappelConsoApi,
    private val dao: RecallDao,
) {
    suspend fun checkBarcode(barcode: String): RecallEntry? {
        // GTINs are purely numeric (8/12/13/14 digits) - guards against
        // building a malformed ODSQL where-clause from a non-GTIN barcode
        // format (e.g. an internal/QR code) that would never match anyway.
        if (barcode.isBlank() || !barcode.all { it.isDigit() }) return null

        dao.findByBarcode(barcode)?.let { cached ->
            val stale = !cached.found && System.currentTimeMillis() - cached.checkedAt > NEGATIVE_CACHE_TTL_MS
            if (!stale) return if (cached.found) cached.toEntry() else null
        }

        return try {
            val response = api.findByGtin(rappelConsoGtinWhereClause(barcode))
            val record = response.results?.firstOrNull()
            val entity = if (record != null) {
                RecallEntity(
                    barcode = barcode, found = true,
                    productLabel = record.libelle, recallReason = record.motifRappel,
                    risks = record.risquesEncourus, publicationDate = record.datePublication,
                    consumerInstructions = record.conduitesATenir, recallSheetUrl = record.lienVersLaFicheRappel,
                    checkedAt = System.currentTimeMillis(),
                )
            } else {
                RecallEntity(
                    barcode = barcode, found = false,
                    productLabel = null, recallReason = null, risks = null,
                    publicationDate = null, consumerInstructions = null, recallSheetUrl = null,
                    checkedAt = System.currentTimeMillis(),
                )
            }
            dao.upsert(entity)
            if (entity.found) entity.toEntry() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort: an offline device or a RappelConso outage should
            // never surface an error or block the main scan flow - see this
            // class's own doc comment.
            null
        }
    }

    private fun RecallEntity.toEntry() = RecallEntry(
        productLabel = productLabel, reasonFr = recallReason, risksFr = risks,
        instructionsFr = consumerInstructions, recallSheetUrl = recallSheetUrl,
        publicationDate = publicationDate,
    )
}
