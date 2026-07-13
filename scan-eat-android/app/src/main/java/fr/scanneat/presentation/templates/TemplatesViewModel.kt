package fr.scanneat.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.planning.MealTemplate
import fr.scanneat.data.repository.planning.MealTemplateRepository
import fr.scanneat.data.repository.planning.TemplateItem
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

    fun create(name: String, meal: MealSlot) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.save(name, meal, items = emptyList<TemplateItem>()) }
    }

    fun rename(template: MealTemplate, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repo.save(newName, template.meal, template.items, id = template.id) }
    }

    fun logTemplate(template: MealTemplate, date: LocalDate = LocalDate.now(), mealOverride: MealSlot? = null) {
        viewModelScope.launch {
            consumptionRepo.logAll(repo.expand(template, date, mealOverride))
        }
    }
}
