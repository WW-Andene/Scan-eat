package fr.scanneat.presentation.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.reminders.CustomReminder
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
    fun setSnack(on: Boolean, time: String)     = viewModelScope.launch { repo.setSnack(on, time) }
    fun setLunch(on: Boolean, time: String)     = viewModelScope.launch { repo.setLunch(on, time) }
    fun setDinner(on: Boolean, time: String)    = viewModelScope.launch { repo.setDinner(on, time) }
    fun setHydration(on: Boolean, intervalHours: Int) = viewModelScope.launch { repo.setHydration(on, intervalHours) }
    fun setHydrationCustom(on: Boolean, time: String) = viewModelScope.launch { repo.setHydrationCustom(on, time) }
    fun setWeight(on: Boolean, thresholdDays: Int)    = viewModelScope.launch { repo.setWeight(on, thresholdDays) }
    fun setActivity(on: Boolean, thresholdDays: Int)  = viewModelScope.launch { repo.setActivity(on, thresholdDays) }
    fun setWeightCustom(on: Boolean, time: String)    = viewModelScope.launch { repo.setWeightCustom(on, time) }

    fun setBreakfastLabel(label: String) = viewModelScope.launch { repo.setBreakfastLabel(label) }
    fun setSnackLabel(label: String)     = viewModelScope.launch { repo.setSnackLabel(label) }
    fun setLunchLabel(label: String)     = viewModelScope.launch { repo.setLunchLabel(label) }
    fun setDinnerLabel(label: String)    = viewModelScope.launch { repo.setDinnerLabel(label) }

    fun addCustomReminder(label: String, time: String) = viewModelScope.launch { repo.addCustomReminder(label, time) }
    fun updateCustomReminder(r: CustomReminder)        = viewModelScope.launch { repo.updateCustomReminder(r) }
    fun deleteCustomReminder(id: Int)                  = viewModelScope.launch { repo.deleteCustomReminder(id) }
    fun setDailyDigest(on: Boolean)                    = viewModelScope.launch { repo.setDailyDigest(on) }
}
