package fr.scanneat.data.repository.sleep

import fr.scanneat.data.local.db.sleep.SleepDao
import fr.scanneat.data.local.db.sleep.SleepEntity
import fr.scanneat.domain.engine.dashboard.longestLogStreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SleepEntry(
    val id: String,
    val date: LocalDate,
    val bedtimeMs: Long,
    val wakeMs: Long,
    val quality: Int,
    val notes: String,
) {
    val durationHours: Double get() = (wakeMs - bedtimeMs).coerceAtLeast(0L) / 3_600_000.0
}

@Singleton
class SleepRepository @Inject constructor(
    private val dao: SleepDao,
) {
    /** Same cap every other log table in this app already trims to. */
    private companion object {
        const val MAX_HISTORY_ROWS = 730 // ~2 years of nightly entries
    }

    fun observeAll(profileId: String = "default"): Flow<List<SleepEntry>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

    /**
     * Logs/corrects a night's sleep - upserts by (date, profileId), same
     * "one row per day, re-saving corrects it" convention WeightRepository.log
     * already uses, rather than accumulating duplicate rows for the same night.
     * [date] defaults to the wake date, matching SleepEntity's own doc comment
     * on why that (not the bedtime date) is the natural key for "last night".
     */
    suspend fun log(bedtimeMs: Long, wakeMs: Long, quality: Int, notes: String = "", date: LocalDate? = null, profileId: String = "default") {
        val wakeDate = date ?: Instant.ofEpochMilli(wakeMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val existing = dao.findByDate(wakeDate.toString(), profileId)
        dao.upsert(
            SleepEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                date = wakeDate.toString(),
                bedtimeMs = bedtimeMs,
                wakeMs = wakeMs,
                quality = quality.coerceIn(1, 5),
                notes = notes,
                loggedAt = System.currentTimeMillis(),
                profileId = profileId,
            )
        )
        dao.trim(MAX_HISTORY_ROWS, profileId)
    }

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Current streak: consecutive nights (ending today, 1-day grace) meeting
     * [goalHours] - same shape FastingRepository.streak/DashboardStreaks.
     * logStreakDays already use elsewhere in this app.
     */
    fun streak(goalHours: Double, profileId: String = "default"): Flow<Int> = observeAll(profileId).map { list ->
        val metDates = list.filter { it.durationHours >= goalHours }.map { it.date }.toSet()
        if (metDates.isEmpty()) return@map 0
        var day = LocalDate.now()
        if (day !in metDates) {
            day = day.minusDays(1)
            if (day !in metDates) return@map 0
        }
        var streak = 0
        while (day in metDates) { streak++; day = day.minusDays(1) }
        streak
    }

    /** All-time record streak - see longestLogStreak's own doc comment (already generic, reused here). */
    fun longestStreak(goalHours: Double, profileId: String = "default"): Flow<Int> = observeAll(profileId).map { list ->
        longestLogStreak(list.filter { it.durationHours >= goalHours }.mapTo(mutableSetOf()) { it.date })
    }

    suspend fun exportAll(profileId: String = "default"): List<SleepEntity> = dao.getAllForBackup(profileId)
    suspend fun importAll(entities: List<SleepEntity>) {
        if (entities.isEmpty()) return
        dao.insertAll(entities.map { it.copy(quality = it.quality.coerceIn(1, 5)) })
    }
}

private fun SleepEntity.toDomain() = SleepEntry(
    id = id,
    date = LocalDate.parse(date),
    bedtimeMs = bedtimeMs,
    wakeMs = wakeMs,
    quality = quality,
    notes = notes,
)
