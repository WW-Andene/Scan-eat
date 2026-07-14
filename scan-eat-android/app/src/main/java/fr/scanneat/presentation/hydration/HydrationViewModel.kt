package fr.scanneat.presentation.hydration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.HYD_DEFAULT_GOAL_ML
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.domain.model.ActivityLevel
import java.time.LocalDate
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

    val goal: StateFlow<Int> = prefs.profile
        .map { repo.goalMl(it.sex, it.activityLevel, it.healthConditions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HYD_DEFAULT_GOAL_ML)

    // Dates with at least one glass logged — drives the calendar marker dots.
    // Re-derived off `intake` (not a one-shot fetch) so logging a glass today
    // updates today's dot immediately instead of only after the screen is
    // left and reopened (WhileSubscribed would otherwise cache a stale set).
    val markedDates: StateFlow<Set<LocalDate>> = intake.map {
        repo.exportAll().filter { it.second > 0 }.map { it.first }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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

    fun addGlass()    = viewModelScope.launch { repo.addGlass() }
    fun removeGlass() = viewModelScope.launch { repo.removeGlass() }
}
