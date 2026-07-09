package fr.scanneat.data.repository.biolism

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.domain.engine.biolism.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.biolismStore: DataStore<Preferences>
    by preferencesDataStore(name = "biolism_prefs")

/** Maps the app-wide profile's sex/activity onto Biolism's own vocabulary. */
private fun fr.scanneat.domain.model.Sex.toBiolismSex(): BiolismSex = when (this) {
    fr.scanneat.domain.model.Sex.MALE          -> BiolismSex.MALE
    fr.scanneat.domain.model.Sex.FEMALE        -> BiolismSex.FEMALE
    fr.scanneat.domain.model.Sex.NOT_SPECIFIED -> BiolismSex.NOT_SPECIFIED
}

private fun fr.scanneat.domain.model.ActivityLevel.toBiolismActivityId(): String = when (this) {
    fr.scanneat.domain.model.ActivityLevel.SEDENTARY         -> "sedentary"
    fr.scanneat.domain.model.ActivityLevel.LIGHTLY_ACTIVE     -> "light"
    fr.scanneat.domain.model.ActivityLevel.MODERATELY_ACTIVE  -> "moderate"
    fr.scanneat.domain.model.ActivityLevel.VERY_ACTIVE        -> "very"
    fr.scanneat.domain.model.ActivityLevel.EXTRA_ACTIVE       -> "extra"
}

