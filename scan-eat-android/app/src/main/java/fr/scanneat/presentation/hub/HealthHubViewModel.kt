package fr.scanneat.presentation.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.health.isLowStock
import fr.scanneat.data.repository.health.renewalDue
import fr.scanneat.data.repository.recall.RecallRepository
import fr.scanneat.data.repository.withdrawal.WithdrawalRepository
import fr.scanneat.domain.engine.withdrawal.taperStatus
import fr.scanneat.domain.engine.withdrawal.tobaccoStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Live one-line status for the Santé hub rows: today's doses, quitting progress, recalls. */
data class HealthHubStatus(
    val dosesLeftToday: Int? = null,
    val supplyAlert: Boolean = false,
    val smokeFreeDays: Int? = null,
    val taperStep: Pair<Int, Int>? = null,
    val recalledProducts: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HealthHubViewModel @Inject constructor(
    prefs: UserPreferences,
    medicationRepo: MedicationRepository,
    withdrawalRepo: WithdrawalRepository,
    recallRepo: RecallRepository,
) : ViewModel() {
    val status: StateFlow<HealthHubStatus> = prefs.activeProfileId.flatMapLatest { id ->
        val today = LocalDate.now()
        val meds = combine(medicationRepo.observeAll(id), medicationRepo.observeLogByDate(today, id)) { all, logs ->
            val scheduled = all.filter { it.active && it.isScheduledOn(today.dayOfWeek) }
            val taken = logs.groupingBy { it.medicationId }.eachCount()
            val left = scheduled.sumOf { (it.dosesPerDay - (taken[it.id] ?: 0)).coerceAtLeast(0) }
            (if (scheduled.isEmpty()) null else left) to all.any { it.active && (it.isLowStock() || it.renewalDue(today)) }
        }
        val quit = combine(withdrawalRepo.tobaccoPlan(id), withdrawalRepo.tobaccoLog(id), withdrawalRepo.lastSmoke(id), withdrawalRepo.taperPlan(id)) { plan, log, last, taper ->
            plan?.let { tobaccoStats(it, log, LocalDateTime.now(), last).smokeFreeDays } to
                taper?.let { taperStatus(it, today) }?.takeIf { it.current != null }?.let { it.stepIndex + 1 to it.stepCount }
        }
        combine(meds, quit, recallRepo.observeRecalledProducts()) { (left, supply), (free, step), recalls ->
            HealthHubStatus(left, supply, free, step, recalls.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthHubStatus())
}
