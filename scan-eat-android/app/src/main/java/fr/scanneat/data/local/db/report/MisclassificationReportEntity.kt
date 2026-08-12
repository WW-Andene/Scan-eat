package fr.scanneat.data.local.db.report

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-requested: "système de correction communautaire quand un produit est
 * mal classé" - e.g. a shampoo scored as food, or a real food product thrown
 * into ScanStateOverlay's non-food dialog (see classifyNonFood's own header
 * for how that classification is made and why it's occasionally wrong on a
 * keyword/tag heuristic). No server-side aggregation exists for this app
 * (scan-eat-server is stateless - scoring/AI calls only, no user DB), so this
 * is a local, per-device log rather than a real "community" database: it
 * gives the reporting user a durable record of what they flagged and why,
 * exportable (see MisclassificationReportRepository.toCsv) so it can be
 * manually relayed into a classifyNonFood tuning pass, the same way this
 * session's own "creme"/"cream" regression was found and fixed by hand.
 */
@Entity(
    tableName = "misclassification_reports",
    indices = [Index(value = ["profileId", "reportedAt"])],
)
data class MisclassificationReportEntity(
    @PrimaryKey val id: String,
    val barcode: String?,
    val productName: String,
    val brand: String,
    /** What the app currently classified this as - "FOOD" or a NonConsumableCategory.name. */
    val currentClassification: String,
    /** What the user says it should be instead - same "FOOD" or NonConsumableCategory.name vocabulary. */
    val correctedClassification: String,
    val note: String,
    val reportedAt: Long,       // epoch millis
    val profileId: String = "default",
)
