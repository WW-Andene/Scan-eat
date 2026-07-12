package fr.scanneat.data.local.db.scan

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_history",
    indices = [
        Index("profileId", "scannedAt"),
        Index("barcode", "profileId"),
        // Covers observeFavorites' WHERE profileId = ? AND favorite = 1 ORDER BY
        // scannedAt DESC - a continuously-subscribed Flow held live for the whole
        // history screen (added for the unbounded favorites view) that otherwise
        // full-table-scans on every insert/update to a table that grows unbounded.
        Index("profileId", "favorite", "scannedAt"),
    ],
)
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val productName: String,
    val score: Int,
    val grade: String,
    val category: String,
    val sourceJson: String,          // serialised ScanSource enum
    val productJson: String,         // serialised Product
    val auditJson: String,           // serialised ScoreAudit
    val scannedAt: Long,             // epoch millis
    val profileId: String = "default",
    val favorite: Boolean = false,
)
