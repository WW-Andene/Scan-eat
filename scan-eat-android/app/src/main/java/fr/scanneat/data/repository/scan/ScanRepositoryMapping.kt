package fr.scanneat.data.repository.scan

import com.squareup.moshi.JsonAdapter
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.domain.model.ScanSource

/**
 * Entity → domain mapping for [ScanRepository], split out of the main file.
 * A plain function (not a `ScanHistoryEntity.toDomain()` member extension,
 * since it needs [ScanRepository]'s own JSON adapters) rather than a
 * ScanRepository extension function - kept a callable member on
 * ScanRepository itself (see `toDomain` there) so MockK-based tests that mock
 * ScanRepository's public surface are unaffected by this split.
 */
internal fun mapScanHistoryEntity(
    entity: ScanHistoryEntity,
    productAdapter: JsonAdapter<fr.scanneat.domain.model.Product>,
    auditAdapter: JsonAdapter<fr.scanneat.domain.model.ScoreAudit>,
    warningsAdapter: JsonAdapter<List<String>>,
): ScanResult? = runCatching {
    ScanResult(
        product  = productAdapter.fromJson(entity.productJson)!!,
        audit    = auditAdapter.fromJson(entity.auditJson)!!,
        warnings = warningsAdapter.fromJson(entity.warningsJson) ?: emptyList(),
        source   = ScanSource.valueOf(entity.sourceJson),
        barcode   = entity.barcode,
        dbId      = entity.id,
        favorite  = entity.favorite,
        scannedAt = entity.scannedAt,
    )
}.onFailure {
    // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository -
    // a parse failure here previously vanished the scan from history/favorites
    // with zero trace.
    android.util.Log.w("ScanRepository", "Failed to parse scan history row id=${entity.id} barcode=${entity.barcode}", it)
}.getOrNull()

/** Same "barcode when present, else lowercased name" match key persist()/priorScores() share. */
internal fun matchKeyFor(barcode: String?, productName: String): String = barcode ?: productName.lowercase()
