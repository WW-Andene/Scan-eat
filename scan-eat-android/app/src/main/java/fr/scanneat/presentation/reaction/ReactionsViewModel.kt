package fr.scanneat.presentation.reaction

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.reaction.AllergicReaction
import fr.scanneat.data.repository.reaction.AllergicReactionRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.reaction.SharedSuspect
import fr.scanneat.domain.engine.reaction.sharedReactionSuspects
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

/** Roadmap N10: allergic-reaction log of the active profile. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReactionsViewModel @Inject constructor(
    private val repo: AllergicReactionRepository,
    scanRepo: ScanRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {
    private val profileId = prefs.activeProfileId.stateIn(viewModelScope, SharingStarted.Eagerly, "default")
    val language: StateFlow<String> = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    val reactions: StateFlow<List<AllergicReaction>> = prefs.activeProfileId.flatMapLatest { repo.observeAll(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suspects: StateFlow<List<SharedSuspect>> = combine(reactions, language) { list, lang ->
        sharedReactionSuspects(list.map { it.ingredients }, lang)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Recent scans to pick the product from (its ingredients are snapshotted). */
    val recentScans: StateFlow<List<ScanResult>> = prefs.activeProfileId.flatMapLatest { scanRepo.observeHistory(30, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(date: LocalDate, scan: ScanResult?, freeName: String, symptoms: String, severity: Int, epinephrine: Boolean, notes: String) =
        guardedLaunch {
            repo.add(
                date = date, productName = scan?.product?.name ?: freeName, barcode = scan?.barcode,
                ingredients = scan?.product?.ingredients?.map { it.name }.orEmpty(),
                symptoms = symptoms, severity = severity, epinephrineUsed = epinephrine, notes = notes, profileId = profileId.value,
            )
        }

    fun delete(id: String) = guardedLaunch { repo.delete(id) }
}
