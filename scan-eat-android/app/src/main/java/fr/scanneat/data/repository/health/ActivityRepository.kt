package fr.scanneat.data.repository.health

import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.activity.ActivityEntity
import fr.scanneat.data.local.db.toIsoString
import fr.scanneat.data.local.db.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

// ============================================================================
// ACTIVITY REPOSITORY — port of public/data/activity.js
//
// Burned kcal estimated with the MET equation:
//   kcal = MET × weight_kg × hours
// Source: Ainsworth et al., Compendium of Physical Activities 2011.
// ============================================================================

enum class ActivityType(val key: String, val met: Double, val labelFr: String) {
    WALKING_BRISK("walking_brisk", 4.3, "Marche rapide"),
    RUNNING      ("running",       9.8, "Course à pied"),
    CYCLING      ("cycling",       7.0, "Vélo"),
    SWIMMING     ("swimming",      7.0, "Natation"),
    STRENGTH     ("strength",      5.0, "Musculation"),
    YOGA         ("yoga",          2.8, "Yoga"),
    HIIT         ("hiit",          8.0, "HIIT"),
    OTHER        ("other",         4.0, "Autre");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: OTHER
    }
}

/**
 * Sub-entries under a parent [ActivityType] - e.g. "bench_press"/"squat"/
 * "deadlift" under STRENGTH, "trail"/"sprint"/"interval" under RUNNING. Purely
 * descriptive (same MET as the parent type, no separate calorie model per
 * sub-type - that level of precision needs exercise-specific MET tables this
 * app doesn't carry yet), stored as a free-form key on ActivityEntity.subType.
 */
val ACTIVITY_SUB_TYPES: Map<ActivityType, List<String>> = mapOf(
    ActivityType.STRENGTH to listOf("bench_press", "squat", "deadlift", "biceps_curl"),
    ActivityType.SWIMMING to listOf("freestyle", "breaststroke", "butterfly"),
    ActivityType.RUNNING to listOf("trail", "sprint", "interval"),
    ActivityType.CYCLING to listOf("road", "mountain", "indoor"),
)

/**
 * MET × weight_kg × (minutes / 60), using the fixed per-type MET table.
 * Returns 0 for missing / invalid inputs — mirrors the JS behaviour.
 */
fun estimateKcalBurned(type: ActivityType, minutes: Int, weightKg: Double): Int {
    if (minutes <= 0 || weightKg <= 0) return 0
    return (type.met * weightKg * (minutes / 60.0)).roundToInt()
}

/**
 * Pace-adjusted running estimate — distanceKm/weightUsedKg were added to
 * ActivityEntry this session and shown in ActivityScreen, but only ever fed
 * a fixed per-type MET table that ignores them entirely: a 5km jog and a
 * 20km run of the same duration produced identical burn numbers. For
 * running specifically there's a well-established, verifiable metabolic
 * equation (ACSM: VO2 ml/kg/min = 3.5 + 0.2 × speed_m_per_min, MET =
 * VO2/3.5) that actually uses pace instead of a single flat MET value.
 * Left to the fixed-MET fallback for every other type/when distance is
 * missing, rather than guessing at a similarly precise formula for
 * strength training's weightUsedKg — no equivalent well-established
 * equation exists for external resistance the way it does for running
 * pace, and fabricating one would be worse than not adjusting at all.
 */
fun estimateKcalBurnedWithDistance(type: ActivityType, minutes: Int, weightKg: Double, distanceKm: Double?): Int {
    if (minutes <= 0 || weightKg <= 0) return 0
    if (type == ActivityType.RUNNING && distanceKm != null && distanceKm > 0) {
        val speedMPerMin = distanceKm * 1000.0 / minutes
        // ACSM running equation is validated for speeds >= 134 m/min (~8 km/h);
        // below that it under-predicts badly (that's walking-pace territory), so
        // it's only applied in the range it was actually derived for.
        if (speedMPerMin >= 134.0) {
            val vo2 = 3.5 + 0.2 * speedMPerMin
            val met = vo2 / 3.5
            return (met * weightKg * (minutes / 60.0)).roundToInt()
        }
    }
    return estimateKcalBurned(type, minutes, weightKg)
}

data class ActivityEntry(
    val id: String,
    val date: LocalDate,
    val type: ActivityType,
    val minutes: Int,
    val kcalBurned: Int,
    val note: String = "",
    val subType: String? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val distanceKm: Double? = null,
    val weightUsedKg: Double? = null,
)

