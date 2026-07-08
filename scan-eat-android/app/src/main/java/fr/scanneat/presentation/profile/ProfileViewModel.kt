package fr.scanneat.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.domain.engine.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefs: UserPreferences,
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

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun save(profile: Profile) {
        viewModelScope.launch {
            prefs.saveProfile(profile)
            _saved.value = true
        }
    }

    fun clearSaved() { _saved.value = false }
}
