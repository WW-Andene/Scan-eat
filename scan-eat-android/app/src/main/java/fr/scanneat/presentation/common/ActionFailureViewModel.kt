package fr.scanneat.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared "guarded write + actionFailed snackbar" pattern, extracted after cycles
 * 5-8 independently added the identical trio (private _actionFailed
 * MutableStateFlow, public actionFailed StateFlow, clearActionFailed()) plus a
 * near-identical runCatching-wrapping launch helper to ProfileViewModel,
 * SettingsViewModel, DataViewModel, and OnboardingViewModel one at a time, each
 * to fix its own unguarded-DataStore-write crash. Centralizing here removes
 * ~6 lines of duplicated boilerplate per ViewModel with no behavior change:
 * every call site already rethrew CancellationException before setting
 * _actionFailed, so guardedLaunch does the same.
 */
abstract class ActionFailureViewModel : ViewModel() {
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed guarded write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    /** Fire-and-forget: runs [block] in viewModelScope, flags [actionFailed] on failure. */
    protected fun guardedLaunch(block: suspend () -> Unit): Job = viewModelScope.launch {
        runCatching { block() }.onFailure { e ->
            if (e is CancellationException) throw e
            _actionFailed.value = true
        }
    }

    /**
     * Suspending variant for callers that must await completion before acting
     * (e.g. OnboardingViewModel.saveMinimalProfile, which only navigates
     * onward once the save has actually succeeded). Returns whether [block]
     * completed without throwing.
     */
    protected suspend fun guardedSuspend(block: suspend () -> Unit): Boolean =
        runCatching { block() }.onFailure { e ->
            if (e is CancellationException) throw e
            _actionFailed.value = true
        }.isSuccess
}
