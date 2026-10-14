package fr.scanneat.data.repository

import fr.scanneat.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Roadmap C15: health history used to be trimmed silently once a table hit
 * its cap. The first time any table goes over, the deletion is held back and
 * Settings shows a warning (with a backup prompt); trimming resumes only once
 * the user has acknowledged it.
 */
@Singleton
class HistoryRetentionGuard @Inject constructor(private val prefs: UserPreferences) {
    suspend fun trim(cap: Int, count: suspend () -> Int, trim: suspend () -> Unit) {
        if (count() <= cap) return
        if (prefs.retentionTrimAcknowledged.first()) trim()
        else prefs.setRetentionWarningPending(true)
    }
}
