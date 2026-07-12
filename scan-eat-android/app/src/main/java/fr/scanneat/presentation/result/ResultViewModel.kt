package fr.scanneat.presentation.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.scan.ComparisonRepository
import fr.scanneat.data.repository.scan.ComparisonResult
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ResultUiState(
    val scanResult: ScanResult? = null,
    val personalScore: PersonalScoreResult? = null,
    val comparisonResult: ComparisonResult? = null,
    val pairings: List<String> = emptyList(),
    val betterAlternative: ScanResult? = null,
    val logState: LogState = LogState.Idle,
)

// Only worth suggesting an alternative below this grade — A/B/A+ are already a
// good choice, and surfacing one for every single scan would just be noise.
private val ALTERNATIVE_ELIGIBLE_GRADES = setOf(Grade.C, Grade.D, Grade.F)

sealed class LogState {
    data object Idle    : LogState()
    data object Loading : LogState()
    data object Done    : LogState()
    data class  Error(val message: String) : LogState()
}

// Internal sealed type — avoids Pair<Triple<...>> type complexity and null-unsafety
private sealed class ScanLoad {
    data object Empty : ScanLoad()
    data class  Loaded(
        val scan: ScanResult,
        val personal: PersonalScoreResult?,
        val comparison: ComparisonResult?,
        val pairings: List<String>,
        val alternative: ScanResult?,
    ) : ScanLoad()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ResultViewModel @Inject constructor(
    private val scanRepo: ScanRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val comparisonRepo: ComparisonRepository,
    private val prefs: UserPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val scanId: Long = savedStateHandle.get<Long>("scanId") ?: 0L

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    private val _logState = MutableStateFlow<LogState>(LogState.Idle)

    // getById() is a one-shot suspend read, not a Flow, so toggling the DB row
    // via setFavorite() wouldn't otherwise be reflected until the screen fully
    // reloads. This local override lets the star flip instantly; null means
    // "no override yet, use whatever was loaded".
    private val favoriteOverride = MutableStateFlow<Boolean?>(null)

    // arm()/compare() disarm shared comparison state as a side effect, so they must run
    // at most once per scan — WhileSubscribed(5000) can cancel and restart this flow
    // (e.g. backgrounding the screen briefly) which would otherwise re-run the side
    // effect and silently re-arm the current scan as its own comparison baseline.
    private var comparisonResolved = false
    private var cachedComparison: ComparisonResult? = null

    // Fix 2: Use a typed sealed class instead of Pair<Triple<...>> — clean and null-safe
    private val scanLoad: Flow<ScanLoad> = prefs.profile.flatMapLatest { profile ->
        flow {
            val scan = if (scanId > 0L) scanRepo.getById(scanId)
                       else scanRepo.observeHistory(limit = 1).first().firstOrNull()

            if (scan == null) { emit(ScanLoad.Empty); return@flow }

            val personal   = computePersonalScore(scan.audit, scan.product, profile)
            if (!comparisonResolved) {
                comparisonResolved = true
                cachedComparison = if (comparisonRepo.isArmed.first()) comparisonRepo.compare(scan)
                                   else { comparisonRepo.arm(scan); null }
            }
            val pairs      = findPairings(scan.product.name, limit = 5)
            val alternative = if (scan.audit.grade in ALTERNATIVE_ELIGIBLE_GRADES)
                scanRepo.findBetterAlternative(scan) else null

            emit(ScanLoad.Loaded(scan, personal, cachedComparison, pairs, alternative))
        }
    }

    val state: StateFlow<ResultUiState> = combine(scanLoad, _logState, favoriteOverride) { load, logState, favOverride ->
        when (load) {
            is ScanLoad.Empty  -> ResultUiState(logState = logState)
            is ScanLoad.Loaded -> ResultUiState(
                scanResult       = favOverride?.let { load.scan.copy(favorite = it) } ?: load.scan,
                personalScore    = load.personal,
                comparisonResult = load.comparison,
                pairings         = load.pairings,
                betterAlternative = load.alternative,
                logState         = logState,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResultUiState())

    fun log(portionG: Double, mealSlot: MealSlot) {
        if (_logState.value is LogState.Loading) return   // guard against double-tap double-logging
        val scan = state.value.scanResult ?: return
        viewModelScope.launch {
            _logState.value = LogState.Loading
            runCatching {
                consumptionRepo.log(
                    DiaryEntry(
                        date        = LocalDate.now(),
                        mealSlot    = mealSlot,
                        productName = scan.product.name,
                        barcode     = scan.barcode,
                        portionG    = portionG,
                        nutrition   = scan.product.nutrition,
                        source      = scan.source,
                    )
                )
            }.fold(
                onSuccess = { _logState.value = LogState.Done },
                onFailure = { e -> _logState.value = LogState.Error(e.message ?: "Erreur") },
            )
        }
    }

    fun clearLogState() { _logState.value = LogState.Idle }

    fun toggleFavorite() {
        val scan = state.value.scanResult ?: return
        if (scan.dbId <= 0) return
        val newValue = !scan.favorite
        favoriteOverride.value = newValue
        viewModelScope.launch { scanRepo.setFavorite(scan.dbId, newValue) }
    }
}
