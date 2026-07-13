package fr.scanneat.data.repository.reminders

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.domain.model.MS_PER_HOUR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CustomReminder(val id: Int, val label: String, val time: String, val on: Boolean)

// ============================================================================
// REMINDERS REPOSITORY — port of Biolism/src/App.jsx RemindersCard
//
// Meal reminders (breakfast/lunch/dinner) fire once per day at a configured
// HH:mm time. Hydration reminders fire every N hours within an 08:00-22:00
// window. Weight-log reminders fire once per day when no entry has been
// logged within a configured day threshold.
// ============================================================================

data class ReminderSettings(
    val breakfastOn: Boolean = false, val breakfastTime: String = "06:00", val breakfastLabel: String = "",
    val snackOn: Boolean = false, val snackTime: String = "09:00", val snackLabel: String = "",
    val lunchOn: Boolean = false, val lunchTime: String = "12:00", val lunchLabel: String = "",
    val dinnerOn: Boolean = false, val dinnerTime: String = "18:00", val dinnerLabel: String = "",
    val hydrationOn: Boolean = false, val hydrationIntervalHours: Int = 3,
    val hydrationCustomOn: Boolean = false, val hydrationCustomTime: String = "12:00",
    val weightOn: Boolean = false, val weightThresholdDays: Int = 1,
    val weightCustomOn: Boolean = false, val weightCustomTime: String = "12:00",
    val customReminders: List<CustomReminder> = emptyList(),
)

private val Context.remindersDataStore by preferencesDataStore(name = "reminders")

