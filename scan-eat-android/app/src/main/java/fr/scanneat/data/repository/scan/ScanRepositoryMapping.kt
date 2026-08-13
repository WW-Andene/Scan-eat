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
        // code-audit §D5/D6: was bare !! - both failures were already caught
        // and logged by the .onFailure below either way, but a bare NPE gave
        // no hint which field was corrupt; error() names it explicitly.
        product  = productAdapter.fromJson(entity.productJson) ?: error("productJson corrupt for id=${entity.id}"),
        audit    = auditAdapter.fromJson(entity.auditJson) ?: error("auditJson corrupt for id=${entity.id}"),
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
    // id only, not the barcode itself - every other trace in this app logs
    // opaque identifiers, never the underlying value (see CrashLogger's own
    // message-stripping), and a scanned barcode reveals a dietary habit.
    android.util.Log.w("ScanRepository", "Failed to parse scan history row id=${entity.id}", it)
}.getOrNull()

/** Same "barcode when present, else lowercased name" match key persist()/priorScores() share. */
internal fun matchKeyFor(barcode: String?, productName: String): String = barcode ?: productName.lowercase()
