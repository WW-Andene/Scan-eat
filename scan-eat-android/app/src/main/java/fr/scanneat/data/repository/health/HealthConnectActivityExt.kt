package fr.scanneat.data.repository.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

// Activity/exercise sync — extracted verbatim out of HealthConnectRepository, the
// cohesive "mirror a logged workout to/from Health Connect" concern. Extension
// functions on HealthConnectRepository, same purely-structural split as
// HealthConnectWeightExt.kt/HealthConnectHydrationExt.kt/HealthConnectNutritionExt.kt.
// HealthConnectRepository's own public API is unchanged.

/**
 * Mirrors a logged workout as an ExerciseSessionRecord + a paired
 * TotalCaloriesBurnedRecord over the same window (Health Connect has no
 * "calories" field on the session itself — a separate overlapping record
 * is the documented way to attach an energy estimate to a session).
 * ActivityRepository only stores a date + duration, not a real logged
 * time-of-day, so [endTime] (the moment the entry was actually created,
 * i.e. ActivityEntity.loggedAt) anchors the window instead of guessing
 * one — end = when it was logged, start = end minus the logged duration.
 */
suspend fun HealthConnectRepository.writeActivity(type: ActivityType, minutes: Int, kcal: Int, endTime: Instant) {
    if (minutes <= 0) return
    try {
        if (!hasPermission(HealthConnectRepository.activityWritePermissions)) return
        val start = endTime.minusSeconds(minutes * 60L)
        val startOffset = ZoneId.systemDefault().rules.getOffset(start)
        val endOffset = ZoneId.systemDefault().rules.getOffset(endTime)
        val session = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = startOffset,
            endTime = endTime,
            endZoneOffset = endOffset,
            exerciseType = exerciseTypeFor(type),
        )
        val calories = TotalCaloriesBurnedRecord(
            startTime = start,
            startZoneOffset = startOffset,
            endTime = endTime,
            endZoneOffset = endOffset,
            energy = Energy.kilocalories(kcal.toDouble()),
        )
        client().insertRecords(listOf(session, calories))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(HealthConnectRepository.TAG, "writeActivity failed", e)
    }
}

private fun exerciseTypeFor(type: ActivityType): Int = when (type) {
    ActivityType.WALKING_BRISK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
    ActivityType.RUNNING       -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
    ActivityType.CYCLING       -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
    ActivityType.SWIMMING      -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
    ActivityType.STRENGTH      -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
    ActivityType.YOGA          -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
    ActivityType.HIIT          -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
    ActivityType.OTHER         -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
}

// Inverse of exerciseTypeFor() - anything Health Connect reports that isn't
// one of our own 8 types (it has dozens more: badminton, rowing, skiing...)
// still gets imported, just bucketed as OTHER rather than silently dropped,
// same fallback convention ActivityType.fromKey() already uses for an
// unrecognized stored key.
private fun activityTypeFor(exerciseType: Int): ActivityType = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ActivityType.WALKING_BRISK
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> ActivityType.RUNNING
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING  -> ActivityType.CYCLING
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> ActivityType.SWIMMING
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> ActivityType.STRENGTH
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> ActivityType.YOGA
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> ActivityType.HIIT
    else -> ActivityType.OTHER
}

/**
 * A workout Health Connect has from an external source (a fitness
 * tracker's own app, etc.) in the given window - excludes this app's own
 * mirrored sessions, same dataOrigin filter [readExternalWeights] uses.
 * Energy comes from whichever TotalCaloriesBurnedRecord's window most
 * overlaps the session's, since Health Connect has no direct link between
 * the two record types beyond time overlap (see [writeActivity]'s own
 * doc comment on why they're separate records in the first place).
 */
suspend fun HealthConnectRepository.readExternalActivity(start: Instant, end: Instant): List<ExternalActivitySession> {
    return try {
        if (!hasPermission(HealthConnectRepository.activityReadPermissions)) return emptyList()
        val sessions = client()
            .readRecords(ReadRecordsRequest(recordType = ExerciseSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end)))
            .records
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
        if (sessions.isEmpty()) return emptyList()
        val calorieRecords = client()
            .readRecords(ReadRecordsRequest(recordType = TotalCaloriesBurnedRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end)))
            .records
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
        sessions.map { session ->
            val overlapping = calorieRecords.filter { it.startTime < session.endTime && it.endTime > session.startTime }
            val kcal = overlapping.sumOf { it.energy.inKilocalories }.roundToInt()
            ExternalActivitySession(
                id        = session.metadata.id,
                type      = activityTypeFor(session.exerciseType),
                startTime = session.startTime,
                endTime   = session.endTime,
                kcal      = kcal,
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(HealthConnectRepository.TAG, "readExternalActivity failed", e)
        emptyList()
    }
}

data class ExternalActivitySession(
    val id: String,
    val type: ActivityType,
    val startTime: Instant,
    val endTime: Instant,
    val kcal: Int,
)
