package fr.scanneat.data.local.db.scan

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only score log, decoupled from scan_history's own upsert-by-barcode
 * dedup scheme. scan_history keeps a single row per barcode (a rescan REPLACEs
 * it in place, to avoid unbounded row growth from re-scanning the same product
 * over and over) - which means a barcoded product's *previous* score is
 * silently overwritten and lost the moment it's rescanned, with nothing left
 * to compute a "score changed by N" delta or a mini history sparkline against.
 * This table exists purely to give ResultViewModel's scoreDelta/scoreHistory
 * something real to query: one row per persist() call, never REPLACEd,
 * trimmed by ScanHistoryDao-style retention rather than upserted away.
 */
@Entity(
    tableName = "scan_score_history",
    indices = [Index("profileId", "matchKey", "scannedAt")],
)
data class ScanScoreHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** barcode when the scan has one, else the product name lowercased - see ScanRepository.matchKeyFor. */
    val matchKey: String,
    val score: Int,
    val scannedAt: Long,
    val profileId: String = "default",
)
