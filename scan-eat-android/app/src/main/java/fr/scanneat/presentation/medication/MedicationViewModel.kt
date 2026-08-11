package fr.scanneat.presentation.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.health.WeightRepository
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
    // New: anticoagulant + antiplatelet (e.g. warfarin + aspirin) is itself a
    // well-known major bleeding-risk combo, distinct from same-group duplicates
    // and from the anticoagulant+NSAID warning already covered above - this
    // detector had anticoagulant/antiplatelet keyword lists sitting right next
    // to each other with only their same-group and anticoag+NSAID pairings
    // ever cross-checked, silently missing the single most common real-world
    // instance of this exact risk class (someone on warfarin also taking
    // daily low-dose aspirin).
    data object AnticoagAntiplatelet : InteractionWarning()
    // NSAID + antiplatelet (e.g. ibuprofen + aspirin) is itself a well-known
    // additional bleeding-risk combo, distinct from the anticoagulant-based
    // pairings above - this detector already had both group's keyword lists
    // but never cross-checked NSAID against antiplatelet directly, silently
    // missing this equally common real-world instance (someone taking daily
    // low-dose aspirin who also reaches for ibuprofen for pain).
    data object NsaidAntiplatelet : InteractionWarning()
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
// Accent-normalized (see fr.scanneat.domain.engine.scoring.normalizeForMatching) so
// "indometacine" typed without its accent still matches "indométacine" - a plain
// .lowercase() comparison was silently missing this on mobile keyboards, unlike
// every other name-matching lookup in the app (GroceryList, PairingsDb, AdditivesDb,
// MedicationLookupDb, NonConsumableLookupDb, FoodDb all normalize accents already).
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
).mapValues { (_, keywords) -> keywords.map { fr.scanneat.domain.engine.scoring.normalizeForMatching(it) } }

private fun detectInteractions(meds: List<Medication>): List<InteractionWarning> {
    val activeNames = meds.filter { it.active }.map { fr.scanneat.domain.engine.scoring.normalizeForMatching(it.name) }
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
    // Anticoagulant + antiplatelet cross-group bleeding risk (e.g. warfarin + aspirin) -
    // see InteractionWarning.AnticoagAntiplatelet's own doc comment.
    val hasAntiplatelet = activeNames.any { name -> INTERACTION_GROUPS[DrugGroup.ANTIPLATELETS]!!.any { name.contains(it) } }
    if (hasAnticoag && hasAntiplatelet) warnings += InteractionWarning.AnticoagAntiplatelet
    // NSAID + antiplatelet cross-group bleeding risk (e.g. ibuprofen + aspirin) -
    // see InteractionWarning.NsaidAntiplatelet's own doc comment.
    if (hasAin && hasAntiplatelet) warnings += InteractionWarning.NsaidAntiplatelet
    // SSRI/SNRI + MAOI serotonin syndrome risk
    val hasSsri = activeNames.any { name -> INTERACTION_GROUPS[DrugGroup.SSRI_SNRI]!!.any { name.contains(it) } }
    val hasMaoi = activeNames.any { name -> INTERACTION_GROUPS[DrugGroup.MAOI]!!.any { name.contains(it) } }
    if (hasSsri && hasMaoi) warnings += InteractionWarning.SsriMaoi
    return warnings
}

/**
 * Was this medication active on [date], independent of its CURRENT `active`
 * flag - i.e. it already existed (createdAt <= date) and hadn't been
 * deactivated yet as of that date (deactivatedAt is null, or its own date is
 * after [date]). Used by adherenceStreak/weeklyAdherence so deactivating a
 * medication only affects future adherence denominators, not past ones.
 */
