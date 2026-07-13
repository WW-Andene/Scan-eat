package fr.scanneat.presentation.hydration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.HYD_DEFAULT_GOAL_ML
import fr.scanneat.data.repository.health.HydrationRepository
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

    fun addGlass()    = viewModelScope.launch { repo.addGlass() }
    fun removeGlass() = viewModelScope.launch { repo.removeGlass() }
}
