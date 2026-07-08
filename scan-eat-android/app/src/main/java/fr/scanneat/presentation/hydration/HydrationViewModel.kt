package fr.scanneat.presentation.hydration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.HydrationRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HydrationViewModel @Inject constructor(
    private val repo: HydrationRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    val intake: StateFlow<Int> = repo.observe(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val goal: StateFlow<Int> = prefs.profile
        .map { repo.goalMl(it.weightKg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HydrationRepository.HYD_DEFAULT_GOAL_ML)

    fun addGlass()    = viewModelScope.launch { repo.addGlass() }
    fun removeGlass() = viewModelScope.launch { repo.removeGlass() }
}
