package fr.scanneat.presentation.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.DayPlan
import fr.scanneat.data.repository.MealPlanRepository
import fr.scanneat.data.repository.MealPlanSlot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MealPlanViewModel @Inject constructor(private val repo: MealPlanRepository) : ViewModel() {
    val weekDates: List<LocalDate> = repo.weekDates()

    val weekPlan: StateFlow<Map<LocalDate, DayPlan>> = repo.weekPlan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setNote(date: LocalDate, meal: String, text: String) {
        viewModelScope.launch { repo.setSlot(date, meal, if (text.isBlank()) null else MealPlanSlot.NoteSlot(text)) }
    }

    fun clear(date: LocalDate, meal: String) {
        viewModelScope.launch { repo.setSlot(date, meal, null) }
    }
}
