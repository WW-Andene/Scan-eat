package fr.scanneat.presentation.symptom

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.symptom.SymptomEntry
import fr.scanneat.data.repository.symptom.SymptomRepository
import fr.scanneat.data.repository.symptom.SymptomType
import fr.scanneat.domain.engine.symptom.FoodCorrelation
import fr.scanneat.domain.engine.symptom.symptomFoodCorrelations
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

// Correlation window - wide enough to have real symptom-day/non-symptom-day
// contrast without an unbounded query as the journal grows for years.
private const val CORRELATION_WINDOW_DAYS = 60L

/**
 * User-requested: a symptom journal (bloating, energy, sleep...) correlated
 * against what was actually logged in the diary that same day - see
 * SymptomEntity/symptomFoodCorrelations' own doc comments.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SymptomViewModel @Inject constructor(
    private val repo: SymptomRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {

    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val entries: StateFlow<List<SymptomEntry>> = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedType = MutableStateFlow<SymptomType?>(null)
    val selectedType: StateFlow<SymptomType?> = _selectedType.asStateFlow()
    fun setSelectedType(type: SymptomType?) { _selectedType.value = type }

    /** Distinct symptom types actually present in this profile's journal - the
     *  correlation filter chip row only needs to offer types with real data. */
    val loggedTypes: StateFlow<List<SymptomType>> = entries
        .map { list -> list.map { it.type }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val correlations: StateFlow<List<FoodCorrelation>> = combine(entries, _selectedType, activeProfileId) { list, type, id ->
        Triple(list, type, id)
    }.flatMapLatest { (list, type, id) ->
        val relevant = if (type != null) list.filter { it.type == type } else list
        val dates = relevant.map { it.date }.toSet()
        if (dates.isEmpty()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val today = LocalDate.now()
            consumptionRepo.observeRange(today.minusDays(CORRELATION_WINDOW_DAYS), today, id)
                .map { diaryEntries -> symptomFoodCorrelations(dates, diaryEntries) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(date: LocalDate, type: SymptomType, customLabel: String, severity: Int, notes: String) {
        guardedLaunch { repo.add(date, type, customLabel, severity, notes, activeProfileId.value) }
    }

    fun delete(id: String) {
        guardedLaunch { repo.delete(id) }
    }
}
