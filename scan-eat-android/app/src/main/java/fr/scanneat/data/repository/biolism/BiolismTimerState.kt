package fr.scanneat.data.repository.biolism

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Session/keto timer state and manual-HR override — delegate used by
 * [BiolismRepository], which forwards its own `timerState`/`saveTimerState`/
 * `manualHR`/`saveManualHR` members to this class so external callers see no
 * change in shape.
 */
internal class TimerStateStore(
    private val store: DataStore<Preferences>,
    private val storeData: Flow<Preferences>,
) {
    val timerState: Flow<BiolismRepository.TimerState> = storeData.map { p ->
        BiolismRepository.TimerState(
            running          = p[BiolismRepository.K_SESS_RUNNING]   ?: false,
            wallStartMs      = p[BiolismRepository.K_SESS_WALL_START] ?: 0L,
            accumulatedMs    = p[BiolismRepository.K_SESS_ACCUMULATED]?: 0L,
            ketoRunning      = p[BiolismRepository.K_KETO_RUNNING]    ?: false,
            ketoWallStartMs  = p[BiolismRepository.K_KETO_WALL_START] ?: 0L,
            ketoAccumulatedMs= p[BiolismRepository.K_KETO_ACCUMULATED]?: 0L,
            ketosisOn        = p[BiolismRepository.K_KETOSIS_ON]      ?: false,
            ketoAdapted      = p[BiolismRepository.K_KETO_ADAPTED]    ?: false,
            fastingActive    = p[BiolismRepository.K_FASTING_ACTIVE]  ?: false,
            lastMealTs       = p[BiolismRepository.K_LAST_MEAL_TS]    ?: 0L,
        )
    }.distinctUntilChanged()

    suspend fun saveTimerState(state: BiolismRepository.TimerState) = store.edit { p ->
        p[BiolismRepository.K_SESS_RUNNING]    = state.running
        p[BiolismRepository.K_SESS_WALL_START] = state.wallStartMs
        p[BiolismRepository.K_SESS_ACCUMULATED]= state.accumulatedMs
        p[BiolismRepository.K_KETO_RUNNING]    = state.ketoRunning
        p[BiolismRepository.K_KETO_WALL_START] = state.ketoWallStartMs
        p[BiolismRepository.K_KETO_ACCUMULATED]= state.ketoAccumulatedMs
        p[BiolismRepository.K_KETOSIS_ON]      = state.ketosisOn
        p[BiolismRepository.K_KETO_ADAPTED]    = state.ketoAdapted
        p[BiolismRepository.K_FASTING_ACTIVE]  = state.fastingActive
        p[BiolismRepository.K_LAST_MEAL_TS]    = state.lastMealTs
    }

    val manualHR: Flow<Int?> = storeData.map { p -> p[BiolismRepository.K_MANUAL_HR] }.distinctUntilChanged()

    suspend fun saveManualHR(bpm: Int?) = store.edit { p ->
        if (bpm != null) p[BiolismRepository.K_MANUAL_HR] = bpm else p.remove(BiolismRepository.K_MANUAL_HR)
    }
}
