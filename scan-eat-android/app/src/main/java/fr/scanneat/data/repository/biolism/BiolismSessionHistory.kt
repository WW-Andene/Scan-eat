package fr.scanneat.data.repository.biolism

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.scanneat.domain.engine.biolism.BiolismSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Session history (last 20 sessions) — delegate used by [BiolismRepository],
 * which forwards its own `sessions`/`saveSession`/`deleteSession` members to
 * this class so external callers see no change in shape.
 */
internal class SessionHistoryStore(
    private val store: DataStore<Preferences>,
    private val storeData: Flow<Preferences>,
) {
    val sessions: Flow<List<BiolismSession>> = storeData.map { p ->
        val json = p[BiolismRepository.K_SESSIONS] ?: return@map emptyList()
        runCatching { Json.decodeFromString<List<SerializableSession>>(json) }
            .getOrElse { emptyList() }
            .map { it.toDomain() }
    }.distinctUntilChanged()

    suspend fun saveSession(session: BiolismSession) = store.edit { p ->
        val current = runCatching {
            Json.decodeFromString<List<SerializableSession>>(p[BiolismRepository.K_SESSIONS] ?: "[]")
        }.getOrElse { emptyList() }
        val updated = (current + SerializableSession.fromDomain(session)).takeLast(20)
        p[BiolismRepository.K_SESSIONS] = Json.encodeToString(updated)
    }

    suspend fun deleteSession(id: Long) = store.edit { p ->
        val current = runCatching {
            Json.decodeFromString<List<SerializableSession>>(p[BiolismRepository.K_SESSIONS] ?: "[]")
        }.getOrElse { emptyList() }
        p[BiolismRepository.K_SESSIONS] = Json.encodeToString(current.filter { it.id != id })
    }
}

@Serializable
internal data class SerializableSession(
    val id: Long, val timestamp: String, val elapsedSec: Double,
    val kcalBurned: Double, val kcalPerMin: Double,
    val bmrDay: Double, val tdeeDay: Double, val activityLabel: String,
    val ketosis: Boolean, val startWeightKg: Double, val endWeightKg: Double,
    val fatFrac: Double, val fatLostKg: Double,
    val ketoElapsedSec: Double = 0.0,
) {
    fun toDomain() = BiolismSession(
        id, timestamp, elapsedSec, kcalBurned, kcalPerMin, bmrDay, tdeeDay,
        activityLabel, ketosis, startWeightKg, endWeightKg, fatFrac, fatLostKg,
        ketoElapsedSec,
    )
    companion object {
        fun fromDomain(s: BiolismSession) = SerializableSession(
            s.id, s.timestamp, s.elapsedSec, s.kcalBurned, s.kcalPerMin,
            s.bmrDay, s.tdeeDay, s.activityLabel, s.ketosis, s.startWeightKg,
            s.endWeightKg, s.fatFrac, s.fatLostKg, s.ketoElapsedSec,
        )
    }
}
