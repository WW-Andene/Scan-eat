package fr.scanneat.data.local.db.scan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScanScoreHistoryDao {
    @Insert
    suspend fun insert(entity: ScanScoreHistoryEntity)

    /**
     * Most-recent-first prior scores for [matchKey], excluding whatever just got
     * inserted at [beforeMillis] or later - so a fresh persist() (which always
     * writes its own row here first, see ScanRepository.persist) doesn't count
     * as its own "prior" score.
     */
    @Query("""
        SELECT score FROM scan_score_history
        WHERE profileId = :profileId AND matchKey = :matchKey AND scannedAt < :beforeMillis
        ORDER BY scannedAt DESC LIMIT :limit
    """)
    suspend fun recentScoresBefore(matchKey: String, beforeMillis: Long, limit: Int = 6, profileId: String = "default"): List<Int>

    @Query("DELETE FROM scan_score_history WHERE profileId = :profileId")
    suspend fun clearAll(profileId: String = "default")

    /** Bounds unbounded growth the same way ScanHistoryDao.trimNonFavorites does for scan_history. */
    @Query("""
        DELETE FROM scan_score_history
        WHERE profileId = :profileId AND id NOT IN (
            SELECT id FROM scan_score_history WHERE profileId = :profileId ORDER BY scannedAt DESC LIMIT :keepCount
        )
    """)
    suspend fun trim(keepCount: Int, profileId: String = "default")

    /** Full unfiltered read/write pair for backup export/import. */
    @Query("SELECT * FROM scan_score_history WHERE profileId = :profileId")
    suspend fun getAllForBackup(profileId: String = "default"): List<ScanScoreHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScanScoreHistoryEntity>)
}
