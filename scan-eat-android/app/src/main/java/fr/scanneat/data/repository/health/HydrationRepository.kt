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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val healthConnect: HealthConnectRepository,
) {
    private val store = context.hydrationDataStore

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    // R&D audit finding: hydration was a DataStore singleton with no profileId
    // concept at all, unlike every Room-backed tracker. "default" keeps the
    // exact same key shape pre-existing installs already use (zero migration);
    // any other profile gets its own namespaced key so its entries can never
    // collide with (or be read/pruned as) another profile's.
    private fun keyPrefix(profileId: String) = if (profileId == "default") "hyd_" else "hydp_${profileId}_"
    private fun key(date: LocalDate, profileId: String = "default") = intPreferencesKey("${keyPrefix(profileId)}${date}")

    private val KEY_PREFIX = "hyd_"
    private val PRUNE_KEEP_DAYS = 90L

    // Weight already has an explicit user-set goalWeightKg driving its own goal
    // line - Hydration's goal was purely formula-derived (sex/activity/health
    // conditions) with no way to override it, e.g. for a doctor-recommended
    // target that doesn't match the EFSA formula.
    private fun customGoalKey(profileId: String) =
        intPreferencesKey(if (profileId == "default") "hyd_custom_goal_ml" else "hydp_${profileId}_custom_goal_ml")

    /** User-set override for the formula-derived goal, if any. Null means "use the formula". */
    fun customGoalMl(profileId: String = "default"): Flow<Int?> = storeData.map { it[customGoalKey(profileId)] }.distinctUntilChanged()

    /** Sets (or clears, when [ml] is null) the custom goal override. */
    suspend fun setCustomGoalMl(ml: Int?, profileId: String = "default") {
        val key = customGoalKey(profileId)
        store.edit { prefs -> if (ml == null) prefs.remove(key) else prefs[key] = ml.coerceAtLeast(1) }
    }

    /**
     * Drop keys older than [PRUNE_KEEP_DAYS] — every day of use otherwise adds a
     * permanent "hyd_<date>" key that's never removed, and DataStore loads the
     * whole preferences file into memory on first access, so years of use means
     * thousands of stale keys parsed on every app start.
     */
    private fun prune(prefs: MutablePreferences, profileId: String) {
        val cutoff = LocalDate.now().minusDays(PRUNE_KEEP_DAYS)
        val prefix = keyPrefix(profileId)
        // Materialize before mutating — removing from prefs while iterating its
        // own live key view risks a ConcurrentModificationException.
        val staleKeys = prefs.asMap().keys.filter { pref ->
            pref.name.startsWith(prefix) &&
                runCatching { LocalDate.parse(pref.name.removePrefix(prefix)) }.getOrNull()?.isBefore(cutoff) == true
        }
        for (pref in staleKeys) prefs.remove(pref)
    }

    /** Observe intake for a given date in mL. Emits 0 when none. */
    fun observe(date: LocalDate, profileId: String = "default"): Flow<Int> =
        storeData.map { prefs -> prefs[key(date, profileId)] ?: 0 }.distinctUntilChanged()

    /**
     * Add (or subtract) mL for a date. Clamps to ≥ 0.
     * A positive delta also mirrors into Health Connect (see writeHydrationDelta) -
     * previously hydration had zero Health Connect wiring at all, unlike weight,
     * so logged water intake never left this app. Only the positive delta is
     * mirrored (an "undo"/subtract has no well-defined prior record to shrink),
     * and only for today's date - HydrationRecord models a real point in time,
     * so mirroring a past-dated correction as "now" would misrepresent when it
     * was actually drunk.
     */
    suspend fun add(date: LocalDate, ml: Int, profileId: String = "default") {
        store.edit { prefs ->
            val current = prefs[key(date, profileId)] ?: 0
            val next = (current + ml).coerceAtLeast(0)
            prefs[key(date, profileId)] = next
            prune(prefs, profileId)
        }
        if (ml > 0 && date == LocalDate.now()) healthConnect.writeHydrationDelta(ml)
    }

    /** Set intake directly (for edit flows). */
    suspend fun set(date: LocalDate, ml: Int, profileId: String = "default") {
        store.edit { prefs ->
            prefs[key(date, profileId)] = ml.coerceAtLeast(0)
            prune(prefs, profileId)
        }
    }

    /**
     * Pulls in water logged externally (a smart bottle's own app, etc.) that
     * Health Connect has for [date] - Hydration was previously write-only
     * (R&D audit finding), unlike Weight/Activity which already read back.
     * Merges via max(local, external) rather than adding, since this app
     * stores one mutable running total per day, not individual entries - see
     * readExternalHydrationTotalMl's own doc comment on why that's safely
     * idempotent instead of double-counting on repeat syncs.
     */
    suspend fun syncFromHealthConnect(date: LocalDate = LocalDate.now(), profileId: String = "default") {
        val external = healthConnect.readExternalHydrationTotalMl(date)
        if (external <= 0) return
        val current = observe(date, profileId).first()
        if (external > current) set(date, external, profileId)
    }

    /** Convenience: +1 glass. */
    suspend fun addGlass(date: LocalDate = LocalDate.now(), profileId: String = "default") = add(date, HYD_GLASS_ML, profileId)

    /** Convenience: −1 glass. */
    suspend fun removeGlass(date: LocalDate = LocalDate.now(), profileId: String = "default") = add(date, -HYD_GLASS_ML, profileId)

    /**
     * Derive daily water goal from sex + activity level via BiolismEngine's
     * EFSA-based formula (2.5L male / 2.0L female + 0.5L activity bonus).
     *
     * [exerciseMinutesToday] (Activity tab R&D improvement): previously this
     * formula only ever looked at the profile's *declared* ActivityLevel (a
     * static PAL setting), never at what was actually logged today - a
     * sedentary-profile user who happened to run 10km still got the flat
     * sedentary goal. ACE (American Council on Exercise) fluid-replacement
     * guidance recommends an extra ~500-700 mL per hour of exercise on top
     * of baseline needs; 600 mL/hour (10 mL/min) is used here as the
     * mid-range value, capped at 1500 mL (~2.5h) since very long sessions
     * need active fueling/electrolyte strategy beyond a simple per-minute
     * water bonus, not a linearly larger one.
     */
    fun goalMl(
        sex: Sex, activityLevel: ActivityLevel, healthConditions: Set<String> = emptySet(),
        exerciseMinutesToday: Int = 0, weightKg: Double? = null,
    ): Int {
        val exerciseBonusMl = (exerciseMinutesToday.coerceAtLeast(0) * 10).coerceAtMost(1500)
        if (sex == Sex.NOT_SPECIFIED) return HYD_DEFAULT_GOAL_ML + exerciseBonusMl
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
        // User-reported: weight was never part of this formula - see
        // computeWaterNeedL's own doc comment on the weight-proportional
        // baseline now used when weightKg is known.
        val waterNeedL = BiolismEngine.computeWaterNeedL(biolismSex, mult, weightKg)
        // EFSA 2010 AI: +0.3L/day during pregnancy on top of the sex/activity baseline.
        val pregnancyBonusL = if ("pregnancy" in healthConditions) 0.3 else 0.0
        return ((waterNeedL + pregnancyBonusL) * 1000).toInt() + exerciseBonusMl
    }

    // ---- Backup export/import ----

    /** Reactive equivalent of [exportAll] — for screens (e.g. the Evolution tab)
     * that must reflect hydration logged elsewhere while they stay open, unlike
     * a one-shot suspend read taken once at construction. */
    // profileId defaults to "default" - BackupRepository's exportAll/importAll
    // (below) call this with no args, so their behavior/format is unchanged
    // and stays scoped to the default profile (multi-profile backup is a
    // separate, larger schema change, not covered by this profileId threading
    // pass). EvolutionViewModel passes the real active profile id.
    fun observeAll(profileId: String = "default"): Flow<List<Pair<LocalDate, Int>>> = storeData.map { prefs ->
        val prefix = keyPrefix(profileId)
        prefs.asMap().entries.mapNotNull { (pref, value) ->
            if (!pref.name.startsWith(prefix)) return@mapNotNull null
            val date = runCatching { LocalDate.parse(pref.name.removePrefix(prefix)) }.getOrNull() ?: return@mapNotNull null
            val ml = value as? Int ?: return@mapNotNull null
            date to ml
        }
    }

    /** All (date, mL) entries currently stored for the default profile, for BackupRepository. */
    suspend fun exportAll(): List<Pair<LocalDate, Int>> = observeAll().first()

    /** Restores entries from a backup — overwrites any existing value for the same date. */
    suspend fun importAll(entries: List<Pair<LocalDate, Int>>) {
        if (entries.isEmpty()) return
        store.edit { prefs -> entries.forEach { (date, ml) -> prefs[key(date)] = ml } }
    }
}
