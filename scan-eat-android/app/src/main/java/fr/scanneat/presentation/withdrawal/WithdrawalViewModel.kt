package fr.scanneat.presentation.withdrawal

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.withdrawal.WithdrawalRepository
import fr.scanneat.domain.engine.withdrawal.TaperPlan
import fr.scanneat.domain.engine.withdrawal.TaperStep
import fr.scanneat.domain.engine.withdrawal.TobaccoPlan
import fr.scanneat.domain.engine.withdrawal.TobaccoStats
import fr.scanneat.domain.engine.withdrawal.tobaccoStats
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WithdrawalViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val repo: WithdrawalRepository,
) : ActionFailureViewModel() {
    private val profileId = prefs.activeProfileId

    /** Ticks every minute so the milestone clock moves while the screen is open. */
    val now: StateFlow<LocalDateTime> = flow { while (true) { emit(LocalDateTime.now()); delay(60_000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDateTime.now())

    val tobaccoPlan: StateFlow<TobaccoPlan?> = profileId.flatMapLatest { repo.tobaccoPlan(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tobaccoStats: StateFlow<TobaccoStats?> = profileId.flatMapLatest { id ->
        combine(repo.tobaccoPlan(id), repo.tobaccoLog(id), repo.lastSmoke(id), now) { plan, log, last, n ->
            plan?.let { tobaccoStats(it, log, n, last) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val taperPlan: StateFlow<TaperPlan?> = profileId.flatMapLatest { repo.taperPlan(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startTobacco(baselinePerDay: Int, packPrice: Double, perPack: Int, targetPerDay: Int?) = guardedLaunch {
        repo.setTobaccoPlan(profileId.first(), TobaccoPlan(LocalDate.now(), baselinePerDay, packPrice, perPack, targetPerDay))
    }
    fun stopTobacco() = guardedLaunch { repo.setTobaccoPlan(profileId.first(), null) }
    fun cigarette(delta: Int) = guardedLaunch { repo.addCigarette(profileId.first(), delta) }

    fun saveTaper(name: String, steps: List<TaperStep>, note: String) = guardedLaunch {
        repo.setTaperPlan(profileId.first(), TaperPlan(name.trim(), steps, note.trim()))
    }
    fun deleteTaper() = guardedLaunch { repo.setTaperPlan(profileId.first(), null) }
}
