package fr.scanneat.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// HYDRATION REPOSITORY — port of public/features/hydration.js
//
// Tracks daily water intake in mL. Default glass = 250 mL.
// Goal is derived from profile weight (30 mL/kg, minimum 1500 mL).
// Storage: DataStore (one key per day, same pattern as DayNotesRepository).
// ============================================================================

const val HYD_GLASS_ML = 250
const val HYD_DEFAULT_GOAL_ML = 2000

private val Context.hydrationDataStore by preferencesDataStore(name = "hydration")

@Singleton
class HydrationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.hydrationDataStore

    private fun key(date: LocalDate) = intPreferencesKey("hyd_${date}")

    /** Observe intake for a given date in mL. Emits 0 when none. */
    fun observe(date: LocalDate): Flow<Int> =
        store.data.map { prefs -> prefs[key(date)] ?: 0 }

    /** Add (or subtract) mL for a date. Clamps to ≥ 0. */
    suspend fun add(date: LocalDate, ml: Int) {
        store.edit { prefs ->
            val current = prefs[key(date)] ?: 0
            val next = (current + ml).coerceAtLeast(0)
            prefs[key(date)] = next
        }
    }

    /** Set intake directly (for edit flows). */
    suspend fun set(date: LocalDate, ml: Int) {
        store.edit { prefs ->
            prefs[key(date)] = ml.coerceAtLeast(0)
        }
    }

    /** Convenience: +1 glass. */
    suspend fun addGlass(date: LocalDate = LocalDate.now()) = add(date, HYD_GLASS_ML)

    /** Convenience: −1 glass. */
    suspend fun removeGlass(date: LocalDate = LocalDate.now()) = add(date, -HYD_GLASS_ML)

    /**
     * Derive daily water goal from body weight.
     * 30 mL/kg, minimum 1500 mL, maximum 4000 mL.
     */
    fun goalMl(weightKg: Double?): Int {
        if (weightKg == null || weightKg <= 0) return HYD_DEFAULT_GOAL_ML
        return (weightKg * 30).toInt().coerceIn(1500, 4000)
    }
}
