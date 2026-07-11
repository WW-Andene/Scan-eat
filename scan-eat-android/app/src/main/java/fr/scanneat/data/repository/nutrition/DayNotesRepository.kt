package fr.scanneat.data.repository.nutrition

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
// Notes stay on-device. NOT currently included in backup export/import
// (BackupBundle only covers the 7 Room-backed entities — see BackupModels.kt's
// scope comment) — a real gap if this repository's data matters to a user
// restoring from backup, tracked there rather than silently claimed here.
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

    /** Observe note for a given date. Emits "" when none set. */
    fun observe(date: LocalDate): Flow<String> =
        storeData.map { prefs -> prefs[key(date)] ?: "" }

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
}
