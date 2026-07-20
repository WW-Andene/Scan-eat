package fr.scanneat.presentation.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.data.repository.health.MedicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
//
// French spellings only until now - the app is bilingual FR/EN (see
// DrugGroup's own doc comment: labels render in the user's app language), but
// an English-locale user typing "Warfarin"/"Aspirin"/"Ibuprofen" got zero
// interaction warning, silently defeating this safety feature for exactly the
// population most likely to type English drug names. English generic-name
// variants added alongside the French ones (case-insensitive match already
// applied by detectInteractions() via .lowercase()).
private val INTERACTION_GROUPS = mapOf(
    DrugGroup.ANTICOAGULANTS to listOf(
        "warfarine", "warfarin", "coumadine", "coumadin", "acenocoumarol", "rivaroxaban", "apixaban", "dabigatran", "héparine", "heparin",
    ),
    DrugGroup.ANTIPLATELETS  to listOf("aspirine", "aspirin", "clopidogrel", "prasugrel", "ticagrelor"),
    DrugGroup.NSAIDS         to listOf(
        "ibuprofène", "ibuprofen", "naproxène", "naproxen", "kétoprofène", "ketoprofen", "diclofénac", "diclofenac", "indométacine", "indomethacin", "méloxicam", "meloxicam",
    ),
    DrugGroup.SSRI_SNRI      to listOf(
        "sertraline", "fluoxétine", "fluoxetine", "paroxétine", "paroxetine", "venlafaxine", "duloxétine", "duloxetine", "escitalopram",
    ),
    DrugGroup.MAOI           to listOf("phénelzine", "phenelzine", "tranylcypromine", "moclobémide", "moclobemide", "sélégiline", "selegiline"),
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repo: MedicationRepository,
) : ViewModel() {
    val medications: StateFlow<List<Medication>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // LocalDate.now() captured once at construction would keep observing
    // today's bucket forever if this ViewModel outlives midnight - same fix
    // HydrationViewModel/DiaryViewModel already apply. Without this, todayTaken
    // kept pointing at yesterday's log past midnight: MedicationScreen's toggle
    // does `if (takenToday != null) undoTaken(takenToday) else markTaken(m)`, so
    // a user marking today's dose taken instead found yesterday's stale entry
    // non-null and tapped into undoTaken - deleting yesterday's legitimate log
    // while today's dose never got recorded.
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    // Traitement previously had no dated "I took this" record at all - only
    // an active list + reminder schedule, unlike every other tracker Journal
    // combines. Today's taken log lets the tab itself show adherence, and
    // feeds the same event into the unified Calendar.
    val todayTaken: StateFlow<List<MedicationLogEntry>> = today.flatMapLatest { date -> repo.observeLogByDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Improvement: keyword-based interaction warnings for the active medication list.
    val interactionWarnings: StateFlow<List<InteractionWarning>> = medications
        .map { detectInteractions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New: daily adherence streak — consecutive past days where every active
    // medication had at least one log entry. Only checks the last 30 days to
    // bound the DB query; today is excluded (still in progress).
    //
    // Combined with [today] and re-subscribed via flatMapLatest (rather than a
    // one-shot repo.getLogRange() call inside a plain .map over `medications`)
    // so this recomputes whenever medication_log itself changes too - a .map
    // over `medications` alone (bound only to the medications table) never
    // re-ran when markTaken()/undoTaken() wrote to medication_log, leaving this
    // stale until something unrelated (e.g. editing a medication) forced a
    // re-emit of `medications`.
    val adherenceStreak: StateFlow<Int> = combine(medications, today) { allMeds, date -> allMeds to date }
        .flatMapLatest { (allMeds, today) ->
            val activeMeds = allMeds.filter { it.active }
            if (activeMeds.isEmpty()) return@flatMapLatest flowOf(0)
            repo.observeLogRange(today.minusDays(30), today.minusDays(1)).map { logs ->
                val logsByDate = logs.groupBy { it.date }
                var streak = 0
                var date = today.minusDays(1)
                repeat(30) {
                    val takenIds = logsByDate[date]?.map { it.medicationId }?.toSet() ?: emptySet()
                    // Only require a log for medications that actually existed on
                    // [date] - the domain Medication previously dropped createdAt
                    // entirely (see MedicationRepository.toDomain()'s matching fix),
                    // so adding any new active medication made yesterday's takenIds
                    // unable to contain it (it didn't exist yesterday), dropping the
                    // whole combined streak to 0 even with a flawless prior history.
                    val relevantMeds = activeMeds.filter { med ->
                        Instant.ofEpochMilli(med.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() <= date
                    }
                    if (relevantMeds.all { it.id in takenIds }) { streak++; date = date.minusDays(1) }
                    else return@map streak
                }
                streak
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** One day's adherence — pct is null when no active medication had been added yet by that date (excluded, not counted as a miss). */
    data class DayAdherence(val date: LocalDate, val pct: Int?)

    // adherenceStreak above only reports the current unbroken run and resets
    // to 0 on the very first miss - Weight/Activity/Hydration all have a 7-day
    // chart showing the actual week, not just a single streak counter, and
    // Medication had none. Reuses the same createdAt/relevantMeds guard as
    // adherenceStreak (see its own comment) so a medication added mid-week
    // doesn't count the days before it existed as missed doses.
    // Same medication_log-reactivity fix as adherenceStreak above - see its comment.
    val weeklyAdherence: StateFlow<List<DayAdherence>> = combine(medications, today) { allMeds, date -> allMeds to date }
        .flatMapLatest { (allMeds, today) ->
            val activeMeds = allMeds.filter { it.active }
            repo.observeLogRange(today.minusDays(6), today).map { logs ->
                val logsByDate = logs.groupBy { it.date }
                (6 downTo 0).map { i ->
                    val date = today.minusDays(i.toLong())
                    val relevantMeds = activeMeds.filter { med ->
                        Instant.ofEpochMilli(med.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() <= date
                    }
                    if (relevantMeds.isEmpty()) {
                        DayAdherence(date, null)
                    } else {
                        val takenIds = logsByDate[date]?.map { it.medicationId }?.toSet() ?: emptySet()
                        DayAdherence(date, relevantMeds.count { it.id in takenIds } * 100 / relevantMeds.size)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Every write below previously called repo's Room writes completely unguarded -
    // unlike every sibling tracker (Weight/Activity/Dashboard/MealPlan/Templates all
    // wrap theirs in runCatching), so a write failure here wasn't just silent, it
    // was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun markTaken(medication: Medication) {
        viewModelScope.launch { runCatching { repo.logTaken(medication) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    fun undoTaken(entry: MedicationLogEntry) {
        viewModelScope.launch { runCatching { repo.deleteLogEntry(entry.id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    fun save(name: String, dosage: String, scheduleNote: String, reminderOn: Boolean = false, reminderTime: String = "08:00") {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.save(name, dosage, scheduleNote, reminderOn = reminderOn, reminderTime = reminderTime) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun rename(medication: Medication, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repo.save(newName, medication.dosage, medication.scheduleNote, medication.barcode, medication.active, id = medication.id,
                    reminderOn = medication.reminderOn, reminderTime = medication.reminderTime)
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /** Toggles/updates a medication's own reminder — previously "schedule" was display-only text with no actual reminder capability. */
    fun setReminder(medication: Medication, on: Boolean, time: String) {
        viewModelScope.launch {
            runCatching {
                repo.save(medication.name, medication.dosage, medication.scheduleNote, medication.barcode, medication.active, id = medication.id,
                    reminderOn = on, reminderTime = time)
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun setActive(medication: Medication, active: Boolean) {
        viewModelScope.launch { runCatching { repo.setActive(medication, active) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    fun delete(id: String) = viewModelScope.launch {
        runCatching { repo.delete(id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }
}