@Singleton
class ActivityRepository @Inject constructor(
    private val dao: ActivityDao,
    private val healthConnect: HealthConnectRepository,
) {

    fun observeByDate(date: LocalDate, profileId: String = "default"): Flow<List<ActivityEntry>> =
        dao.observeByDate(date.toIsoString(), profileId).map { list -> list.map { it.toDomain() } }

    suspend fun log(
        type: ActivityType,
        minutes: Int,
        weightKg: Double,
        kcalOverride: Int? = null,
        note: String = "",
        date: LocalDate = LocalDate.now(),
        profileId: String = "default",
        subType: String? = null,
        sets: Int? = null,
        reps: Int? = null,
        distanceKm: Double? = null,
        weightUsedKg: Double? = null,
    ) {
        val kcal = if (kcalOverride != null && kcalOverride > 0) kcalOverride
                   else estimateKcalBurnedWithDistance(type, minutes, weightKg, distanceKm)
        val loggedAt = System.currentTimeMillis()
        dao.insert(ActivityEntity(
            id           = UUID.randomUUID().toString(),
            date         = date.toIsoString(),
            type         = type.key,
            minutes      = minutes.coerceAtLeast(0),
            kcalBurned   = kcal,
            note         = note,
            loggedAt     = loggedAt,
            profileId    = profileId,
            subType      = subType,
            sets         = sets?.coerceAtLeast(0),
            reps         = reps?.coerceAtLeast(0),
            distanceKm   = distanceKm?.coerceAtLeast(0.0),
            weightUsedKg = weightUsedKg?.coerceAtLeast(0.0),
        ))
        // Health Connect had zero Activité wiring at all before this - a logged
        // workout stayed invisible to it and any other app reading from it,
        // unlike weight/hydration which already mirror.
        healthConnect.writeActivity(type, minutes.coerceAtLeast(0), kcal, Instant.ofEpochMilli(loggedAt))
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun getRange(from: LocalDate, to: LocalDate, profileId: String = "default"): List<ActivityEntry> =
        dao.getRange(from.toIsoString(), to.toIsoString(), profileId).map { it.toDomain() }

    /**
     * Pulls in workouts Health Connect has from an *external* source (a
     * fitness tracker's own app, etc.) that aren't already imported - sync
     * was previously write-only for Activité (unlike weight, which already
     * reads back), so a tracker writing straight into Health Connect never
     * reached Scan'eat's own history. Dedup is by Health Connect's own
     * record id (ActivityEntity.externalSourceId) rather than a per-day
     * convention like WeightRepository's: activity genuinely has multiple
     * entries per day, so "already have one for this day" isn't a safe
     * dedup signal here the way it is for weight. Inserted directly via the
     * DAO (not log()) so an imported session is never re-mirrored back out
     * to Health Connect as a brand-new, differently-id'd one.
     */
    suspend fun syncFromHealthConnect(profileId: String = "default", days: Int = 30) {
        val zone = java.time.ZoneId.systemDefault()
        val start = LocalDate.now().minusDays(days.toLong()).atStartOfDay(zone).toInstant()
        val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant()
        val external = healthConnect.readExternalActivity(start, end)
        if (external.isEmpty()) return
        val alreadyImported = dao.getImportedExternalIds(profileId).toSet()
        for (session in external) {
            if (session.id in alreadyImported) continue
            val minutes = java.time.Duration.between(session.startTime, session.endTime).toMinutes().toInt().coerceAtLeast(1)
            dao.insert(ActivityEntity(
                id               = UUID.randomUUID().toString(),
                date             = session.endTime.atZone(zone).toLocalDate().toIsoString(),
                type             = session.type.key,
                minutes          = minutes,
                kcalBurned       = session.kcal,
                loggedAt         = session.endTime.toEpochMilli(),
                profileId        = profileId,
                externalSourceId = session.id,
            ))
        }
    }

    private fun ActivityEntity.toDomain() = ActivityEntry(
        id           = id,
        date         = date.toLocalDate(),
        type         = ActivityType.fromKey(type),
        minutes      = minutes,
        kcalBurned   = kcalBurned,
        note         = note,
        subType      = subType,
        sets         = sets,
        reps         = reps,
        distanceKm   = distanceKm,
        weightUsedKg = weightUsedKg,
    )
}
