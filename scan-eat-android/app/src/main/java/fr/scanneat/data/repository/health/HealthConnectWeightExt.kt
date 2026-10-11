package fr.scanneat.data.repository.health

import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Weight sync — extracted verbatim out of HealthConnectRepository, the cohesive
// "mirror a locally-logged weight entry to/from Health Connect" concern. Extension
// functions on HealthConnectRepository rather than a delegate class, since every
// function here needs the repository's own client()/hasPermission()/context - same
// purely-structural split ScanRepository already went through for
// ScanOffLookup/ScanServerClient. HealthConnectRepository's own public API is
// unchanged.

/**
 * Mirrors a locally-logged weight entry into Health Connect. No-ops silently if not
 * available/permitted — sync is opt-in, never a hard dependency for local logging. Also
 * no-ops (logging only) on a genuine Health Connect exception (permission revoked between
 * the check above and the write, provider uninstalled mid-session, etc.) - the local Room
 * write this mirrors has already committed by the time WeightRepository.log() calls this.
 */
suspend fun HealthConnectRepository.writeWeight(date: LocalDate, weightKg: Double) {
    try {
        if (!hasPermission(HealthConnectRepository.weightWritePermissions)) return
        val instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val record = WeightRecord(
            time = instant,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(instant),
            weight = Mass.kilograms(weightKg),
        )
        client().insertRecords(listOf(record))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(HealthConnectRepository.TAG, "writeWeight failed", e)
    }
}

/** Reads weight records Health Connect has from any source (this app or others) in the given window. */
suspend fun HealthConnectRepository.readWeights(start: Instant, end: Instant): List<WeightRecord> {
    return try {
        if (!hasPermission(HealthConnectRepository.weightReadPermissions)) return emptyList()
        client()
            .readRecords(ReadRecordsRequest(recordType = WeightRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end)))
            .records
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(HealthConnectRepository.TAG, "readWeights failed", e)
        emptyList()
    }
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
suspend fun HealthConnectRepository.readExternalWeights(start: Instant, end: Instant): List<WeightRecord> = try {
    readWeights(start, end).filter { it.metadata.dataOrigin.packageName != context.packageName }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    android.util.Log.w(HealthConnectRepository.TAG, "readExternalWeights failed", e)
    emptyList()
}

/**
 * Deletes whatever weight record(s) this app previously mirrored for [date] —
 * WeightRepository.delete() previously never called this at all, so deleting a
 * bad/duplicate entry in-app left a stale record permanently in Health Connect
 * (and any other app reading from it) with no way to remove it except manually
 * in the Health Connect app itself. insertRecords() has no stable id to target
 * directly, so this reads the day's records back and deletes them by their
 * Health Connect-assigned metadata id.
 */
suspend fun HealthConnectRepository.deleteWeight(date: LocalDate) {
    try {
        // Deleting needs write permission; the read below (to find which records
        // to delete) is gated separately inside readWeights() itself.
        if (!hasPermission(HealthConnectRepository.weightWritePermissions)) return
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val ids = readWeights(start, end).map { it.metadata.id }
        if (ids.isNotEmpty()) client().deleteRecords(WeightRecord::class, ids, emptyList())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(HealthConnectRepository.TAG, "deleteWeight failed", e)
    }
}
