package fr.scanneat.data.repository.mood

import fr.scanneat.data.local.db.mood.MoodDao
import fr.scanneat.data.local.db.mood.MoodEntity
import fr.scanneat.domain.engine.dashboard.longestLogStreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class MoodEntry(
    val id: String,
    val date: LocalDate,
    val mood: Int,
    val stress: Int,
    val notes: String,
)

@Singleton
class MoodRepository @Inject constructor(
    private val dao: MoodDao,
) {
    /** Same cap every other log table in this app already trims to. */
    private companion object {
        const val MAX_HISTORY_ROWS = 730 // ~2 years of daily entries
    }

    fun observeAll(profileId: String = "default"): Flow<List<MoodEntry>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

    /**
     * Logs/corrects today's (or [date]'s) mood+stress - upserts by
     * (date, profileId), same "one row per day, re-saving corrects it"
     * convention SleepRepository.log/WeightRepository.log already use.
     */
    suspend fun log(mood: Int, stress: Int, notes: String = "", date: LocalDate? = null, profileId: String = "default") {
        val d = date ?: LocalDate.now()
        val existing = dao.findByDate(d.toString(), profileId)
        dao.upsert(
            MoodEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                date = d.toString(),
                mood = mood.coerceIn(1, 5),
                stress = stress.coerceIn(1, 5),
                notes = notes,
                loggedAt = System.currentTimeMillis(),
                profileId = profileId,
            )
        )
        dao.trim(MAX_HISTORY_ROWS, profileId)
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Current streak: consecutive days (ending today, 1-day grace) with a logged entry -
     *  same shape SleepRepository.streak/DashboardStreaks.logStreakDays already use. */
    fun streak(profileId: String = "default"): Flow<Int> = observeAll(profileId).map { list ->
        val loggedDates = list.map { it.date }.toSet()
        if (loggedDates.isEmpty()) return@map 0
        var day = LocalDate.now()
        if (day !in loggedDates) {
            day = day.minusDays(1)
            if (day !in loggedDates) return@map 0
        }
        var streak = 0
        while (day in loggedDates) { streak++; day = day.minusDays(1) }
        streak
    }

    /** All-time record streak - see longestLogStreak's own doc comment (already generic, reused here). */
    fun longestStreak(profileId: String = "default"): Flow<Int> = observeAll(profileId).map { list ->
        longestLogStreak(list.mapTo(mutableSetOf()) { it.date })
    }

    suspend fun exportAll(profileId: String = "default"): List<MoodEntity> = dao.getAllForBackup(profileId)
    suspend fun importAll(entities: List<MoodEntity>) {
        if (entities.isEmpty()) return
        dao.insertAll(entities.map { it.copy(mood = it.mood.coerceIn(1, 5), stress = it.stress.coerceIn(1, 5)) })
    }
}

private fun MoodEntity.toDomain() = MoodEntry(
    id = id,
    date = LocalDate.parse(date),
    mood = mood,
    stress = stress,
    notes = notes,
)