private fun Medication.wasActiveOn(date: LocalDate): Boolean {
    val createdDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val deactivatedDate = deactivatedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    return createdDate <= date && (deactivatedDate == null || deactivatedDate > date)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicationViewModel @Inject constructor(
    private val repo: MedicationRepository,
    private val hydrationRepo: HydrationRepository,
    private val weightRepo: WeightRepository,
    private val prefs: fr.scanneat.data.local.prefs.UserPreferences,
) : ViewModel() {
    // R&D audit finding, phase 2: profileId was dead scaffolding until
    // multi-profile support made it real.
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val medications: StateFlow<List<Medication>> = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // In-app language (Settings) can differ from the device locale - every
    // sibling date-heavy screen already threads this through instead of
    // Locale.getDefault(); MedicationReminderDialog's day-of-week picker needs
    // it to render short day names in the right language.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

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
    val todayTaken: StateFlow<List<MedicationLogEntry>> = combine(today, activeProfileId) { date, id -> date to id }
        .flatMapLatest { (date, id) -> repo.observeLogByDate(date, id) }
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
    val adherenceStreak: StateFlow<Int> = combine(medications, today, activeProfileId) { allMeds, date, id -> Triple(allMeds, date, id) }
        .flatMapLatest { (allMeds, today, id) ->
            if (allMeds.isEmpty()) return@flatMapLatest flowOf(0)
            repo.observeLogRange(today.minusDays(30), today.minusDays(1), id).map { logs ->
                val logsByDate = logs.groupBy { it.date }
                var streak = 0
                var date = today.minusDays(1)
                repeat(30) {
                    val takenIds = logsByDate[date]?.map { it.medicationId }?.toSet() ?: emptySet()
                    // Was `activeMeds.filter { existed on [date] }`, i.e. filtered by
                    // the CURRENT `active` flag first - deactivating a medication (not
                    // deleting it) retroactively dropped it out of every past day's
                    // relevantMeds too, silently erasing its real historical misses/
                    // successes instead of freezing them at the deactivation date.
                    // wasActiveOn() checks createdAt/deactivatedAt against [date]
                    // itself, independent of the medication's current active state.
                    val relevantMeds = allMeds.filter { med -> med.wasActiveOn(date) }
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
    val weeklyAdherence: StateFlow<List<DayAdherence>> = combine(medications, today, activeProfileId) { allMeds, date, id -> Triple(allMeds, date, id) }
        .flatMapLatest { (allMeds, today, id) ->
            repo.observeLogRange(today.minusDays(6), today, id).map { logs ->
                val logsByDate = logs.groupBy { it.date }
                (6 downTo 0).map { i ->
                    val date = today.minusDays(i.toLong())
                    // Same wasActiveOn() fix as adherenceStreak above - see its comment.
                    val relevantMeds = allMeds.filter { med -> med.wasActiveOn(date) }
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

    // R&D audit finding: Medication had zero cross-reference into Weight either.
    // Purely descriptive (weight entries before vs. after this medication's
    // createdAt), never a causal claim - MedicationScreen frames this as
    // informational only, same discipline HealthConditionGuidanceDb.kt uses for
    // never overclaiming a nutrition-outcome link. Requires at least one real
    // weight entry logged before AND after createdAt (a single post-start entry
    // alone can't show a "since" delta) - medication id -> (deltaKg, fromKg, toKg).
    val weightDeltaSinceStart: StateFlow<Map<String, Triple<Double, Double, Double>>> =
        combine(medications, activeProfileId.flatMapLatest { id -> weightRepo.observeAll(id) }) { meds, weights ->
            if (weights.size < 2) return@combine emptyMap()
            val sorted = weights.sortedBy { it.date }
            meds.filter { it.active }.mapNotNull { med ->
                val startDate = Instant.ofEpochMilli(med.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                val before = sorted.lastOrNull { it.date <= startDate } ?: sorted.firstOrNull()
                val after = sorted.lastOrNull()
                if (before == null || after == null || before.date >= after.date) return@mapNotNull null
                med.id to Triple(after.weightKg - before.weightKg, before.weightKg, after.weightKg)
            }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Every write below previously called repo's Room writes completely unguarded -
    // unlike every sibling tracker (Weight/Activity/Dashboard/MealPlan/Templates all
    // wrap theirs in runCatching), so a write failure here wasn't just silent, it
    // was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    // R&D audit finding: Medication had zero cross-reference into Hydration or
    // Weight. Standard medical guidance for most oral medication is to take it
    // with a full glass of water - marking a dose taken now also credits one
    // Hydration glass (HYD_GLASS_ML, same 250 mL convention the Hydration tab
    // itself uses), easily correctable via Hydration's own -1 glass control if
    // it over-counts for a given user. Only on the forward action (not
    // undoTaken()) - removing a "taken" log doesn't mean the water wasn't
    // actually drunk, same "additive, not reversible" convention already used
    // for Activity's hydration bonus.
    fun markTaken(medication: Medication) {
        viewModelScope.launch {
            runCatching {
                repo.logTaken(medication, profileId = activeProfileId.value)
                hydrationRepo.addGlass(profileId = activeProfileId.value)
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun undoTaken(entry: MedicationLogEntry) {
        viewModelScope.launch { runCatching { repo.deleteLogEntry(entry.id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    fun save(name: String, dosage: String, scheduleNote: String, reminderOn: Boolean = false, reminderTime: String = "08:00") {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.save(name, dosage, scheduleNote, reminderOn = reminderOn, reminderTime = reminderTime, profileId = activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /** Edits an existing medication's name/dosage/schedule note in place — previously the
     *  only mutations available after creation were rename(), setReminder(), and delete();
     *  fixing a dosage typo or a schedule-note change required deleting and recreating the
     *  medication, losing its adherence-log association (MedicationLogEntry references it
     *  by id) and its reminder settings in the process. */
    fun edit(medication: Medication, name: String, dosage: String, scheduleNote: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repo.save(name, dosage, scheduleNote, medication.barcode, medication.active, id = medication.id,
                    reminderOn = medication.reminderOn, reminderTime = medication.reminderTime, profileId = activeProfileId.value,
                    deactivatedAt = medication.deactivatedAt,
                    scheduleDaysMask = medication.scheduleDaysMask, extraReminderTimes = medication.extraReminderTimes)
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /**
     * Toggles/updates a medication's own reminder - previously "schedule" was
     * display-only text with no actual reminder capability, and later only a
     * single daily time with no day-of-week/multiple-doses-per-day support.
     * [daysMask] 0 = every day (see Medication.isScheduledOn); [extraTimes] is
     * every additional dose time beyond [time] itself (slot 0).
     */
    fun setReminder(medication: Medication, on: Boolean, time: String, daysMask: Int = 0, extraTimes: List<String> = emptyList()) {
        viewModelScope.launch {
            runCatching {
                repo.save(medication.name, medication.dosage, medication.scheduleNote, medication.barcode, medication.active, id = medication.id,
                    reminderOn = on, reminderTime = time, profileId = activeProfileId.value, deactivatedAt = medication.deactivatedAt,
                    scheduleDaysMask = daysMask, extraReminderTimes = extraTimes.joinToString(","))
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun setActive(medication: Medication, active: Boolean) {
        viewModelScope.launch { runCatching { repo.setActive(medication, active, activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    // Same undo-delete pattern as DiaryViewModel/WeightViewModel/ScanHistoryViewModel -
    // snapshots the row right before deleting it so a snackbar "Undo" can restore it.
    // repo.save(id = ...) re-upserts the exact same row (same id, so its
    // MedicationLogEntry history stays associated) rather than creating a new one.
    private var lastDeleted: Medication? = null

    fun delete(id: String) {
        val entry = medications.value.firstOrNull { it.id == id }
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onSuccess { lastDeleted = entry }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun undoDelete() {
        val entry = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            runCatching {
                repo.save(entry.name, entry.dosage, entry.scheduleNote, entry.barcode, entry.active, id = entry.id,
                    reminderOn = entry.reminderOn, reminderTime = entry.reminderTime, profileId = activeProfileId.value,
                    deactivatedAt = entry.deactivatedAt,
                    scheduleDaysMask = entry.scheduleDaysMask, extraReminderTimes = entry.extraReminderTimes)
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
