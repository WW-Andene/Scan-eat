package fr.scanneat.data.repository.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

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
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            // Hydration is write-only (see writeHydrationDelta) - Health Connect's
            // HydrationRecord models a volume over a start/end interval, but this
            // app stores intake as a single mutable running total per day, so
            // reading external records back and merging them risks double-
            // counting on every re-read rather than being a safe idempotent
            // import the way readExternalWeights() is for WeightRepository.
            HealthPermission.getWritePermission(HydrationRecord::class),
        )
    }

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.UNSUPPORTED
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NOT_INSTALLED
        else -> HealthConnectAvailability.AVAILABLE
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean {
        if (availability() != HealthConnectAvailability.AVAILABLE) return false
        return client().permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }

    /** Mirrors a locally-logged weight entry into Health Connect. No-ops silently if not available/permitted — sync is opt-in, never a hard dependency for local logging. */
    suspend fun writeWeight(date: LocalDate, weightKg: Double) {
        if (!hasPermissions()) return
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
        if (!hasPermissions()) return emptyList()
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
        if (!hasPermissions()) return
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
        if (!hasPermissions()) return
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
}
