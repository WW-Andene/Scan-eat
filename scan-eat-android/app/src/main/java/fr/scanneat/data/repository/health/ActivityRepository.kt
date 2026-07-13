package fr.scanneat.data.repository.health

import fr.scanneat.data.local.db.activity.ActivityDao
import fr.scanneat.data.local.db.activity.ActivityEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
 * MET × weight_kg × (minutes / 60).
 * Returns 0 for missing / invalid inputs — mirrors the JS behaviour.
 */
fun estimateKcalBurned(type: ActivityType, minutes: Int, weightKg: Double): Int {
    if (minutes <= 0 || weightKg <= 0) return 0
    return (type.met * weightKg * (minutes / 60.0)).roundToInt()
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

data class DailyBurned(val kcal: Int, val minutes: Int, val count: Int)

@Singleton
class ActivityRepository @Inject constructor(private val dao: ActivityDao) {

    fun observeByDate(date: LocalDate, profileId: String = "default"): Flow<List<ActivityEntry>> =
        dao.observeByDate(date.toString(), profileId).map { list -> list.map { it.toDomain() } }

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
                   else estimateKcalBurned(type, minutes, weightKg)
        dao.insert(ActivityEntity(
            id           = UUID.randomUUID().toString(),
            date         = date.toString(),
            type         = type.key,
            minutes      = minutes.coerceAtLeast(0),
            kcalBurned   = kcal,
            note         = note,
            loggedAt     = System.currentTimeMillis(),
            profileId    = profileId,
            subType      = subType,
            sets         = sets?.coerceAtLeast(0),
            reps         = reps?.coerceAtLeast(0),
            distanceKm   = distanceKm?.coerceAtLeast(0.0),
            weightUsedKg = weightUsedKg?.coerceAtLeast(0.0),
        ))
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Sum kcal burned for a date. */
    suspend fun dailyBurned(date: LocalDate = LocalDate.now(), profileId: String = "default"): DailyBurned {
        val entries = dao.getRange(date.toString(), date.toString(), profileId)
        return DailyBurned(
            kcal    = entries.sumOf { it.kcalBurned },
            minutes = entries.sumOf { it.minutes },
            count   = entries.size,
        )
    }

    suspend fun getRange(from: LocalDate, to: LocalDate, profileId: String = "default"): List<ActivityEntry> =
        dao.getRange(from.toString(), to.toString(), profileId).map { it.toDomain() }

    private fun ActivityEntity.toDomain() = ActivityEntry(
        id           = id,
        date         = LocalDate.parse(date),
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
