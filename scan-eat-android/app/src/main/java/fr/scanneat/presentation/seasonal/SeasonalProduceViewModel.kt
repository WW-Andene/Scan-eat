package fr.scanneat.presentation.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.domain.engine.nutrition.SEASONAL_PRODUCE_DB
import fr.scanneat.domain.engine.nutrition.SeasonalProduce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SeasonalProduceViewModel @Inject constructor(
    prefs: UserPreferences,
) : ViewModel() {

    // In-app language, not Locale.getDefault() - same fix every date-heavy
    // sibling screen (Weight/Expenses/Diary/...) already applies.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    /** 1 (January) .. 12 (December) - the current calendar month by default,
     *  overridable so a user can browse other months' seasonal produce. */
    val currentMonth: Int = LocalDate.now().monthValue
    private val _selectedMonth = MutableStateFlow(currentMonth)
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

    fun selectMonth(month: Int) { _selectedMonth.value = month.coerceIn(1, 12) }

    fun inSeason(month: Int): List<SeasonalProduce> {
        val isFrench = language.value == "fr"
        return SEASONAL_PRODUCE_DB.filter { month in it.months }
            .sortedBy { if (isFrench) it.nameFr else it.nameEn }
    }
}
