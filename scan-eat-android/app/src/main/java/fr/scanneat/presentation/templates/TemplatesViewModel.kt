package fr.scanneat.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.ConsumptionRepository
import fr.scanneat.data.repository.MealTemplate
import fr.scanneat.data.repository.MealTemplateRepository
import fr.scanneat.domain.model.MealSlot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repo: MealTemplateRepository,
    private val consumptionRepo: ConsumptionRepository,
) : ViewModel() {
    val templates: StateFlow<List<MealTemplate>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun logTemplate(template: MealTemplate, date: LocalDate = LocalDate.now(), mealOverride: MealSlot? = null) {
        viewModelScope.launch {
            repo.expand(template, date, mealOverride).forEach { consumptionRepo.log(it) }
        }
    }
}
