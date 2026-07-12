package fr.scanneat.data.repository.nutrition

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// DAY NOTES REPOSITORY — port of public/features/day-notes.js
//
// Per-day free-text notes: mood, training, cycle phase, medication, etc.
// Cap: 500 chars per note (same as original).
// Storage: DataStore (replaces localStorage on Android).
// Notes stay on-device; included in backup export/import since format v2
// (see BackupModels.kt / BackupRepository.kt).
// ============================================================================

const val DAY_NOTE_MAX_CHARS = 500

private val Context.notesDataStore by preferencesDataStore(name = "day_notes")

@Singleton
class DayNotesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.notesDataStore

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private fun key(date: LocalDate) = stringPreferencesKey("note_${date}")

    /** Observe note for a given date. Emits "" when none set.
     * distinctUntilChanged - Preferences DataStore re-emits its whole blob on
     * every edit() call for ANY date, not just this one; without dedup every
     * unrelated note write (a different day, a backup import) would re-fire
     * this flow with an unchanged value. */
    fun observe(date: LocalDate): Flow<String> =
        storeData.map { prefs -> prefs[key(date)] ?: "" }.distinctUntilChanged()

    /** Set or clear a note. Truncates to DAY_NOTE_MAX_CHARS. */
    suspend fun set(date: LocalDate, text: String) {
        val trimmed = text.take(DAY_NOTE_MAX_CHARS)
        store.edit { prefs ->
            if (trimmed.isEmpty()) prefs.remove(key(date))
            else prefs[key(date)] = trimmed
        }
    }

    /** List all dates that have a note, sorted ascending. */
    suspend fun listDates(): List<LocalDate> {
        val prefs = storeData.first()
        return prefs.asMap().keys
            .filter { it.name.startsWith("note_") }
            .mapNotNull { runCatching { LocalDate.parse(it.name.removePrefix("note_")) }.getOrNull() }
            .sorted()
    }

    // ---- Backup export/import ----

    /** All (date, text) notes currently stored, for BackupRepository. */
    suspend fun exportAll(): List<Pair<LocalDate, String>> {
        val prefs = storeData.first()
        return prefs.asMap().entries.mapNotNull { (pref, value) ->
            if (!pref.name.startsWith("note_")) return@mapNotNull null
            val date = runCatching { LocalDate.parse(pref.name.removePrefix("note_")) }.getOrNull() ?: return@mapNotNull null
            val text = value as? String ?: return@mapNotNull null
            date to text
        }
    }

    /** Restores notes from a backup — overwrites any existing note for the same date. */
    suspend fun importAll(entries: List<Pair<LocalDate, String>>) {
        if (entries.isEmpty()) return
        store.edit { prefs -> entries.forEach { (date, text) -> prefs[key(date)] = text.take(DAY_NOTE_MAX_CHARS) } }
    }
}
