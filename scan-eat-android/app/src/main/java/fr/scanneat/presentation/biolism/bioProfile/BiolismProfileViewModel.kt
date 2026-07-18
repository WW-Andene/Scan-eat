package fr.scanneat.presentation.biolism.bioProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.domain.engine.biolism.BiolismProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiolismProfileViewModel @Inject constructor(
    private val repo: BiolismRepository,
    prefs: UserPreferences,
) : ViewModel() {
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val profile: StateFlow<BiolismProfile> = repo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** 0..1 fraction of profile fields that are filled — used to drive a completeness progress bar. */
    val profileCompleteness: StateFlow<Float> = profile.map { p ->
        val checks = listOf(
            p.sex != fr.scanneat.domain.engine.biolism.BiolismSex.NOT_SPECIFIED,
            p.ageYears > 0,
            p.heightCm > 0,
            p.weightKg > 0,
            p.activityId.isNotBlank(),
            p.waistCm > 0,
            p.hipCm > 0,
            p.neckCm > 0,
            // ETHNICITY_OPTIONS has no "other" id (its real opt-out choice is
            // "prefer_not") - the old sentinel could never match, so selecting
            // "Prefer not to say" always counted as a completed field.
            p.ethnicityId.isNotBlank() && p.ethnicityId != "prefer_not",
        )
        checks.count { it }.toFloat() / checks.size.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val onboarded: StateFlow<Boolean> = repo.onboarded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun save(p: BiolismProfile) = viewModelScope.launch { repo.saveProfile(p); _saved.value = true }
    fun clearSaved() { _saved.value = false }

    fun completeOnboarding(p: BiolismProfile) = viewModelScope.launch { repo.saveProfile(p); repo.setOnboarded(true) }
    fun skipOnboarding() = viewModelScope.launch { repo.setOnboarded(true) }
}
