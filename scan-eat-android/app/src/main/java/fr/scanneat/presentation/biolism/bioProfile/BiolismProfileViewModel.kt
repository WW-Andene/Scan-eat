package fr.scanneat.presentation.biolism.bioProfile

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiolismProfileViewModel @Inject constructor(
    private val repo: BiolismRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val profile: StateFlow<BiolismProfile> = repo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())

    // Same app-wide, persisted metric/imperial preference ProfileViewModel/
    // WeightViewModel read/write - this screen previously kept its own
    // session-only, always-metric-by-default local toggle instead, so a user
    // in imperial mode everywhere else in the app still saw cm/kg here, and
    // any preference they set on this specific screen was lost the moment
    // they navigated away instead of matching the rest of the app.
    val useImperial: StateFlow<Boolean> = prefs.useImperialWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setUseImperial(v: Boolean) {
        viewModelScope.launch { prefs.setUseImperialWeight(v) }
    }

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
    fun save(p: BiolismProfile) = viewModelScope.launch {
        runCatching { repo.saveProfile(p) }.onSuccess { _saved.value = true }.onFailure { e -> if (e is CancellationException) throw e; flagActionFailed() }
    }
    fun clearSaved() { _saved.value = false }

    fun completeOnboarding(p: BiolismProfile) = guardedLaunch { repo.saveProfile(p); repo.setOnboarded(true) }
    fun skipOnboarding() = guardedLaunch { repo.setOnboarded(true) }
}
