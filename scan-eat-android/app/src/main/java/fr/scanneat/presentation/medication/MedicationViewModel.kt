package fr.scanneat.presentation.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.data.repository.health.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

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
