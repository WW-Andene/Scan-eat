package fr.scanneat.data.repository.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
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
            metadata = Metadata.manualEntry(),
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
}
