package fr.scanneat.data.repository.health

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.domain.engine.biolism.ACTIVITY_LEVELS
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.BiolismSex
import fr.scanneat.domain.engine.biolism.computeWaterNeedL
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// HYDRATION REPOSITORY — port of public/features/hydration.js
//
// Tracks daily water intake in mL. Default glass = 250 mL.
// Goal is derived from BiolismEngine.computeWaterNeedL (EFSA 2010 gender
// baseline + activity bonus), matching the Biolism engine's own formula.
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

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    private fun key(date: LocalDate) = intPreferencesKey("hyd_${date}")

    /** Observe intake for a given date in mL. Emits 0 when none. */
    fun observe(date: LocalDate): Flow<Int> =
        storeData.map { prefs -> prefs[key(date)] ?: 0 }

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
     * Derive daily water goal from sex + activity level via BiolismEngine's
     * EFSA-based formula (2.5L male / 2.0L female + 0.5L activity bonus).
     */
    fun goalMl(sex: Sex, activityLevel: ActivityLevel): Int {
        if (sex == Sex.NOT_SPECIFIED) return HYD_DEFAULT_GOAL_ML
        val biolismSex = when (sex) {
            Sex.MALE -> BiolismSex.MALE
            Sex.FEMALE -> BiolismSex.FEMALE
            Sex.NOT_SPECIFIED -> BiolismSex.NOT_SPECIFIED
        }
        val activityId = when (activityLevel) {
            ActivityLevel.SEDENTARY -> "sedentary"
            ActivityLevel.LIGHTLY_ACTIVE -> "light"
            ActivityLevel.MODERATELY_ACTIVE -> "moderate"
            ActivityLevel.VERY_ACTIVE -> "very"
            ActivityLevel.EXTRA_ACTIVE -> "extra"
        }
        val mult = ACTIVITY_LEVELS.find { it.id == activityId }?.mult ?: 1.55
        val waterNeedL = BiolismEngine.computeWaterNeedL(biolismSex, mult)
        return (waterNeedL * 1000).toInt()
    }
}
