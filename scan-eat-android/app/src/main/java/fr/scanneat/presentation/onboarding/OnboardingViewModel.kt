package fr.scanneat.presentation.onboarding

import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: UserPreferences) : ActionFailureViewModel() {

    /** Where the screen navigates once onboarding finishes - see OnboardingScreen's single LaunchedEffect. */
    enum class Exit { SCAN, PROFILE }

    // Previously two independent signals (a `done` Boolean plus the "Complete
    // profile" button calling onGoToProfile() directly in the same onClick) -
    // both an onDone-observing LaunchedEffect and the direct callback fired off
    // their own navigate() call for the same tap, racing to decide the
    // post-onboarding destination. A single nullable Exit is now the only
    // source of truth: exactly one navigation happens, driven by one observer.
    private val _exit = MutableStateFlow<Exit?>(null)
    val exit: StateFlow<Exit?> = _exit.asStateFlow()

    // Every DataStore write below previously ran completely unguarded - the same
    // "one code path missing a check its sibling has" pattern this loop keeps
    // finding elsewhere (WeightViewModel/ActivityViewModel, DataViewModel,
    // ProfileViewModel, SettingsViewModel). Here it was worse: saveMinimalProfile
    // is a suspend fun awaited directly inside OnboardingScreen's
    // `rememberCoroutineScope().launch { }` with no try/catch at the call site
    // either, so a DataStore I/O failure (disk full, corrupt prefs file) during
    // onboarding - the very first screen a new user ever sees - would propagate
    // as an uncaught exception and crash the whole app instead of surfacing a
    // recoverable error.
    // actionFailed/clearActionFailed()/guardedLaunch/guardedSuspend now come
    // from ActionFailureViewModel (see that file's doc comment) instead of
    // being redefined here.

    fun setMode(mode: ApiMode) = guardedLaunch { prefs.setApiMode(mode) }
    // Trimmed to match SettingsViewModel.saveApiKey/saveServerUrl — an accidental
    // leading/trailing space (easy to pick up on a copy-paste from elsewhere) was
    // previously stored verbatim here, silently making the Groq key or server URL
    // invalid despite looking correct in the input field.
    fun setApiKey(key: String) = guardedLaunch { prefs.setGroqApiKey(key.trim()) }
    fun setServerUrl(url: String) = guardedLaunch { prefs.setServerUrl(url.trim()) }
    fun skipApiSetup() { /* no key/server set — barcode-only OFF lookups still work */ }

    // Onboarding previously never asked for sex/age/height/weight at all - only an
    // optional post-onboarding screen did, and hasMinimalProfile() (PersonalScoreEngine)
    // requires all four before dailyTargets()/PersonalScoreEngine ever compute anything.
    // A user who tapped "Skip" got zero personalized targets and zero personal score
    // indefinitely. suspendable, not fire-and-forget: the caller awaits this before
    // calling finish() so the profile is guaranteed saved before navigation/scope teardown.
    // Returns whether the save actually succeeded - callers now only advance
    // (finish()) when true, instead of unconditionally continuing even after a
    // silently-swallowed write failure.
    suspend fun saveMinimalProfile(sex: Sex, ageYears: Int?, heightCm: Double?, weightKg: Double?, activityLevel: ActivityLevel, goal: Goal): Boolean =
        guardedSuspend {
            prefs.saveProfile(Profile(sex = sex, ageYears = ageYears, heightCm = heightCm, weightKg = weightKg, activityLevel = activityLevel, goal = goal))
        }

    fun finish(goToProfile: Boolean = false) {
        guardedLaunch { prefs.setOnboardingComplete(true) }
        _exit.value = if (goToProfile) Exit.PROFILE else Exit.SCAN
    }
}
