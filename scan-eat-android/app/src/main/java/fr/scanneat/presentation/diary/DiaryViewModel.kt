package fr.scanneat.presentation.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.nutrition.DayNotesRepository
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.DailySummary
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.ScanSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val consumptionRepo: ConsumptionRepository,
    private val notesRepo: DayNotesRepository,
    private val customFoodRepo: CustomFoodRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    // Fix 13: selectedDate as a StateFlow — avoids stale data across midnight
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

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

    // ── Manual add: search + log ─────────────────────────────────────────────
    // Previously the only way to add a diary entry was via the barcode/photo
    // scan flow — there was no way to search and log something (a home-cooked
    // meal, a fruit, anything not barcode-scanned) directly from the Journal.
    // Same debounced search-over-observeAll() pattern as CustomFoodViewModel,
    // so results can't go stale after a save/delete the way a one-shot
    // repo.search() snapshot could.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<FoodEntry>> =
        combine(_searchQuery.debounce(200), customFoodRepo.observeAll()) { q, customs -> q to customs }
            .map { (q, customs) -> if (q.isBlank()) emptyList() else searchFoodDB(q, limit = 10, customs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun clearSearch() { _searchQuery.value = "" }

    /** Logs [entry] to the currently selected day (not always "today" — the user may be browsing a past date). */
    fun addEntry(entry: FoodEntry, portionG: Double, mealSlot: MealSlot) {
        viewModelScope.launch {
            consumptionRepo.log(
                DiaryEntry(
                    date        = _selectedDate.value,
                    mealSlot    = mealSlot,
                    productName = entry.name,
                    barcode     = null,
                    portionG    = portionG,
                    nutrition   = customFoodRepo.toProduct(entry).nutrition,
                    source      = ScanSource.MANUAL,
                )
            )
            _searchQuery.value = ""
        }
    }
}
