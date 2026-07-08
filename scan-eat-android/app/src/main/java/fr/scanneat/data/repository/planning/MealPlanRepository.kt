package fr.scanneat.data.repository.planning

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
// MEAL PLAN REPOSITORY — port of public/features/meal-plan.js
//
// Forward meal plan: what you INTEND to eat over the next 7 days.
// Distinct from ConsumptionRepository (what you DID eat).
//
// Storage: DataStore single JSON key → Map<date, Map<meal, MealPlanSlot>>
// Auto-prunes entries older than KEEP_DAYS_PAST days on every write.
// ============================================================================

private val Context.mealPlanDataStore by preferencesDataStore(name = "meal_plan")
private val KEY_PLAN = stringPreferencesKey("plan_json")

private const val KEEP_DAYS_PAST = 7

sealed class MealPlanSlot {
    data class RecipeSlot(val id: String, val name: String) : MealPlanSlot()
    data class TemplateSlot(val id: String, val name: String) : MealPlanSlot()
    data class NoteSlot(val text: String) : MealPlanSlot()
}

data class DayPlan(
    val date: LocalDate,
    val breakfast: MealPlanSlot? = null,
    val lunch: MealPlanSlot? = null,
    val dinner: MealPlanSlot? = null,
    val snack: MealPlanSlot? = null,
) {
    operator fun get(meal: String): MealPlanSlot? = when (meal.lowercase()) {
        "breakfast" -> breakfast
        "lunch"     -> lunch
        "dinner"    -> dinner
        "snack"     -> snack
        else        -> null
    }
    fun with(meal: String, slot: MealPlanSlot?): DayPlan = when (meal.lowercase()) {
        "breakfast" -> copy(breakfast = slot)
        "lunch"     -> copy(lunch     = slot)
        "dinner"    -> copy(dinner    = slot)
        "snack"     -> copy(snack     = slot)
        else        -> this
    }
}

@Singleton
class MealPlanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.mealPlanDataStore

    // ---- Observe ----

    val weekPlan: Flow<Map<LocalDate, DayPlan>> = store.data.map { prefs ->
        deserialize(prefs[KEY_PLAN] ?: "")
    }

    fun dayPlan(date: LocalDate): Flow<DayPlan> = weekPlan.map { plan ->
        plan[date] ?: DayPlan(date)
    }

    // ---- Mutations ----

    /** Set a slot for a specific date + meal. Prunes old entries. */
    suspend fun setSlot(date: LocalDate, meal: String, slot: MealPlanSlot?) {
        store.edit { prefs ->
            val plan = deserialize(prefs[KEY_PLAN] ?: "").toMutableMap()
            val day = plan[date] ?: DayPlan(date)
            plan[date] = day.with(meal, slot)
            prefs[KEY_PLAN] = serialize(prune(plan))
        }
    }

    /** Clear all slots for a given day. */
    suspend fun clearDay(date: LocalDate) {
        store.edit { prefs ->
            val plan = deserialize(prefs[KEY_PLAN] ?: "").toMutableMap()
            plan.remove(date)
            prefs[KEY_PLAN] = serialize(prune(plan))
        }
    }

    /** Clear the entire plan. */
    suspend fun clearAll() {
        store.edit { prefs -> prefs.remove(KEY_PLAN) }
    }

    /** Dates with at least one slot set, for the next 7 days from today. */
    fun weekDates(startDate: LocalDate = LocalDate.now()): List<LocalDate> =
        (0 until 7).map { startDate.plusDays(it.toLong()) }

    // ---- Pruning ----

    private fun prune(plan: Map<LocalDate, DayPlan>): Map<LocalDate, DayPlan> {
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS_PAST.toLong())
        return plan.filterKeys { !it.isBefore(cutoff) }
    }

    // ---- Serialization (pipe-delimited, no Moshi to keep DataStore simple) ----
    // Format per entry: date|meal|kind|id_or_text|name
    // Entries separated by newline.

    private fun serialize(plan: Map<LocalDate, DayPlan>): String =
        plan.flatMap { (date, day) ->
            listOf("breakfast" to day.breakfast, "lunch" to day.lunch,
                   "dinner" to day.dinner, "snack" to day.snack)
                .mapNotNull { (meal, slot) ->
                    slot ?: return@mapNotNull null
                    when (slot) {
                        is MealPlanSlot.RecipeSlot   -> "$date|$meal|recipe|${slot.id}|${slot.name}"
                        is MealPlanSlot.TemplateSlot -> "$date|$meal|template|${slot.id}|${slot.name}"
                        is MealPlanSlot.NoteSlot     -> "$date|$meal|note||${slot.text}"
                    }
                }
        }.joinToString("\n")

    private fun deserialize(raw: String): Map<LocalDate, DayPlan> {
        if (raw.isBlank()) return emptyMap()
        val plan = mutableMapOf<LocalDate, DayPlan>()
        for (line in raw.lines()) {
            val parts = line.split("|", limit = 5)
            if (parts.size < 5) continue
            val (dateStr, meal, kind, id, nameOrText) = parts
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            val slot: MealPlanSlot = when (kind) {
                "recipe"   -> MealPlanSlot.RecipeSlot(id, nameOrText)
                "template" -> MealPlanSlot.TemplateSlot(id, nameOrText)
                "note"     -> MealPlanSlot.NoteSlot(nameOrText)
                else       -> continue
            }
            val day = plan[date] ?: DayPlan(date)
            plan[date] = day.with(meal, slot)
        }
        return plan
    }
}

private operator fun <A, B, C, D, E> List<*>.component5(): E {
    @Suppress("UNCHECKED_CAST")
    return get(4) as E
}
