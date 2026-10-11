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
        // MIGRATION_15_16 creates this index on the real database (for
        // findBetterInCategory's WHERE profileId = ? AND category = ? ORDER BY
        // score) but it was never declared here - Room validates a migrated DB's
        // on-disk schema against the schema it derives from these annotations, so
        // the mismatch would fail that validation and crash every upgrading user
        // on next launch, while fresh installs (which never run the migration)
        // silently never got the index at all.
        Index("profileId", "category", "score"),
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
    // serialised List<String> - OcrParser.buildWarnings()/source-conflict messages
    // computed for the scan but previously never persisted, so ResultContent's
    // WarningsSection could never render scan.warnings for any scan, fresh or
    // historical (only audit.warnings, a separate field, ever showed up).
    val warningsJson: String = "[]",
    // ScanRepository.persist() always passes true explicitly - a row only ever
    // reaches persist() after scoreDirectBarcode/scoreViaServer's classifyNonFood
    // check has already passed (a non-food barcode throws NonFoodProductException
    // before persist() is reached). The raw default stays false on purpose: it's
    // what MIGRATION_24_25 backfills pre-existing rows to, and - just as
    // importantly - what BackupRepository's Moshi restore falls back to for any
    // backup JSON written before this field existed (KotlinJsonAdapterFactory
    // applies a missing key's constructor default). A restored pre-classifier
    // backup is exactly the same "might be a mis-scored non-food item" case the
    // migration handles, so it needs the same false, not persist()'s true.
    // ScanRepository.scoreBarcode's cache-hit shortcut treats false as "not yet
    // verified" and re-runs a real lookup once for that barcode instead of
    // trusting the stale cached score forever.
    val nonFoodChecked: Boolean = false,
)
