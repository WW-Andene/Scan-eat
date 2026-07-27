package fr.scanneat.presentation.biolism.tracker

import androidx.lifecycle.viewModelScope
import fr.scanneat.domain.model.MS_PER_HOUR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Ketosis / fasting toggles and manual time adjustments — extracted verbatim
// out of TrackerViewModel (same package). All were already public members;
// external callers see no change.
// ─────────────────────────────────────────────────────────────────────────────

internal fun TrackerViewModel.toggleKetosis() {
    val s = _timerState.value
    val now = System.currentTimeMillis()
    val next = if (s.ketosisOn) {
        // turning off — reset keto timer
        val ketoAcc = s.ketoAccumulatedMs + (if (s.ketoRunning && s.ketoWallStartMs > 0) now - s.ketoWallStartMs else 0L)
        s.copy(ketosisOn = false, ketoRunning = false, ketoWallStartMs = 0L, ketoAccumulatedMs = ketoAcc)
    } else {
        // turning on
        s.copy(ketosisOn = true,
               ketoRunning = s.running,
               ketoWallStartMs = if (s.running) now else 0L,
               ketoAccumulatedMs = 0L)
    }
    _timerState.value = next
    _ketoElapsedMs.value = next.ketoElapsedMs
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

internal fun TrackerViewModel.toggleKetoAdapted() {
    val s = _timerState.value
    val next = s.copy(ketoAdapted = !s.ketoAdapted)
    _timerState.value = next
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

internal fun TrackerViewModel.toggleFastingActive() {
    val s = _timerState.value
    val next = s.copy(fastingActive = !s.fastingActive)
    _timerState.value = next
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

internal fun TrackerViewModel.logMealNow() {
    val s = _timerState.value
    val next = s.copy(fastingActive = true, lastMealTs = System.currentTimeMillis())
    _timerState.value = next
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

internal fun TrackerViewModel.importRealFast() {
    val hours = realFastHours.value ?: return
    val s = _timerState.value
    val next = s.copy(fastingActive = true, lastMealTs = System.currentTimeMillis() - (hours * MS_PER_HOUR).toLong())
    _timerState.value = next
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

// ── Add time to keto/fasting timers ──────────────────────────────────────
internal fun TrackerViewModel.addKetoHours(hours: Double) {
    val s = _timerState.value
    val addMs = (hours * MS_PER_HOUR).toLong()
    val next  = s.copy(ketoAccumulatedMs = (s.ketoAccumulatedMs + addMs).coerceAtLeast(0L))
    _timerState.value = next
    _ketoElapsedMs.value = next.ketoElapsedMs
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

internal fun TrackerViewModel.addFastingHours(hours: Double) {
    val s = _timerState.value
    val deltaMs = (hours * MS_PER_HOUR).toLong()
    // Upper-bounded at "now" too — unlike addKetoHours' single coerceAtLeast(0),
    // repeatedly tapping the "-" stepper here could otherwise push lastMealTs into
    // the future, making fastingHours negative and silently blanking the badge.
    val newTs   = ((s.lastMealTs.takeIf { it > 0L } ?: System.currentTimeMillis()) - deltaMs)
        .coerceIn(0L, System.currentTimeMillis())
    val next    = s.copy(fastingActive = true, lastMealTs = newTs)
    _timerState.value = next
    viewModelScope.launch { runCatching { repo.saveTimerState(next) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}
