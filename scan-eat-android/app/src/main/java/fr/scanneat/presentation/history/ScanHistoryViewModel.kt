package fr.scanneat.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

enum class HistorySort { RECENT, OLDEST, NAME_AZ, SCORE_DESC }

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(
    private val repo: ScanRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Plain string comparison sorts accented letters (Éclair) after every
    // unaccented one instead of near their unaccented form - a Collator with
    // PRIMARY strength groups accents/case together for correct French order.
    // Derived from the in-app language (not Locale.getDefault()/system locale)
    // and recomputed whenever it changes, matching WeightScreen/DiaryScreen/
    // MealPlanScreen's Locale(language.value) pattern - a fixed Locale.getDefault()
    // snapshot here would ignore Settings > Language entirely for history sorting.
    private val nameCollator: StateFlow<Collator> = language
        .map { Collator.getInstance(Locale(it)).apply { strength = Collator.PRIMARY } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Collator.getInstance(Locale("fr")).apply { strength = Collator.PRIMARY })

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _sort = MutableStateFlow(HistorySort.RECENT)
    val sort: StateFlow<HistorySort> = _sort.asStateFlow()

    // Reactive limit instead of a fixed 200 - a user with more scans than the
    // cap would otherwise have every older non-favorite scan permanently
    // unreachable from History with no way to see further back. loadMore()
    // raises the cap and flatMapLatest re-subscribes with the wider window.
    private val _limit = MutableStateFlow(200)
    private val allScans = _limit.flatMapLatest { repo.observeHistory(limit = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True while there may be more, older rows beyond the current window. */
    val canLoadMore: StateFlow<Boolean> = combine(allScans, _limit) { scans, limit -> scans.size >= limit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadMore() { _limit.value += 200 }

    // observeHistory is capped at 200 rows - a favorite older than the 200
    // most-recent scans would otherwise silently vanish from its own
    // dedicated filter. observeFavorites queries the DB directly, unbounded.
    private val favoriteScans = repo.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Score-range filter: null = all, else pair of (min, max) inclusive
    private val _scoreRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val scoreRange: StateFlow<Pair<Int, Int>?> = _scoreRange.asStateFlow()

    // kotlinx.coroutines' typed combine() overloads stop at 5 flows, so _sort
    // and nameCollator are paired up-front to keep this at 5 arguments.
    private val sortAndCollator: Flow<Pair<HistorySort, Collator>> = combine(_sort, nameCollator) { sort, collator -> sort to collator }

    val filtered: StateFlow<List<ScanResult>> = combine(allScans, favoriteScans, _query.debounce(200), _favoritesOnly, sortAndCollator) { scans, favs, q, favOnly, (sort, collator) ->
        (if (favOnly) favs else scans)
            .filter { q.isBlank() || it.product.name.contains(q, ignoreCase = true) || it.barcode?.contains(q) == true }
            .let { list ->
                when (sort) {
                    HistorySort.RECENT      -> list
                    HistorySort.OLDEST      -> list.asReversed()
                    HistorySort.NAME_AZ     -> list.sortedWith(compareBy(collator) { it.product.name })
                    HistorySort.SCORE_DESC  -> list.sortedByDescending { it.audit.score }
                }
            }
    }.combine(_scoreRange) { list, range ->
        if (range == null) list
        else list.filter { it.audit.score in range.first..range.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Top 3 most-scanned products (by product name across full history)
    val topScanned: StateFlow<List<Pair<String, Int>>> = allScans
        .map { scans ->
            scans.groupBy { it.product.name }
                .mapValues { it.value.size }
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key to it.value }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setFavoritesOnly(value: Boolean) { _favoritesOnly.value = value }
    fun setSort(value: HistorySort) { _sort.value = value }
    fun setScoreRange(range: Pair<Int, Int>?) { _scoreRange.value = range }

    fun toggleFavorite(scan: ScanResult) {
        if (scan.dbId <= 0) return
        viewModelScope.launch {
            // Reads the ViewModel's own live caches instead of trusting the
            // `scan` snapshot the composable captured at click time — a rapid
            // double-tap (faster than the DB write's Flow re-emission) could
            // otherwise read the same stale `favorite` value twice and call
            // setFavorite(true) both times instead of toggling back off.
            // Checks favoriteScans too - a favorite older than the 200-row
            // allScans cap only exists in that unbounded list, so relying on
            // allScans alone reintroduces the exact race this comment
            // describes for every scan visible solely via favoritesOnly.
            val current = allScans.value.firstOrNull { it.dbId == scan.dbId }?.favorite
                ?: favoriteScans.value.firstOrNull { it.dbId == scan.dbId }?.favorite
                ?: scan.favorite
            repo.setFavorite(scan.dbId, !current)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
