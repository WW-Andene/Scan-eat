package fr.scanneat.presentation.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.recall.RecallRepository
import fr.scanneat.domain.engine.health.FastingSafety
import fr.scanneat.domain.engine.health.fastingSafety
import fr.scanneat.domain.model.Profile
import fr.scanneat.presentation.medication.InteractionWarning
import fr.scanneat.presentation.medication.detectInteractions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class MySafetyState(
    val profile: Profile = Profile(),
    val activeMedications: List<String> = emptyList(),
    val interactions: List<InteractionWarning> = emptyList(),
    val recalledCount: Int = 0,
    val fasting: FastingSafety = FastingSafety(false, emptyList()),
)

/** Roadmap D9: everything safety-related about the active profile, in one place. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MySafetyViewModel @Inject constructor(
    prefs: UserPreferences,
    medicationRepo: MedicationRepository,
    recallRepo: RecallRepository,
) : ViewModel() {
    private val meds = prefs.activeProfileId.flatMapLatest { medicationRepo.observeAll(it) }
        .map { list -> list.filter { it.active }.map { it.name } }

    val state: StateFlow<MySafetyState> = combine(prefs.profile, meds, recallRepo.observeRecalledProducts()) { profile, names, recalled ->
        MySafetyState(
            profile = profile,
            activeMedications = names,
            interactions = detectInteractions(names),
            recalledCount = recalled.size,
            fasting = fastingSafety(profile),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MySafetyState())
}
