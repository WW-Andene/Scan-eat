package fr.scanneat.data.local.db.scan

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-requested "typing cache" for Recherche's online (Open Food Facts)
 * search - every OFF result the app has ever fetched, so a re-typed or
 * related query can show instant, offline suggestions from a prior session
 * without re-hitting OFF's rate-limited search endpoint. One row per barcode
 * (REPLACEd on refetch), same JSON-via-Moshi shape as [ScanHistoryEntity]'s
 * productJson/auditJson/warningsJson/sourceJson columns, deliberately kept as
 * a *separate* table rather than folded into scan_history: this is
 * throwaway "seen while searching" data with no favorite/profile/score-
 * history semantics of its own, and unlike scan_history (a genuine record of
 * what the user actually scanned, kept forever) it's fine to trim once it
 * grows past a bound - see OnlineSearchCacheDao.trimTo.
 */
@Entity(tableName = "online_search_cache")
data class OnlineSearchCacheEntity(
    @PrimaryKey val barcode: String,
    val productJson: String,   // serialised Product
    val auditJson: String,     // serialised ScoreAudit
    val sourceJson: String,    // serialised ScanSource enum name
    val warningsJson: String = "[]",
    val cachedAt: Long,        // epoch millis - trimTo's eviction order
)
