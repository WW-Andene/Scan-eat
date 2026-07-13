package fr.scanneat.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val biolismRepo: BiolismRepository,
) : ViewModel() {

    val profile: StateFlow<Profile> = prefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())

    // Derived metrics — recomputed whenever profile changes
    val bmiValue: StateFlow<Double?> = profile.map { bmi(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tdee: StateFlow<Double?> = profile.map { tdeeKcal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bmiCat: StateFlow<BmiCategory?> = bmiValue.map { bmiCategory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tdeeAtGoalWeight: StateFlow<Double?> = profile.map { p ->
        val gw = p.goalWeightKg ?: return@map null
        tdeeKcal(p.copy(weightKg = gw))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    // Circumferences + ethnicity previously only existed in the Métabolisme
    // profile screen (BiolismProfileScreen), invisible from Réglages > Mon
    // Profil even though they're stored in the same synced BiolismRepository
    // as the shared sex/age/height/weight/activity fields. Exposed here so
    // both screens surface the same complete set of fields.
    val biolismProfile: StateFlow<BiolismProfile> = biolismRepo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())

    fun save(profile: Profile) {
        viewModelScope.launch {
            prefs.saveProfile(profile)
            biolismRepo.clearProfileOverride()
            _saved.value = true
        }
    }

    /** Saves only the circumference/ethnicity fields — sex/age/height/weight/activity keep following [profile] via the shared BiolismRepository sync. */
    fun saveBodyMeasurements(waistCm: Double, hipCm: Double, neckCm: Double, ethnicityId: String) {
        viewModelScope.launch {
            biolismRepo.saveBodyMeasurements(waistCm, hipCm, neckCm, ethnicityId)
        }
    }

    fun clearSaved() { _saved.value = false }
}
