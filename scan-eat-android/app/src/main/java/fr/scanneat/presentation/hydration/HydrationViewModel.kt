package fr.scanneat.presentation.hydration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.HYD_DEFAULT_GOAL_ML
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.domain.model.ActivityLevel
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HydrationViewModel @Inject constructor(
    private val repo: HydrationRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    // LocalDate.now() captured once at construction would keep observing
    // today's bucket forever if this ViewModel outlives midnight - polling
    // + distinctUntilChanged re-subscribes intake to the new day exactly when
    // it actually rolls over, same fix DiaryViewModel applies via selectedDate.
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val intake: StateFlow<Int> = today.flatMapLatest { date -> repo.observe(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val formulaGoal: Flow<Int> = prefs.profile
        .map { repo.goalMl(it.sex, it.activityLevel, it.healthConditions) }

    /** Null when no override is set - screen shows this to offer "reset to formula". */
    val customGoalMl: StateFlow<Int?> = repo.customGoalMl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val goal: StateFlow<Int> = combine(formulaGoal, repo.customGoalMl) { formula, custom -> custom ?: formula }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HYD_DEFAULT_GOAL_ML)

    fun setCustomGoal(ml: Int?) = viewModelScope.launch {
        runCatching { repo.setCustomGoalMl(ml) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }

    // Improvement: consecutive-days streak — counts backwards from yesterday
    // (today is still in progress, so excluding it avoids a misleading "1-day
    // streak" that resets every morning before the first glass).
    val streak: StateFlow<Int> = combine(intake, goal) { _, goalMl ->
        val all = repo.exportAll().toMap()
        var count = 0
        var date = LocalDate.now().minusDays(1)
        while (true) {
            val dayMl = all[date] ?: 0
            if (dayMl < goalMl) break
            count++
            date = date.minusDays(1)
        }
        count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // New: smart goal suggestion — base goal + weight-scaled bonus (10 mL per kg
    // above 70 kg) + extra 300 mL for very-active/extra-active profile, shown as a
    // non-binding nudge when it differs from the current goal by ≥ 200 mL.
    val suggestedGoalMl: StateFlow<Int?> = combine(goal, prefs.profile) { currentGoal, profile ->
        val weightBonus = ((profile.weightKg ?: 70.0) - 70.0).coerceAtLeast(0.0) * 10
        val activityBonus = when (profile.activityLevel) {
            ActivityLevel.VERY_ACTIVE, ActivityLevel.EXTRA_ACTIVE -> 300
            else -> 0
        }
        val suggested = (currentGoal + weightBonus + activityBonus).toInt()
        if (suggested - currentGoal >= 200) suggested else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 7 days of intake (date → ml) for the weekly bar chart
    val weeklyIntake: StateFlow<List<Pair<LocalDate, Int>>> = intake.map {
        val all = repo.exportAll().toMap()
        val today = LocalDate.now()
        (6 downTo 0).map { daysBack ->
            val d = today.minusDays(daysBack.toLong())
            d to (all[d] ?: 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Number of days in the last 7 (including today) where intake met or exceeded the goal. */
    val weeklyGoalMetDays: StateFlow<Int> = combine(weeklyIntake, goal) { week, goalMl ->
        week.count { (_, ml) -> ml >= goalMl }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // addGlass()/removeGlass() previously called repo's DataStore write completely
    // unguarded - unlike every sibling tracker (Weight/Activity/Dashboard/MealPlan/
    // Templates all wrap theirs in runCatching), so a write failure here wasn't
    // just silent, it was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun addGlass()    = viewModelScope.launch { runCatching { repo.addGlass() }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    fun removeGlass() = viewModelScope.launch { runCatching { repo.removeGlass() }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}
