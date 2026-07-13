package fr.scanneat.data.repository.health

import fr.scanneat.data.local.db.weight.WeightDao
import fr.scanneat.data.local.db.weight.WeightEntity
import fr.scanneat.domain.model.roundTo1Decimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// WEIGHT REPOSITORY — port of public/data/weight-log.js
// ============================================================================

data class WeightEntry(
    val id: String,
    val date: LocalDate,
    val weightKg: Double,
    val notes: String = "",
)

data class WeightSummary(
    val latestKg: Double,
    val latestDate: LocalDate,
    val deltaKg: Double,         // vs. first entry in window
    val minKg: Double,
    val maxKg: Double,
    val count: Int,
    val recentCount: Int,
    val daysWindow: Int,
    val trendKgPerWeek: Double,  // linear regression
)

@Singleton
class WeightRepository @Inject constructor(
    private val dao: WeightDao,
    private val healthConnect: HealthConnectRepository,
) {

    fun observeAll(profileId: String = "default"): Flow<List<WeightEntry>> =
        dao.observeAll(profileId).map { list -> list.map { it.toDomain() } }

    /**
     * Upsert — one entry per day, newest write wins (mirrors weight-log.js).
     * Also mirrors to Health Connect when the user has granted sync permission
     * (writeWeight() no-ops silently otherwise — sync is opt-in, never a hard
     * dependency for the local log to succeed).
     *
     * Health Connect's insertRecords() always creates a new record — unlike
     * the local upsert above, it has no update-in-place here (a true fix
     * needs Metadata's clientRecordId, whose factory-method shape has
     * already broken once across this pinned alpha's versions — see
     * gradle/libs.versions.toml's health-connect comment — so it isn't
     * safe to guess blind). Skipping the mirror when the value hasn't
     * actually changed at least stops the common case (re-opening/resaving
     * the same day) from spamming a fresh duplicate record every time.
     */
    suspend fun log(date: LocalDate, weightKg: Double, notes: String = "", profileId: String = "default") {
        require(weightKg > 0 && weightKg <= 400) { "Invalid weight: $weightKg" }
        val existing = dao.findByDate(date.toString(), profileId)
        val rounded = weightKg.roundTo1Decimal()
        dao.upsert(WeightEntity(
            id        = existing?.id ?: UUID.randomUUID().toString(),
            date      = date.toString(),
            weightKg  = rounded,
            notes     = notes,
            loggedAt  = System.currentTimeMillis(),
            profileId = profileId,
        ))
        if (existing?.weightKg != rounded) healthConnect.writeWeight(date, rounded)
    }

    /**
     * Previously never touched Health Connect at all — log() mirrors a write
     * there, but deleting a bad/duplicate entry in-app (including via the
     * Undo-snackbar flow in WeightScreen) left a permanently stale record in
     * Health Connect and any other app reading from it. Looks the entry up
     * first purely to get its date, since Health Connect has no stable id to
     * target directly from the local Room id.
     */
    suspend fun delete(id: String) {
        val entry = dao.findById(id)
        dao.delete(id)
        entry?.let { runCatching { healthConnect.deleteWeight(LocalDate.parse(it.date)) } }
    }

    /**
     * Pulls in weight records Health Connect has from an *external* source
     * (a smart scale's own app, etc.) that aren't already logged here —
     * sync was previously write-only, so a scale writing straight into
     * Health Connect never actually reached Scan'eat's own history. Only
     * fills genuinely empty days rather than overwriting an existing local
     * entry for that date, so it can never silently clobber a manual
     * correction the user already made in-app for that day. No-ops
     * entirely if Health Connect isn't available/permitted.
     */
    suspend fun syncFromHealthConnect(profileId: String = "default", days: Int = 90) {
        val zone = java.time.ZoneId.systemDefault()
        val start = LocalDate.now().minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant()
        val external = healthConnect.readExternalWeights(start, end)
        if (external.isEmpty()) return
        val existingDates = dao.getRecent(Int.MAX_VALUE, profileId).map { it.date }.toSet()
        // Health Connect can hold more than one reading per day (e.g. a scale
        // logging every weigh-in) — the most recent one per day wins, same
        // "one entry per day" convention log() already uses.
        val byDate = external.groupBy { LocalDate.ofInstant(it.time, zone) }
        for ((date, records) in byDate) {
            if (date.toString() in existingDates) continue
            val latest = records.maxByOrNull { it.time } ?: continue
            dao.upsert(WeightEntity(
                id        = UUID.randomUUID().toString(),
                date      = date.toString(),
                weightKg  = latest.weight.inKilograms.roundTo1Decimal(),
                notes     = "",
                loggedAt  = System.currentTimeMillis(),
                profileId = profileId,
            ))
        }
    }

    /** Reactive summary — recomputes whenever any weight entry changes. */
    fun observeSummary(days: Int = 30, profileId: String = "default"): Flow<WeightSummary?> =
        observeAll(profileId).map { _ -> summarize(days, profileId) }

    suspend fun summarize(days: Int = 30, profileId: String = "default"): WeightSummary? {
        val all = dao.getRecent(Int.MAX_VALUE, profileId)
            .map { it.toDomain() }
            .sortedBy { it.date }
        if (all.isEmpty()) return null

        val latest  = all.last()
        val cutoff  = LocalDate.now().minusDays(days.toLong())
        val recent  = all.filter { !it.date.isBefore(cutoff) }
        val window  = recent.ifEmpty { listOf(all.first()) }
        val first   = window.first()
        val delta   = (latest.weightKg - first.weightKg).roundTo1Decimal()

        return WeightSummary(
            latestKg       = latest.weightKg,
            latestDate     = latest.date,
            deltaKg        = delta,
            minKg          = window.minOf { it.weightKg },
            maxKg          = window.maxOf { it.weightKg },
            count          = all.size,
            recentCount    = recent.size,
            daysWindow     = days,
            trendKgPerWeek = weeklyTrend(all),
        )
    }

    // ---- Pure math — port of weeklyTrend() from weight-log.js ----

    private fun weeklyTrend(entries: List<WeightEntry>): Double {
        if (entries.size < 2) return 0.0
        val xs = entries.map { it.date.toEpochDay().toDouble() }
        val ys = entries.map { it.weightKg }
        val x0 = xs.first()
        val xn = xs.map { it - x0 }
        val meanX = xn.average()
        val meanY = ys.average()
        var num = 0.0; var den = 0.0
        for (i in xn.indices) {
            num += (xn[i] - meanX) * (ys[i] - meanY)
            den += (xn[i] - meanX) * (xn[i] - meanX)
        }
        if (den == 0.0) return 0.0
        return ((num / den) * 7.0).roundTo1Decimal()
    }

    private fun WeightEntity.toDomain() = WeightEntry(id, LocalDate.parse(date), weightKg, notes)
}
