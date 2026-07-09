package fr.scanneat.data.repository.health

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// FASTING REPOSITORY — port of public/features/fasting.js
//
// Manages intermittent-fasting timer state.
// Active fast: startMs + targetHours stored in DataStore.
// History: list of completed fasts (date, duration, target).
// ============================================================================

private val Context.fastingDataStore by preferencesDataStore(name = "fasting")

private val KEY_START_MS     = longPreferencesKey("fasting_start_ms")
private val KEY_TARGET_HOURS = intPreferencesKey("fasting_target_hours")
private val KEY_HISTORY_JSON = stringPreferencesKey("fasting_history")

data class FastingState(
    val startMs: Long,
    val targetHours: Int,
) {
    // Clamped at 0: a backward clock change (manual set, NTP correction) would otherwise
    // make this negative, which made the active-fast UI disappear entirely and let the
    // user silently overwrite startMs by tapping "Start" again on what looked like an
    // idle screen — the fast is still running, so it should never be hidden, just show 0.
    val elapsedMs: Long get() = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
    val elapsedHours: Double get() = elapsedMs / 3_600_000.0
    val isActive: Boolean get() = elapsedHours in 0.0..targetHours.toDouble()
    val progressFraction: Float get() = (elapsedHours / targetHours).toFloat().coerceIn(0f, 1.2f)
    val targetMs: Long get() = startMs + targetHours * 3_600_000L
}

data class FastCompletion(
    val date: String,           // YYYY-MM-DD of the end
    val startMs: Long,
    val endMs: Long,
    val targetHours: Int,
    val achievedHours: Double,
    val reached: Boolean,       // achieved >= target
)

@Singleton
class FastingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.fastingDataStore

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    // ---- Current fast state ----

    val state: Flow<FastingState?> = storeData.map { prefs ->
        val start  = prefs[KEY_START_MS] ?: return@map null
        val target = prefs[KEY_TARGET_HOURS] ?: 16
        FastingState(start, target)
    }

    val isActive: Flow<Boolean> = state.map { it?.isActive == true }

    suspend fun start(targetHours: Int = 16) {
        store.edit { prefs ->
            prefs[KEY_START_MS]     = System.currentTimeMillis()
            prefs[KEY_TARGET_HOURS] = targetHours.coerceIn(1, 72)
        }
    }

    /** Stop fasting — persists a completion record to history. */
    suspend fun stop() {
        val now = System.currentTimeMillis()
        store.edit { prefs ->
            val start  = prefs[KEY_START_MS] ?: return@edit
            val target = prefs[KEY_TARGET_HOURS] ?: 16
            val achieved = (now - start) / 3_600_000.0

            val completion = FastCompletion(
                date          = LocalDate.now().toString(),
                startMs       = start,
                endMs         = now,
                targetHours   = target,
                achievedHours = Math.round(achieved * 10.0) / 10.0,
                reached       = achieved >= target,
            )
            val history = loadHistory(prefs).toMutableList()
            history.add(0, completion)
            prefs[KEY_HISTORY_JSON] = serializeHistory(history.take(90)) // keep 90 entries max

            prefs.remove(KEY_START_MS)
            prefs.remove(KEY_TARGET_HOURS)
        }
    }

    suspend fun cancel() {
        store.edit { prefs ->
            prefs.remove(KEY_START_MS)
            prefs.remove(KEY_TARGET_HOURS)
        }
    }

    // ---- History ----

    val history: Flow<List<FastCompletion>> = storeData.map { prefs ->
        loadHistory(prefs)
    }

    /** Current streak: consecutive days with at least one completed fast reaching target. */
    val streak: Flow<Int> = history.map { list ->
        if (list.isEmpty()) return@map 0
        val doneDates = list.filter { it.reached }.map { it.date }.toSortedSet().reversed()
        var streak = 0
        var expected = LocalDate.now().toString()
        for (date in doneDates) {
            if (date == expected) {
                streak++
                expected = LocalDate.parse(date).minusDays(1).toString()
            } else break
        }
        streak
    }

    suspend fun clearHistory() {
        store.edit { prefs -> prefs.remove(KEY_HISTORY_JSON) }
    }

    // ---- Serialization (lightweight, no Moshi dep in DataStore layer) ----

    private fun loadHistory(prefs: Preferences): List<FastCompletion> {
        val raw = prefs[KEY_HISTORY_JSON] ?: return emptyList()
        return raw.split("|").mapNotNull { parseEntry(it) }
    }

    private fun serializeHistory(list: List<FastCompletion>): String =
        list.joinToString("|") { "${it.date},${it.startMs},${it.endMs},${it.targetHours},${it.achievedHours},${it.reached}" }

    private fun parseEntry(s: String): FastCompletion? = runCatching {
        val p = s.split(",")
        FastCompletion(p[0], p[1].toLong(), p[2].toLong(), p[3].toInt(), p[4].toDouble(), p[5].toBoolean())
    }.getOrNull()
}