@Singleton
class BiolismRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences,
) {
    private val store = context.biolismStore

    // ─────────────────────────────────────────────────────────────────────────
    // Keys
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        val K_SEX          = stringPreferencesKey("bio_sex")
        val K_AGE          = intPreferencesKey("bio_age")
        val K_HEIGHT       = floatPreferencesKey("bio_height_cm")
        val K_WEIGHT       = floatPreferencesKey("bio_weight_kg")
        val K_ACTIVITY     = stringPreferencesKey("bio_activity")
        val K_ETHNICITY    = stringPreferencesKey("bio_ethnicity")
        val K_WAIST        = floatPreferencesKey("bio_waist_cm")
        val K_HIP          = floatPreferencesKey("bio_hip_cm")
        val K_NECK         = floatPreferencesKey("bio_neck_cm")
        val K_CYCLE_DAY    = intPreferencesKey("bio_cycle_day")

        // Session timer state
        val K_SESS_RUNNING      = booleanPreferencesKey("bio_sess_running")
        val K_SESS_WALL_START   = longPreferencesKey("bio_sess_wall_start")   // epoch ms, 0 = not set
        val K_SESS_ACCUMULATED  = longPreferencesKey("bio_sess_acc_ms")
        val K_KETO_RUNNING      = booleanPreferencesKey("bio_keto_running")
        val K_KETO_WALL_START   = longPreferencesKey("bio_keto_wall_start")
        val K_KETO_ACCUMULATED  = longPreferencesKey("bio_keto_acc_ms")
        val K_KETOSIS_ON        = booleanPreferencesKey("bio_ketosis_on")
        val K_KETO_ADAPTED      = booleanPreferencesKey("bio_keto_adapted")
        val K_FASTING_ACTIVE    = booleanPreferencesKey("bio_fasting_active")
        val K_LAST_MEAL_TS      = longPreferencesKey("bio_last_meal_ts")      // epoch ms, 0 = not set

        // Caloric balance — stored as JSON per date ("YYYY-MM-DD" → MealEntryDay json)
        // We use a single large key for today's data (max 5 MB per DataStore key is fine)
        val K_CALORIC_TODAY     = stringPreferencesKey("bio_caloric_today")   // JSON
        val K_CALORIC_DATE      = stringPreferencesKey("bio_caloric_date")    // the date K_CALORIC_TODAY covers
        val K_HISTORY_7D        = stringPreferencesKey("bio_caloric_7d")      // JSON list[DayEntry]

        // Session history (last 20 sessions, JSON list)
        val K_SESSIONS          = stringPreferencesKey("bio_sessions")

        // Manual HR for Fick cross-check
        val K_MANUAL_HR         = intPreferencesKey("bio_manual_hr")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────────────────────────────────
    // Sex/age/height/weight/activity are shared with the app-wide profile so the user only
    // fills them in once — Biolism only keeps its own copy once the user explicitly edits and
    // saves them from within Biolism (K_SEX present means an explicit Biolism-side override).
    val profile: Flow<BiolismProfile> = combine(store.data, userPreferences.profile) { p, mainProfile ->
        val hasOwnOverride = p[K_SEX] != null
        val ethnicityId = p[K_ETHNICITY] ?: "caucasian"
        val waistCm      = p[K_WAIST]?.toDouble() ?: 0.0
        val hipCm        = p[K_HIP]?.toDouble()   ?: 0.0
        val neckCm       = p[K_NECK]?.toDouble()  ?: 0.0
        val cycleDay     = p[K_CYCLE_DAY] ?: 14

        if (hasOwnOverride) {
            BiolismProfile(
                sex         = BiolismSex.values().firstOrNull { it.name == p[K_SEX] } ?: BiolismSex.NOT_SPECIFIED,
                ageYears    = p[K_AGE] ?: 0,
                heightCm    = p[K_HEIGHT]?.toDouble() ?: 0.0,
                weightKg    = p[K_WEIGHT]?.toDouble() ?: 0.0,
                activityId  = p[K_ACTIVITY] ?: "sedentary",
                ethnicityId = ethnicityId,
                waistCm     = waistCm, hipCm = hipCm, neckCm = neckCm, cycleDay = cycleDay,
            )
        } else {
            BiolismProfile(
                sex         = mainProfile.sex.toBiolismSex(),
                ageYears    = mainProfile.ageYears ?: 0,
                heightCm    = mainProfile.heightCm ?: 0.0,
                weightKg    = mainProfile.weightKg ?: 0.0,
                activityId  = mainProfile.activityLevel.toBiolismActivityId(),
                ethnicityId = ethnicityId,
                waistCm     = waistCm, hipCm = hipCm, neckCm = neckCm, cycleDay = cycleDay,
            )
        }
    }

    suspend fun saveProfile(profile: BiolismProfile) = store.edit { p ->
        p[K_SEX]       = profile.sex.name
        p[K_AGE]       = profile.ageYears
        p[K_HEIGHT]    = profile.heightCm.toFloat()
        p[K_WEIGHT]    = profile.weightKg.toFloat()
        p[K_ACTIVITY]  = profile.activityId
        p[K_ETHNICITY] = profile.ethnicityId
        p[K_WAIST]     = profile.waistCm.toFloat()
        p[K_HIP]       = profile.hipCm.toFloat()
        p[K_NECK]      = profile.neckCm.toFloat()
        p[K_CYCLE_DAY] = profile.cycleDay
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session timer state
    // ─────────────────────────────────────────────────────────────────────────
    data class TimerState(
        val running: Boolean = false,
        val wallStartMs: Long = 0L,
        val accumulatedMs: Long = 0L,
        val ketoRunning: Boolean = false,
        val ketoWallStartMs: Long = 0L,
        val ketoAccumulatedMs: Long = 0L,
        val ketosisOn: Boolean = false,
        val ketoAdapted: Boolean = false,
        val fastingActive: Boolean = false,
        val lastMealTs: Long = 0L,
    ) {
        val elapsedMs: Long get() {
            val wall = if (running && wallStartMs > 0) System.currentTimeMillis() - wallStartMs else 0L
            return accumulatedMs + wall
        }
        val ketoElapsedMs: Long get() {
            val wall = if (ketoRunning && ketoWallStartMs > 0) System.currentTimeMillis() - ketoWallStartMs else 0L
            return ketoAccumulatedMs + wall
        }
        val fastingHours: Double get() {
            if (!fastingActive || lastMealTs == 0L) return 0.0
            return (System.currentTimeMillis() - lastMealTs) / 3_600_000.0
        }
        val ketoHours: Double get() = ketoElapsedMs / 3_600_000.0
    }

    val timerState: Flow<TimerState> = store.data.map { p ->
        TimerState(
            running          = p[K_SESS_RUNNING]   ?: false,
            wallStartMs      = p[K_SESS_WALL_START] ?: 0L,
            accumulatedMs    = p[K_SESS_ACCUMULATED]?: 0L,
            ketoRunning      = p[K_KETO_RUNNING]    ?: false,
            ketoWallStartMs  = p[K_KETO_WALL_START] ?: 0L,
            ketoAccumulatedMs= p[K_KETO_ACCUMULATED]?: 0L,
            ketosisOn        = p[K_KETOSIS_ON]      ?: false,
            ketoAdapted      = p[K_KETO_ADAPTED]    ?: false,
            fastingActive    = p[K_FASTING_ACTIVE]  ?: false,
            lastMealTs       = p[K_LAST_MEAL_TS]    ?: 0L,
        )
    }

    suspend fun saveTimerState(state: TimerState) = store.edit { p ->
        p[K_SESS_RUNNING]    = state.running
        p[K_SESS_WALL_START] = state.wallStartMs
        p[K_SESS_ACCUMULATED]= state.accumulatedMs
        p[K_KETO_RUNNING]    = state.ketoRunning
        p[K_KETO_WALL_START] = state.ketoWallStartMs
        p[K_KETO_ACCUMULATED]= state.ketoAccumulatedMs
        p[K_KETOSIS_ON]      = state.ketosisOn
        p[K_KETO_ADAPTED]    = state.ketoAdapted
        p[K_FASTING_ACTIVE]  = state.fastingActive
        p[K_LAST_MEAL_TS]    = state.lastMealTs
    }

    suspend fun logMealNow() = store.edit { p ->
        p[K_LAST_MEAL_TS] = System.currentTimeMillis()
        p[K_FASTING_ACTIVE] = true
    }

    suspend fun setKetosisOn(on: Boolean) = store.edit { p -> p[K_KETOSIS_ON] = on }
    suspend fun setKetoAdapted(on: Boolean) = store.edit { p -> p[K_KETO_ADAPTED] = on }

    val manualHR: Flow<Int?> = store.data.map { p -> p[K_MANUAL_HR] }
    suspend fun saveManualHR(bpm: Int?) = store.edit { p ->
        if (bpm != null) p[K_MANUAL_HR] = bpm else p.remove(K_MANUAL_HR)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caloric balance diary — simple per-slot kcal + macros, today only
    // ─────────────────────────────────────────────────────────────────────────
    @Serializable
    data class MealEntry(
        val slotId: String,
        val kcal: Double,
        val proteinG: Double = 0.0,
        val carbsG: Double = 0.0,
        val fatG: Double = 0.0,
        val ts: Long = System.currentTimeMillis(),
    )

    @Serializable
    data class DayEntries(val date: String, val meals: List<MealEntry>)

    val todayMeals: Flow<List<MealEntry>> = store.data.map { p ->
        val today = LocalDate.now().toString()
        val storedDate = p[K_CALORIC_DATE]
        if (storedDate == today) {
            val json = p[K_CALORIC_TODAY] ?: return@map emptyList()
            runCatching { Json.decodeFromString<List<MealEntry>>(json) }.getOrElse { emptyList() }
        } else emptyList()
    }

    suspend fun saveMeal(entry: MealEntry) = store.edit { p ->
        val today = LocalDate.now().toString()
        val storedDate = p[K_CALORIC_DATE]
        val current: List<MealEntry> = if (storedDate == today) {
            runCatching {
                Json.decodeFromString<List<MealEntry>>(p[K_CALORIC_TODAY] ?: "[]")
            }.getOrElse { emptyList() }
        } else emptyList()
        val updated = current.filter { it.slotId != entry.slotId } + entry
        p[K_CALORIC_DATE]  = today
        p[K_CALORIC_TODAY] = Json.encodeToString(updated)
    }

    suspend fun clearMeal(slotId: String) = store.edit { p ->
        val today = LocalDate.now().toString()
        if (p[K_CALORIC_DATE] != today) return@edit
        val current = runCatching {
            Json.decodeFromString<List<MealEntry>>(p[K_CALORIC_TODAY] ?: "[]")
        }.getOrElse { emptyList() }
        p[K_CALORIC_TODAY] = Json.encodeToString(current.filter { it.slotId != slotId })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session history (last 20 sessions)
    // ─────────────────────────────────────────────────────────────────────────
    val sessions: Flow<List<BiolismSession>> = store.data.map { p ->
        val json = p[K_SESSIONS] ?: return@map emptyList()
        runCatching { Json.decodeFromString<List<SerializableSession>>(json) }
            .getOrElse { emptyList() }
            .map { it.toDomain() }
    }

    suspend fun saveSession(session: BiolismSession) = store.edit { p ->
        val current = runCatching {
            Json.decodeFromString<List<SerializableSession>>(p[K_SESSIONS] ?: "[]")
        }.getOrElse { emptyList() }
        val updated = (current + SerializableSession.fromDomain(session)).takeLast(20)
        p[K_SESSIONS] = Json.encodeToString(updated)
    }

    suspend fun deleteSession(id: Long) = store.edit { p ->
        val current = runCatching {
            Json.decodeFromString<List<SerializableSession>>(p[K_SESSIONS] ?: "[]")
        }.getOrElse { emptyList() }
        p[K_SESSIONS] = Json.encodeToString(current.filter { it.id != id })
    }

    @Serializable
    private data class SerializableSession(
        val id: Long, val timestamp: String, val elapsedSec: Double,
        val kcalBurned: Double, val kcalPerMin: Double,
        val bmrDay: Double, val tdeeDay: Double, val activityLabel: String,
        val ketosis: Boolean, val startWeightKg: Double, val endWeightKg: Double,
        val fatFrac: Double, val fatLostKg: Double,
    ) {
        fun toDomain() = BiolismSession(
            id, timestamp, elapsedSec, kcalBurned, kcalPerMin, bmrDay, tdeeDay,
            activityLabel, ketosis, startWeightKg, endWeightKg, fatFrac, fatLostKg,
        )
        companion object {
            fun fromDomain(s: BiolismSession) = SerializableSession(
                s.id, s.timestamp, s.elapsedSec, s.kcalBurned, s.kcalPerMin,
                s.bmrDay, s.tdeeDay, s.activityLabel, s.ketosis, s.startWeightKg,
                s.endWeightKg, s.fatFrac, s.fatLostKg,
            )
        }
    }
}