@Singleton
class RemindersRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.remindersDataStore

    // DataStore.data throws IOException on read/corruption errors — fall back to
    // an empty (default-valued) Preferences instead of crashing collectors.
    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    companion object {
        val K_BREAKFAST_ON   = booleanPreferencesKey("rem_breakfast_on")
        val K_BREAKFAST_TIME = stringPreferencesKey("rem_breakfast_time")
        val K_SNACK_ON       = booleanPreferencesKey("rem_snack_on")
        val K_SNACK_TIME     = stringPreferencesKey("rem_snack_time")
        val K_LUNCH_ON       = booleanPreferencesKey("rem_lunch_on")
        val K_LUNCH_TIME     = stringPreferencesKey("rem_lunch_time")
        val K_DINNER_ON      = booleanPreferencesKey("rem_dinner_on")
        val K_DINNER_TIME    = stringPreferencesKey("rem_dinner_time")
        val K_HYDRATION_ON       = booleanPreferencesKey("rem_hydration_on")
        val K_HYDRATION_INTERVAL = intPreferencesKey("rem_hydration_interval_h")
        val K_HYDRATION_CUSTOM_ON   = booleanPreferencesKey("rem_hydration_custom_on")
        val K_HYDRATION_CUSTOM_TIME = stringPreferencesKey("rem_hydration_custom_time")
        val K_WEIGHT_ON        = booleanPreferencesKey("rem_weight_on")
        val K_WEIGHT_THRESHOLD = intPreferencesKey("rem_weight_threshold_days")
        val K_WEIGHT_CUSTOM_ON   = booleanPreferencesKey("rem_weight_custom_on")
        val K_WEIGHT_CUSTOM_TIME = stringPreferencesKey("rem_weight_custom_time")

        val K_BREAKFAST_LABEL = stringPreferencesKey("rem_breakfast_label")
        val K_SNACK_LABEL     = stringPreferencesKey("rem_snack_label")
        val K_LUNCH_LABEL     = stringPreferencesKey("rem_lunch_label")
        val K_DINNER_LABEL    = stringPreferencesKey("rem_dinner_label")
        val K_CUSTOM_REMINDERS = stringPreferencesKey("rem_custom_list")

        val K_LAST_BREAKFAST_DATE    = stringPreferencesKey("rem_last_breakfast_date")
        val K_LAST_SNACK_DATE        = stringPreferencesKey("rem_last_snack_date")
        val K_LAST_LUNCH_DATE        = stringPreferencesKey("rem_last_lunch_date")
        val K_LAST_DINNER_DATE       = stringPreferencesKey("rem_last_dinner_date")
        val K_LAST_HYDRATION_MS      = longPreferencesKey("rem_last_hydration_ms")
        val K_LAST_HYDRATION_CUSTOM_DATE = stringPreferencesKey("rem_last_hydration_custom_date")
        val K_LAST_WEIGHT_NUDGE_DATE = stringPreferencesKey("rem_last_weight_nudge_date")
        val K_LAST_WEIGHT_CUSTOM_DATE     = stringPreferencesKey("rem_last_weight_custom_date")
    }

    val settings: Flow<ReminderSettings> = storeData.map { p ->
        val customs = runCatching { Json.decodeFromString<List<CustomReminder>>(p[K_CUSTOM_REMINDERS] ?: "[]") }.getOrElse { emptyList() }
        ReminderSettings(
            breakfastOn = p[K_BREAKFAST_ON] ?: false, breakfastTime = p[K_BREAKFAST_TIME] ?: "06:00", breakfastLabel = p[K_BREAKFAST_LABEL] ?: "",
            snackOn     = p[K_SNACK_ON] ?: false,      snackTime     = p[K_SNACK_TIME] ?: "09:00",     snackLabel     = p[K_SNACK_LABEL] ?: "",
            lunchOn     = p[K_LUNCH_ON] ?: false,      lunchTime     = p[K_LUNCH_TIME] ?: "12:00",     lunchLabel     = p[K_LUNCH_LABEL] ?: "",
            dinnerOn    = p[K_DINNER_ON] ?: false,      dinnerTime    = p[K_DINNER_TIME] ?: "18:00",    dinnerLabel    = p[K_DINNER_LABEL] ?: "",
            hydrationOn = p[K_HYDRATION_ON] ?: false,
            hydrationIntervalHours = p[K_HYDRATION_INTERVAL] ?: 3,
            hydrationCustomOn   = p[K_HYDRATION_CUSTOM_ON] ?: false,
            hydrationCustomTime = p[K_HYDRATION_CUSTOM_TIME] ?: "12:00",
            weightOn        = p[K_WEIGHT_ON] ?: false,
            weightThresholdDays = p[K_WEIGHT_THRESHOLD] ?: 1,
            weightCustomOn   = p[K_WEIGHT_CUSTOM_ON] ?: false,
            weightCustomTime = p[K_WEIGHT_CUSTOM_TIME] ?: "12:00",
            customReminders = customs,
        )
    }.distinctUntilChanged()

    // Enabling a reminder (or changing its time) to a time already past today
    // otherwise fires it on the very next worker run, however soon that is —
    // mark today as already-fired so the first real reminder is tomorrow at
    // the configured time, same as if it had already fired normally today.
    private fun markStaleIfPast(prefs: MutablePreferences, time: String, lastFiredKey: Preferences.Key<String>) {
        val target = runCatching { LocalTime.parse(time) }.getOrNull() ?: return
        if (!LocalTime.now().isBefore(target)) prefs[lastFiredKey] = LocalDate.now().toString()
    }

    suspend fun setBreakfastLabel(label: String) = store.edit { it[K_BREAKFAST_LABEL] = label }
    suspend fun setSnackLabel(label: String)     = store.edit { it[K_SNACK_LABEL] = label }
    suspend fun setLunchLabel(label: String)     = store.edit { it[K_LUNCH_LABEL] = label }
    suspend fun setDinnerLabel(label: String)    = store.edit { it[K_DINNER_LABEL] = label }

    private fun customs(p: Preferences): List<CustomReminder> =
        runCatching { Json.decodeFromString<List<CustomReminder>>(p[K_CUSTOM_REMINDERS] ?: "[]") }.getOrElse { emptyList() }

    suspend fun addCustomReminder(label: String, time: String) = store.edit { p ->
        val list = customs(p)
        val nextId = (list.maxOfOrNull { it.id } ?: 909) + 1
        p[K_CUSTOM_REMINDERS] = Json.encodeToString(list + CustomReminder(nextId, label, time, false))
    }

    suspend fun updateCustomReminder(reminder: CustomReminder) = store.edit { p ->
        val list = customs(p).map { if (it.id == reminder.id) reminder else it }
        p[K_CUSTOM_REMINDERS] = Json.encodeToString(list)
    }

    suspend fun deleteCustomReminder(id: Int) = store.edit { p ->
        p[K_CUSTOM_REMINDERS] = Json.encodeToString(customs(p).filter { it.id != id })
    }

    val customLastFiredKey: (Int) -> Preferences.Key<String> = { id -> stringPreferencesKey("rem_custom_${id}_last") }

    suspend fun setBreakfast(on: Boolean, time: String) = store.edit {
        it[K_BREAKFAST_ON] = on; it[K_BREAKFAST_TIME] = time
        if (on) markStaleIfPast(it, time, K_LAST_BREAKFAST_DATE)
    }
    suspend fun setLunch(on: Boolean, time: String) = store.edit {
        it[K_LUNCH_ON] = on; it[K_LUNCH_TIME] = time
        if (on) markStaleIfPast(it, time, K_LAST_LUNCH_DATE)
    }
    suspend fun setSnack(on: Boolean, time: String) = store.edit {
        it[K_SNACK_ON] = on; it[K_SNACK_TIME] = time
        if (on) markStaleIfPast(it, time, K_LAST_SNACK_DATE)
    }
    suspend fun setDinner(on: Boolean, time: String) = store.edit {
        it[K_DINNER_ON] = on; it[K_DINNER_TIME] = time
        if (on) markStaleIfPast(it, time, K_LAST_DINNER_DATE)
    }
    suspend fun setHydration(on: Boolean, intervalHours: Int) = store.edit { it[K_HYDRATION_ON] = on; it[K_HYDRATION_INTERVAL] = intervalHours }
    suspend fun setHydrationCustom(on: Boolean, time: String) = store.edit {
        it[K_HYDRATION_CUSTOM_ON] = on; it[K_HYDRATION_CUSTOM_TIME] = time
        if (on) markStaleIfPast(it, time, K_LAST_HYDRATION_CUSTOM_DATE)
    }
    suspend fun setWeight(on: Boolean, thresholdDays: Int)    = store.edit { it[K_WEIGHT_ON] = on; it[K_WEIGHT_THRESHOLD] = thresholdDays }
    suspend fun setWeightCustom(on: Boolean, time: String) = store.edit {
        it[K_WEIGHT_CUSTOM_ON] = on; it[K_WEIGHT_CUSTOM_TIME] = time
        if (on) markStaleIfPast(it, time, K_LAST_WEIGHT_CUSTOM_DATE)
    }

    suspend fun wasFiredToday(key: Preferences.Key<String>): Boolean =
        storeData.first()[key] == LocalDate.now().toString()

    suspend fun markFiredToday(key: Preferences.Key<String>) = store.edit { it[key] = LocalDate.now().toString() }

    /** Returns true (and marks) if at least [intervalHours] have elapsed since the last hydration fire. */
    suspend fun hydrationDueAndMark(intervalHours: Int): Boolean {
        var due = false
        store.edit { p ->
            val now = System.currentTimeMillis()
            val last = p[K_LAST_HYDRATION_MS] ?: 0L
            if (now - last >= intervalHours * MS_PER_HOUR) {
                due = true
                p[K_LAST_HYDRATION_MS] = now
            }
        }
        return due
    }
}
