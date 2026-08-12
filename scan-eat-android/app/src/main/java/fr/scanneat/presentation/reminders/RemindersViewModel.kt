package fr.scanneat.presentation.reminders

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.reminders.CustomReminder
import fr.scanneat.data.repository.reminders.ReminderSettings
import fr.scanneat.data.repository.reminders.RemindersRepository
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repo: RemindersRepository,
) : ActionFailureViewModel() {
    val settings: StateFlow<ReminderSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderSettings())

    fun setBreakfast(on: Boolean, time: String) = guardedLaunch { repo.setBreakfast(on, time) }
    fun setSnack(on: Boolean, time: String)     = guardedLaunch { repo.setSnack(on, time) }
    fun setLunch(on: Boolean, time: String)     = guardedLaunch { repo.setLunch(on, time) }
    fun setDinner(on: Boolean, time: String)    = guardedLaunch { repo.setDinner(on, time) }
    fun setHydration(on: Boolean, intervalHours: Int) = guardedLaunch { repo.setHydration(on, intervalHours) }
    fun setHydrationCustom(on: Boolean, time: String) = guardedLaunch { repo.setHydrationCustom(on, time) }
    fun setWeight(on: Boolean, thresholdDays: Int)    = guardedLaunch { repo.setWeight(on, thresholdDays) }
    fun setActivity(on: Boolean, thresholdDays: Int)  = guardedLaunch { repo.setActivity(on, thresholdDays) }
    fun setWeightCustom(on: Boolean, time: String)    = guardedLaunch { repo.setWeightCustom(on, time) }

    fun setBreakfastLabel(label: String) = guardedLaunch { repo.setBreakfastLabel(label) }
    fun setSnackLabel(label: String)     = guardedLaunch { repo.setSnackLabel(label) }
    fun setLunchLabel(label: String)     = guardedLaunch { repo.setLunchLabel(label) }
    fun setDinnerLabel(label: String)    = guardedLaunch { repo.setDinnerLabel(label) }

    fun addCustomReminder(label: String, time: String) = guardedLaunch { repo.addCustomReminder(label, time) }
    fun updateCustomReminder(r: CustomReminder)        = guardedLaunch { repo.updateCustomReminder(r) }
    fun deleteCustomReminder(id: Int)                  = guardedLaunch { repo.deleteCustomReminder(id) }
    fun setDailyDigest(on: Boolean)                    = guardedLaunch { repo.setDailyDigest(on) }
    fun setPantryExpiry(on: Boolean)                   = guardedLaunch { repo.setPantryExpiry(on) }
}
