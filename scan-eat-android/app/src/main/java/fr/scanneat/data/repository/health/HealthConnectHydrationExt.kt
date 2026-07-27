package fr.scanneat.data.repository.health

import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.units.Volume
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

// Hydration sync — extracted verbatim out of HealthConnectRepository, the cohesive
// "mirror a hydration delta to Health Connect" concern. Extension function on
// HealthConnectRepository, same purely-structural split as
// HealthConnectWeightExt.kt/HealthConnectActivityExt.kt/HealthConnectNutritionExt.kt.
// HealthConnectRepository's own public API is unchanged.

/**
 * Mirrors a single hydration delta (e.g. +1 glass, 250 mL) as its own
 * instantaneous HydrationRecord - HydrationRepository.add()/addGlass() had
 * zero Health Connect wiring before, unlike weight, so a day's water intake
 * never left this app. A tiny (1s) interval rather than a true zero-length
 * one: HydrationRecord requires startTime < endTime.
 */
suspend fun HealthConnectRepository.writeHydrationDelta(mlDelta: Int) {
    if (mlDelta <= 0) return
    try {
        if (!hasPermission(HealthConnectRepository.hydrationPermissions)) return
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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(HealthConnectRepository.TAG, "writeHydrationDelta failed", e)
    }
}
