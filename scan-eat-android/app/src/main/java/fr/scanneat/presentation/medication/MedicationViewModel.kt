package fr.scanneat.presentation.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.data.repository.health.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Drug class used for keyword-based interaction detection — labelled/localized in MedicationScreen, not here (a ViewModel has no stringResource()). */
enum class DrugGroup {
    ANTICOAGULANTS, ANTIPLATELETS, NSAIDS, SSRI_SNRI, MAOI
}

/** A detected medication-interaction risk, safety-relevant so it must render in the user's own app language, not a hardcoded one. */
sealed class InteractionWarning {
    data class GroupDuplicate(val group: DrugGroup) : InteractionWarning()
    data object AnticoagNsaid : InteractionWarning()
    data object SsriMaoi : InteractionWarning()
}

// Known high-risk keyword patterns: maps a drug class to a set of name substrings.
// If two or more medications from the same risk group are active simultaneously,
// an interaction warning is surfaced.
private val INTERACTION_GROUPS = mapOf(
    DrugGroup.ANTICOAGULANTS to listOf("warfarine", "coumadine", "acenocoumarol", "rivaroxaban", "apixaban", "dabigatran", "héparine"),
    DrugGroup.ANTIPLATELETS  to listOf("aspirine", "clopidogrel", "prasugrel", "ticagrelor"),
    DrugGroup.NSAIDS         to listOf("ibuprofène", "naproxène", "kétoprofène", "diclofénac", "indométacine", "méloxicam"),
    DrugGroup.SSRI_SNRI      to listOf("sertraline", "fluoxétine", "paroxétine", "venlafaxine", "duloxétine", "escitalopram"),
    DrugGroup.MAOI           to listOf("phénelzine", "tranylcypromine", "moclobémide", "sélégiline"),
)

private fun detectInteractions(meds: List<Medication>): List<InteractionWarning> {
    val activeNames = meds.filter { it.active }.map { it.name.lowercase() }
    val warnings = mutableListOf<InteractionWarning>()
    // Same-group duplicates (e.g., two anticoagulants)
    for ((group, keywords) in INTERACTION_GROUPS) {
        val matches = activeNames.count { name -> keywords.any { name.contains(it) } }
        if (matches >= 2) warnings += InteractionWarning.GroupDuplicate(group)
    }
    // Anticoagulant + NSAID cross-group risk
    val hasAnticoag = activeNames.any { name -> INTERACTION_GROUPS[DrugGroup.ANTICOAGULANTS]!!.any { name.contains(it) } }
    val hasAin      = activeNames.any { name -> INTERACTION_GROUPS[DrugGroup.NSAIDS]!!.any { name.contains(it) } }
    if (hasAnticoag && hasAin) warnings += InteractionWarning.AnticoagNsaid
    // SSRI/SNRI + MAOI serotonin syndrome risk
    val hasSsri = activeNames.any { name -> INTERACTION_GROUPS[DrugGroup.SSRI_SNRI]!!.any { name.contains(it) } }
    val hasMaoi = activeNames.any { name -> INTERACTION_GROUPS[DrugGroup.MAOI]!!.any { name.contains(it) } }
    if (hasSsri && hasMaoi) warnings += InteractionWarning.SsriMaoi
    return warnings
}

@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repo: MedicationRepository,
) : ViewModel() {
    val medications: StateFlow<List<Medication>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Traitement previously had no dated "I took this" record at all - only
    // an active list + reminder schedule, unlike every other tracker Journal
    // combines. Today's taken log lets the tab itself show adherence, and
    // feeds the same event into the unified Calendar.
    val todayTaken: StateFlow<List<MedicationLogEntry>> = repo.observeLogByDate(LocalDate.now())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Improvement: keyword-based interaction warnings for the active medication list.
    val interactionWarnings: StateFlow<List<InteractionWarning>> = medications
        .map { detectInteractions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New: daily adherence streak — consecutive past days where every active
    // medication had at least one log entry. Only checks the last 30 days to
    // bound the DB query; today is excluded (still in progress).
    val adherenceStreak: StateFlow<Int> = medications
        .map { allMeds ->
            val activeMeds = allMeds.filter { it.active }
            if (activeMeds.isEmpty()) return@map 0
            val today = LocalDate.now()
            val logs = repo.getLogRange(today.minusDays(30), today.minusDays(1))
            val logsByDate = logs.groupBy { it.date }
            var streak = 0
            var date = today.minusDays(1)
            repeat(30) {
                val takenIds = logsByDate[date]?.map { it.medicationId }?.toSet() ?: emptySet()
                if (activeMeds.all { it.id in takenIds }) { streak++; date = date.minusDays(1) }
                else return@map streak
            }
            streak
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markTaken(medication: Medication) {
        viewModelScope.launch { repo.logTaken(medication) }
    }

    fun undoTaken(entry: MedicationLogEntry) {
        viewModelScope.launch { repo.deleteLogEntry(entry.id) }
    }

    fun save(name: String, dosage: String, scheduleNote: String, reminderOn: Boolean = false, reminderTime: String = "08:00") {
        if (name.isBlank()) return
        viewModelScope.launch { repo.save(name, dosage, scheduleNote, reminderOn = reminderOn, reminderTime = reminderTime) }
    }

    fun rename(medication: Medication, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repo.save(newName, medication.dosage, medication.scheduleNote, medication.barcode, medication.active, id = medication.id,
                reminderOn = medication.reminderOn, reminderTime = medication.reminderTime)
        }
    }

    /** Toggles/updates a medication's own reminder — previously "schedule" was display-only text with no actual reminder capability. */
    fun setReminder(medication: Medication, on: Boolean, time: String) {
        viewModelScope.launch {
            repo.save(medication.name, medication.dosage, medication.scheduleNote, medication.barcode, medication.active, id = medication.id,
                reminderOn = on, reminderTime = time)
        }
    }

    fun setActive(medication: Medication, active: Boolean) {
        viewModelScope.launch { repo.setActive(medication, active) }
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
}
