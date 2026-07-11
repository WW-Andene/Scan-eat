package fr.scanneat.presentation.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.planning.DayPlan
import fr.scanneat.data.repository.planning.MealPlanRepository
import fr.scanneat.data.repository.planning.MealPlanSlot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val repo: MealPlanRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    val weekDates: List<LocalDate> = repo.weekDates()

    val weekPlan: StateFlow<Map<LocalDate, DayPlan>> = repo.weekPlan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    fun setNote(date: LocalDate, meal: String, text: String) {
        // Entries are newline-delimited in storage (see MealPlanRepository.serialize) —
        // strip any embedded newline (e.g. from pasted clipboard text) so a note can't
        // split across "lines" and corrupt the following entry.
        val sanitized = text.replace("\n", " ")
        viewModelScope.launch { repo.setSlot(date, meal, if (sanitized.isBlank()) null else MealPlanSlot.NoteSlot(sanitized)) }
    }

    fun clear(date: LocalDate, meal: String) {
        viewModelScope.launch { repo.setSlot(date, meal, null) }
    }
}
