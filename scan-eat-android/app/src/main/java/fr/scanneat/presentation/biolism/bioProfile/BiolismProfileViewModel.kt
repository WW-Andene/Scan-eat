package fr.scanneat.presentation.biolism.bioProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.domain.engine.biolism.BiolismProfile
import kotlinx.coroutines.CancellationException
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

    // save()/completeOnboarding()/skipOnboarding() previously called repo's DataStore
    // writes completely unguarded - unlike every sibling tracker ViewModel (Weight/
    // Activity/Dashboard/MealPlan/Templates all wrap theirs in runCatching), so a
    // write failure here wasn't just silent, it was an uncaught exception that would
    // crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun save(p: BiolismProfile) = viewModelScope.launch {
        runCatching { repo.saveProfile(p) }.onSuccess { _saved.value = true }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }
    fun clearSaved() { _saved.value = false }

    fun completeOnboarding(p: BiolismProfile) = viewModelScope.launch {
        runCatching { repo.saveProfile(p); repo.setOnboarded(true) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }
    fun skipOnboarding() = viewModelScope.launch {
        runCatching { repo.setOnboarded(true) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }
}
