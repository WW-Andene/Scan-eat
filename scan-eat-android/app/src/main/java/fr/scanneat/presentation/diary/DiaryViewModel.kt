package fr.scanneat.presentation.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.DayNotesRepository
import fr.scanneat.domain.model.DailySummary
import fr.scanneat.domain.model.DiaryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val consumptionRepo: ConsumptionRepository,
    private val notesRepo: DayNotesRepository,
) : ViewModel() {

    // Fix 13: selectedDate as a StateFlow — avoids stale data across midnight
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // Flat-map so the observation restarts whenever the date changes
    val summary: StateFlow<DailySummary> = _selectedDate
        .flatMapLatest { date -> consumptionRepo.observeDay(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            DailySummary(LocalDate.now(), emptyList(), ConsumedNutrition.ZERO))

    fun goToPreviousDay() { _selectedDate.value = _selectedDate.value.minusDays(1) }
    fun goToNextDay()     { _selectedDate.value = _selectedDate.value.plusDays(1) }
    fun goToToday()       { _selectedDate.value = LocalDate.now() }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    // Delete and edit wired to repository
    fun deleteEntry(id: Long) {
        viewModelScope.launch { consumptionRepo.delete(id) }
    }

    fun updateEntry(entry: DiaryEntry) {
        viewModelScope.launch { consumptionRepo.update(entry) }
    }

    /** Reactive — safe across midnight. */
    val isToday: Flow<Boolean> = _selectedDate.map { it == LocalDate.now() }

    // ── Day notes ─────────────────────────────────────────────────────────────
    val dayNote: Flow<String> = _selectedDate.flatMapLatest { date ->
        notesRepo.observe(date)
    }

    fun saveNote(text: String) {
        viewModelScope.launch { notesRepo.set(_selectedDate.value, text) }
    }
}
