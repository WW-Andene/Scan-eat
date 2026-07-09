package fr.scanneat.presentation.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.reminders.ReminderSettings
import fr.scanneat.data.repository.reminders.RemindersRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repo: RemindersRepository,
) : ViewModel() {
    val settings: StateFlow<ReminderSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderSettings())

    fun setBreakfast(on: Boolean, time: String) = viewModelScope.launch { repo.setBreakfast(on, time) }
    fun setLunch(on: Boolean, time: String)     = viewModelScope.launch { repo.setLunch(on, time) }
    fun setDinner(on: Boolean, time: String)    = viewModelScope.launch { repo.setDinner(on, time) }
    fun setHydration(on: Boolean, intervalHours: Int) = viewModelScope.launch { repo.setHydration(on, intervalHours) }
    fun setWeight(on: Boolean, thresholdDays: Int)    = viewModelScope.launch { repo.setWeight(on, thresholdDays) }
}
