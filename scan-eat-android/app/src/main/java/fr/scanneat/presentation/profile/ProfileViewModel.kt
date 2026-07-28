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
import kotlinx.coroutines.CancellationException
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

    // True until prefs.profile's first real DataStore-backed value has arrived -
    // before that, `profile` above is still its Profile() seed default, which
    // ProfileScreen previously rendered as-is (a brief flash of blank/default
    // fields on cold start before the real saved profile loads asynchronously).
    private val _profileLoaded = MutableStateFlow(false)
    val profileLoaded: StateFlow<Boolean> = _profileLoaded.asStateFlow()
    init {
        viewModelScope.launch { prefs.profile.first(); _profileLoaded.value = true }
    }

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

    // save() below called prefs.saveProfile()/biolismRepo's DataStore writes
    // completely unguarded - the same "one code path missing a check its sibling
    // has" pattern already fixed in DataViewModel.saveManualHR()/deleteSession()
    // (Biolism's Data tab). A DataStore I/O failure (disk full, corrupt prefs
    // file) here would crash this ViewModel's coroutine and leave the user
    // silently stuck on the Profile screen with no feedback that Save did
    // nothing, instead of surfacing a recoverable error.
    private val _actionFailed = MutableStateFlow(false)
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    // Circumferences + ethnicity previously only existed in the Métabolisme
    // profile screen (BiolismProfileScreen), invisible from Réglages > Mon
    // Profil even though they're stored in the same synced BiolismRepository
    // as the shared sex/age/height/weight/activity fields. Exposed here so
    // both screens surface the same complete set of fields.
    val biolismProfile: StateFlow<BiolismProfile> = biolismRepo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())

    // Same app-wide, persisted metric/imperial preference WeightViewModel reads/writes —
    // previously this screen ignored it entirely and always treated typed values as
    // cm/kg, so a user in imperial mode elsewhere could type a pound value here and
    // have it silently stored as kilograms.
    val useImperial: StateFlow<Boolean> = prefs.useImperialWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setUseImperial(v: Boolean) {
        viewModelScope.launch { prefs.setUseImperialWeight(v) }
    }

    /**
     * Saves the shared profile fields plus the circumference/ethnicity fields
     * (which live in BiolismRepository, disjoint DataStore keys — see
     * saveBodyMeasurements's own doc) in one coroutine. Previously these were
     * two independent viewModelScope.launch calls: _saved.value = true fired
     * as soon as the first (save's own prefs.saveProfile + clearProfileOverride)
     * finished, driving ProfileScreen's LaunchedEffect to pop the screen and
     * clear this ViewModel's scope — which could cancel the still-in-flight
     * body-measurements write, silently dropping waist/hip/neck/ethnicity edits.
     */
    fun save(profile: Profile, waistCm: Double, hipCm: Double, neckCm: Double, ethnicityId: String) {
        viewModelScope.launch {
            // Biolism's own override (sex/age/height/weight/activity - see
            // BiolismRepository.saveProfile()'s own doc comment) previously got
            // cleared unconditionally on every single save here, even one that
            // only touched allergens/diet/goal weight - a user who'd deliberately
            // set a Biolism-specific weight/activity (e.g. for a more precise
            // body-comp-aware TDEE) lost it the next time they saved anything
            // unrelated on this screen. Only clear when a field Biolism actually
            // mirrors changed.
            val before = this@ProfileViewModel.profile.value
            runCatching {
                prefs.saveProfile(profile)
                val sharedFieldsChanged = before.sex != profile.sex || before.ageYears != profile.ageYears ||
                    before.heightCm != profile.heightCm || before.weightKg != profile.weightKg ||
                    before.activityLevel != profile.activityLevel
                if (sharedFieldsChanged) biolismRepo.clearProfileOverride()
                biolismRepo.saveBodyMeasurements(waistCm, hipCm, neckCm, ethnicityId)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _actionFailed.value = true
                return@launch
            }
            _saved.value = true
        }
    }

    fun clearSaved() { _saved.value = false }
}
