package fr.scanneat.data.repository.health

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.domain.model.roundTo1Decimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.io.IOException
import java.util.UUID
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

// R&D audit finding: fasting was a DataStore singleton with no profileId
// concept at all, unlike every Room-backed tracker. "default" keeps the
// exact same key names pre-existing installs already use (zero migration);
// any other profile gets its own namespaced keys.
private fun keyStartMs(profileId: String) =
    longPreferencesKey(if (profileId == "default") "fasting_start_ms" else "fasting_${profileId}_start_ms")
private fun keyTargetHours(profileId: String) =
    intPreferencesKey(if (profileId == "default") "fasting_target_hours" else "fasting_${profileId}_target_hours")
private fun keyHistoryJson(profileId: String) =
    stringPreferencesKey(if (profileId == "default") "fasting_history" else "fasting_${profileId}_history")

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
    // Matches progressFraction's 1.2x grace window - a fast running past its
    // target hour count should still read as "in progress, overshooting" until
    // the user actually taps stop, not silently hide the running-fast UI.
    val isActive: Boolean get() = elapsedHours in 0.0..(targetHours * 1.2)
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
    // Was startMs doubling as a de-facto unique key ("two fasts can't start in
    // the same millisecond" - true in practice, but a real id is one less thing
    // to reason about as more features build on this history, e.g. edit-in-place).
    // A real UUID assigned at completion time. Entries serialized before this
    // field existed have none in storage - parseEntry() below synthesizes one
    // from startMs for those (same fallback identity as before), so old history
    // rows keep working without a migration.
    val id: String = UUID.randomUUID().toString(),
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

    fun state(profileId: String = "default"): Flow<FastingState?> = storeData.map { prefs ->
        val start  = prefs[keyStartMs(profileId)] ?: return@map null
        val target = prefs[keyTargetHours(profileId)] ?: 16
        FastingState(start, target)
    }.distinctUntilChanged()

    fun isActive(profileId: String = "default"): Flow<Boolean> = state(profileId).map { it?.isActive == true }

    suspend fun start(targetHours: Int = 16, profileId: String = "default") {
        store.edit { prefs ->
            prefs[keyStartMs(profileId)]     = System.currentTimeMillis()
            prefs[keyTargetHours(profileId)] = targetHours.coerceIn(1, 72)
        }
    }

    /** Stop fasting — persists a completion record to history. */
    suspend fun stop(profileId: String = "default") {
        val now = System.currentTimeMillis()
        store.edit { prefs ->
            val start  = prefs[keyStartMs(profileId)] ?: return@edit
            val target = prefs[keyTargetHours(profileId)] ?: 16
            val achieved = (now - start) / 3_600_000.0

            val completion = FastCompletion(
                date          = LocalDate.now().toString(),
                startMs       = start,
                endMs         = now,
                targetHours   = target,
                achievedHours = achieved.roundTo1Decimal(),
                reached       = achieved >= target,
            )
            val history = loadHistory(prefs, profileId).toMutableList()
            history.add(0, completion)
            prefs[keyHistoryJson(profileId)] = serializeHistory(history.take(90)) // keep 90 entries max

            prefs.remove(keyStartMs(profileId))
            prefs.remove(keyTargetHours(profileId))
        }
    }

    suspend fun cancel(profileId: String = "default") {
        store.edit { prefs ->
            prefs.remove(keyStartMs(profileId))
            prefs.remove(keyTargetHours(profileId))
        }
    }

    // ---- History ----

    fun history(profileId: String = "default"): Flow<List<FastCompletion>> = storeData.map { prefs ->
        loadHistory(prefs, profileId)
    }.distinctUntilChanged()

    /**
     * Current streak: consecutive days with at least one completed fast reaching
     * target. Same 1-day grace as DashboardAggregator.logStreakDays — a fast
     * still in progress today shouldn't zero out the streak before the day is
     * even over, so the walk starts from yesterday when today has no completion yet.
     */
    fun streak(profileId: String = "default"): Flow<Int> = history(profileId).map { list ->
        if (list.isEmpty()) return@map 0
        // toSortedSet(reverseOrder()) instead of toSortedSet().reversed() - SortedSet#reversed()
        // is only available from API 35, but minSdk here is 26.
        val doneDates = list.filter { it.reached }.map { it.date }.toSortedSet(reverseOrder())
        var expected = LocalDate.now().toString()
        if (expected !in doneDates) expected = LocalDate.now().minusDays(1).toString()
        var streak = 0
        for (date in doneDates) {
            if (date == expected) {
                streak++
                expected = LocalDate.parse(date).minusDays(1).toString()
            } else break
        }
        streak
    }

    suspend fun clearHistory(profileId: String = "default") {
        store.edit { prefs -> prefs.remove(keyHistoryJson(profileId)) }
    }

    /**
     * Removes a single history entry - previously the only way to fix a mis-tapped
     * "Finish" (wrong hours logged) was clearHistory(), which wipes all 90 entries
     * and permanently zeroes the streak, unlike Weight/Activity/Medication which all
     * support editing/deleting a single entry. Matched by [FastCompletion.id] now
     * (see that field's own doc comment for the startMs-as-id history this replaces).
     */
    suspend fun deleteEntry(id: String, profileId: String = "default") {
        store.edit { prefs ->
            val history = loadHistory(prefs, profileId).filterNot { it.id == id }
            prefs[keyHistoryJson(profileId)] = serializeHistory(history)
        }
    }

    // ---- Backup export/import ----

    /** Current active session (if any) plus history for the default profile, for BackupRepository. */
    suspend fun exportForBackup(): Triple<Long?, Int?, List<FastCompletion>> {
        val prefs = storeData.first()
        val activeStart  = prefs[keyStartMs("default")]
        val activeTarget = prefs[keyTargetHours("default")]
        return Triple(activeStart, activeTarget, loadHistory(prefs, "default"))
    }

    /** Restores an active session and/or history from a backup, into the default profile. */
    suspend fun importForBackup(activeStartMs: Long?, activeTargetHours: Int?, history: List<FastCompletion>) {
        store.edit { prefs ->
            if (activeStartMs != null && activeTargetHours != null) {
                prefs[keyStartMs("default")] = activeStartMs
                prefs[keyTargetHours("default")] = activeTargetHours
            }
            if (history.isNotEmpty()) {
                prefs[keyHistoryJson("default")] = serializeHistory(history.take(90))
            }
        }
    }

    // ---- Serialization (lightweight, no Moshi dep in DataStore layer) ----

    private fun loadHistory(prefs: Preferences, profileId: String): List<FastCompletion> {
        val raw = prefs[keyHistoryJson(profileId)] ?: return emptyList()
        return raw.split("|").mapNotNull { parseEntry(it) }
    }

    private fun serializeHistory(list: List<FastCompletion>): String =
        list.joinToString("|") { "${it.date},${it.startMs},${it.endMs},${it.targetHours},${it.achievedHours},${it.reached},${it.id}" }

    private fun parseEntry(s: String): FastCompletion? = runCatching {
        val p = s.split(",")
        // A row written before FastCompletion.id existed has no 7th field -
        // fall back to startMs.toString() as its id, matching what deleteEntry()
        // effectively keyed on before this field existed (startMs was unique
        // in practice), so old history rows keep a stable identity across parses.
        val id = p.getOrNull(6) ?: p[1]
        FastCompletion(p[0], p[1].toLong(), p[2].toLong(), p[3].toInt(), p[4].toDouble(), p[5].toBoolean(), id)
    }.onFailure {
        // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository -
        // a corrupted delimited entry here previously vanished from the fasting
        // history/streak calculation with zero trace. Logs the field count, not
        // the raw entry itself - every sibling repository's equivalent parse-
        // failure log (ConsumptionRepository/WeightRepository/MedicationRepository)
        // logs only a row id, never the row's actual content.
        android.util.Log.w("FastingRepository", "Failed to parse fast completion entry (${s.split(",").size} fields)", it)
    }.getOrNull()
}
