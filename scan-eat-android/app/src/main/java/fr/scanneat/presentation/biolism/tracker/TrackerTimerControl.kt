package fr.scanneat.presentation.biolism.tracker

import androidx.lifecycle.viewModelScope
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Timer control — extracted verbatim out of TrackerViewModel (same package) so
// the start/pause/reset/ticker state machine lives together. TrackerViewModel's
// startOrPause()/reset()/onCleared() call these extension functions directly;
// external callers see no change since these were already private/public members.
// ─────────────────────────────────────────────────────────────────────────────

internal fun TrackerViewModel.startOrPause() {
    val s = _timerState.value
    if (s.running) pauseSession(s) else startSession(s)
}

private fun TrackerViewModel.startSession(s: TimerState) {
    val now = System.currentTimeMillis()
    val next = s.copy(
        running       = true,
        wallStartMs   = now,
        ketoRunning   = s.ketosisOn,
        ketoWallStartMs = if (s.ketosisOn) now else s.ketoWallStartMs,
    )
    _timerState.value = next
    _saved.value = false
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    startTicker()
}

private fun TrackerViewModel.pauseSession(s: TimerState) {
    val now = System.currentTimeMillis()
    val accMs     = s.accumulatedMs + (if (s.wallStartMs > 0) now - s.wallStartMs else 0L)
    val ketoAccMs = s.ketoAccumulatedMs + (if (s.ketoRunning && s.ketoWallStartMs > 0) now - s.ketoWallStartMs else 0L)
    val next = s.copy(running = false, wallStartMs = 0L, accumulatedMs = accMs,
                      ketoRunning = false, ketoWallStartMs = 0L, ketoAccumulatedMs = ketoAccMs)
    _timerState.value = next
    _elapsedMs.value    = accMs
    _ketoElapsedMs.value = ketoAccMs
    stopTicker()
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

internal fun TrackerViewModel.reset() {
    stopTicker()
    val next = _timerState.value.copy(
        running = false, wallStartMs = 0L, accumulatedMs = 0L,
        ketoRunning = false, ketoWallStartMs = 0L, ketoAccumulatedMs = 0L,
    )
    _timerState.value = next
    _elapsedMs.value = 0L
    _ketoElapsedMs.value = 0L
    _saved.value = false
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

internal fun TrackerViewModel.startTicker() {
    tickJob?.cancel()
    tickJob = viewModelScope.launch {
        while (isActive) {
            val now = System.currentTimeMillis()
            val s   = _timerState.value
            val wall     = if (s.wallStartMs > 0) now - s.wallStartMs else 0L
            val ketoWall = if (s.ketoRunning && s.ketoWallStartMs > 0) now - s.ketoWallStartMs else 0L
            _elapsedMs.value     = s.accumulatedMs + wall
            _ketoElapsedMs.value = s.ketoAccumulatedMs + ketoWall
            delay(100L)  // 10 fps — smooth enough for a display, battery-friendly
        }
    }
}

internal fun TrackerViewModel.stopTicker() { tickJob?.cancel(); tickJob = null }
