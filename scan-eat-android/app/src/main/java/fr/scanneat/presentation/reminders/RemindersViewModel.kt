package fr.scanneat.presentation.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.reminders.CustomReminder
import fr.scanneat.data.repository.reminders.ReminderSettings
import fr.scanneat.data.repository.reminders.RemindersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repo: RemindersRepository,
) : ViewModel() {
    val settings: StateFlow<ReminderSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderSettings())

    // Every setter below previously called repo's DataStore writes completely
    // unguarded - unlike every sibling tracker ViewModel (Weight/Activity/Dashboard/
    // MealPlan/Templates all wrap theirs in runCatching), so a write failure here
    // wasn't just silent, it was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    private fun guarded(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }

    fun setBreakfast(on: Boolean, time: String) = guarded { repo.setBreakfast(on, time) }
    fun setSnack(on: Boolean, time: String)     = guarded { repo.setSnack(on, time) }
    fun setLunch(on: Boolean, time: String)     = guarded { repo.setLunch(on, time) }
    fun setDinner(on: Boolean, time: String)    = guarded { repo.setDinner(on, time) }
    fun setHydration(on: Boolean, intervalHours: Int) = guarded { repo.setHydration(on, intervalHours) }
    fun setHydrationCustom(on: Boolean, time: String) = guarded { repo.setHydrationCustom(on, time) }
    fun setWeight(on: Boolean, thresholdDays: Int)    = guarded { repo.setWeight(on, thresholdDays) }
    fun setActivity(on: Boolean, thresholdDays: Int)  = guarded { repo.setActivity(on, thresholdDays) }
    fun setWeightCustom(on: Boolean, time: String)    = guarded { repo.setWeightCustom(on, time) }

    fun setBreakfastLabel(label: String) = guarded { repo.setBreakfastLabel(label) }
    fun setSnackLabel(label: String)     = guarded { repo.setSnackLabel(label) }
    fun setLunchLabel(label: String)     = guarded { repo.setLunchLabel(label) }
    fun setDinnerLabel(label: String)    = guarded { repo.setDinnerLabel(label) }

    fun addCustomReminder(label: String, time: String) = guarded { repo.addCustomReminder(label, time) }
    fun updateCustomReminder(r: CustomReminder)        = guarded { repo.updateCustomReminder(r) }
    fun deleteCustomReminder(id: Int)                  = guarded { repo.deleteCustomReminder(id) }
    fun setDailyDigest(on: Boolean)                    = guarded { repo.setDailyDigest(on) }
}
