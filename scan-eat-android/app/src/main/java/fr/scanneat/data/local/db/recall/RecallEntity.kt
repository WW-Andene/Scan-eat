package fr.scanneat.data.local.db.recall

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of RappelConso (official French product-recall) lookups, keyed
 * by barcode — same "offline-first via a barcode-keyed table" shape as
 * [fr.scanneat.data.local.db.scan.OnlineSearchCacheEntity], with two extra
 * roles specific to a recall check:
 *  - [found] distinguishes "checked, no active recall" from "never checked"
 *    (a row with found=false is a real, still-useful negative cache entry —
 *    RecallRepository.checkBarcode should skip a redundant network call for
 *    a barcode already confirmed clean today, not just for barcodes with a
 *    positive hit).
 *  - a positive hit ([found]=true) is kept indefinitely (it's a real safety
 *    record about a product the user scanned), while negative entries are
 *    fine to trim/refresh past a TTL — see RecallRepository's own doc comment.
 */
@Entity(tableName = "recall_cache")
data class RecallEntity(
    @PrimaryKey val barcode: String,
    val found: Boolean,
    val productLabel: String?,
    val recallReason: String?,
    val risks: String?,
    val publicationDate: String?,
    val consumerInstructions: String?,
    val recallSheetUrl: String?,
    val checkedAt: Long, // epoch millis
)
