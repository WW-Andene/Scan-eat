package fr.scanneat.data.repository.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

// ============================================================================
// HEALTH CONNECT REPOSITORY — platform health-data sync for weight.
//
// Scan'eat previously had zero integration with any platform health store —
// weight logged in-app stayed siloed here, invisible to Health Connect and
// any other app reading from it (or writing to it: a smart scale's own app,
// for instance). This wraps the minimal read/write surface for WeightRecord;
// WeightRepository calls writeWeight() after a successful local log() when
// the user has opted in and granted permission (see SettingsScreen).
//
// Health Connect ships as a separate app on API < 34 (bundled into the OS
// from API 34+), so availability must be checked before touching the client
// — HealthConnectClient.getOrCreate() throws if it isn't present.
// ============================================================================

enum class HealthConnectAvailability { AVAILABLE, NOT_INSTALLED, UNSUPPORTED }

@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val weightPermissions = setOf(
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
        )
        // Hydration is write-only (see writeHydrationDelta) - Health Connect's
        // HydrationRecord models a volume over a start/end interval, but this
        // app stores intake as a single mutable running total per day, so
        // reading external records back and merging them risks double-
        // counting on every re-read rather than being a safe idempotent
        // import the way readExternalWeights() is for WeightRepository.
        private val hydrationPermissions = setOf(HealthPermission.getWritePermission(HydrationRecord::class))
        // Activity now reads back too (see readExternalActivity) - unlike
        // hydration, ActivityEntity is a genuine multi-entry-per-day table
        // with a dedup key (externalSourceId, added alongside this), so a
        // safe idempotent import is possible the same way it already is for
        // weight. There's still no stored Health-Connect-record id to delete
        // later (ActivityRepository.delete() only ever gets the local row's
        // own id), so a deleted in-app activity still leaves its HC mirror
        // behind - that part of the original write-only limitation stands.
        //
        // Write and read are two separate subsets (not one combined set) for
        // the same reason weight/hydration/activity writes each check their
        // own subset via hasPermission() below rather than the full
        // PERMISSIONS set: a user who granted only the write half (or only
        // the read half) must not lose the other half's functionality just
        // because this pair happens to ship together.
        private val activityWritePermissions = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        )
        private val activityReadPermissions = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        )

        /** Requested together up front (single system permission dialog) - see [hasPermission] for why writes each check only their own subset instead of this combined set. */
        val PERMISSIONS: Set<String> = weightPermissions + hydrationPermissions + activityWritePermissions + activityReadPermissions
    }

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.UNSUPPORTED
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NOT_INSTALLED
        else -> HealthConnectAvailability.AVAILABLE
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    /** True only when every permission Scan'eat ever asks for is granted — used for the Settings screen's single "connected" status, not to gate individual writes (see [hasPermission] for that). */
    suspend fun hasPermissions(): Boolean = hasPermission(PERMISSIONS)

    /**
     * Checks only the specific permission(s) a given sync feature needs.
     * hasPermissions() (all-or-nothing against the full PERMISSIONS set) used
     * to gate every write path, including writeWeight()/readWeights() - so
     * adding hydration/activity's own write permissions to that same shared
     * set meant a user who had weight sync working (and never touched the
     * newer hydration/activity permissions, e.g. by revoking just one of them
     * in system settings) would silently lose weight sync too, the moment
     * PERMISSIONS stopped being a subset of what's actually granted. Each
     * write function now checks only what it personally needs.
     */
    private suspend fun hasPermission(required: Set<String>): Boolean {
        if (availability() != HealthConnectAvailability.AVAILABLE) return false
        return client().permissionController.getGrantedPermissions().containsAll(required)
    }

    /** Mirrors a locally-logged weight entry into Health Connect. No-ops silently if not available/permitted — sync is opt-in, never a hard dependency for local logging. */
    suspend fun writeWeight(date: LocalDate, weightKg: Double) {
        if (!hasPermission(weightPermissions)) return
        val instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val record = WeightRecord(
            time = instant,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(instant),
            weight = Mass.kilograms(weightKg),
        )
        client().insertRecords(listOf(record))
    }

    /** Reads weight records Health Connect has from any source (this app or others) in the given window. */
    suspend fun readWeights(start: Instant, end: Instant): List<WeightRecord> {
        if (!hasPermission(weightPermissions)) return emptyList()
        return client()
            .readRecords(ReadRecordsRequest(recordType = WeightRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end)))
            .records
    }

    /**
     * Same as [readWeights] but excludes records this app itself wrote —
     * readWeights() existed but had zero callers anywhere: sync was
     * write-only, so a smart scale (or any other app) writing into Health
     * Connect never appeared in Scan'eat's own weight history. Filtering out
     * this app's own dataOrigin is what makes importing them back safe —
     * without it, WeightRepository's own writeWeight() calls would get read
     * back as if they were new external data, in an endless feedback loop.
     */
    suspend fun readExternalWeights(start: Instant, end: Instant): List<WeightRecord> =
        readWeights(start, end).filter { it.metadata.dataOrigin.packageName != context.packageName }

    /**
     * Deletes whatever weight record(s) this app previously mirrored for [date] —
     * WeightRepository.delete() previously never called this at all, so deleting a
     * bad/duplicate entry in-app left a stale record permanently in Health Connect
     * (and any other app reading from it) with no way to remove it except manually
     * in the Health Connect app itself. insertRecords() has no stable id to target
     * directly, so this reads the day's records back and deletes them by their
     * Health Connect-assigned metadata id.
     */
    suspend fun deleteWeight(date: LocalDate) {
        if (!hasPermission(weightPermissions)) return
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val ids = readWeights(start, end).map { it.metadata.id }
        if (ids.isNotEmpty()) client().deleteRecords(WeightRecord::class, ids, emptyList())
    }

    /**
     * Mirrors a single hydration delta (e.g. +1 glass, 250 mL) as its own
     * instantaneous HydrationRecord - HydrationRepository.add()/addGlass() had
     * zero Health Connect wiring before, unlike weight, so a day's water intake
     * never left this app. A tiny (1s) interval rather than a true zero-length
     * one: HydrationRecord requires startTime < endTime.
     */
    suspend fun writeHydrationDelta(mlDelta: Int) {
        if (mlDelta <= 0) return
        if (!hasPermission(hydrationPermissions)) return
        val end = Instant.now()
        val start = end.minusSeconds(1)
        val record = HydrationRecord(
            startTime = start,
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(start),
            endTime = end,
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(end),
            volume = Volume.milliliters(mlDelta.toDouble()),
        )
        client().insertRecords(listOf(record))
    }

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
    suspend fun writeActivity(type: ActivityType, minutes: Int, kcal: Int, endTime: Instant) {
        if (minutes <= 0 || !hasPermission(activityWritePermissions)) return
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
    suspend fun readExternalActivity(start: Instant, end: Instant): List<ExternalActivitySession> {
        if (!hasPermission(activityReadPermissions)) return emptyList()
        val sessions = client()
            .readRecords(ReadRecordsRequest(recordType = ExerciseSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end)))
            .records
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
        if (sessions.isEmpty()) return emptyList()
        val calorieRecords = client()
            .readRecords(ReadRecordsRequest(recordType = TotalCaloriesBurnedRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end)))
            .records
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
        return sessions.map { session ->
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
    }
}

data class ExternalActivitySession(
    val id: String,
    val type: ActivityType,
    val startTime: Instant,
    val endTime: Instant,
    val kcal: Int,
)
