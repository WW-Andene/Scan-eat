package fr.scanneat.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.CancellationException
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

    // Previously "search" only ever filtered allScans/favoriteScans - the loaded
    // window (default 200, expandable via loadMore), or the unbounded-but-
    // favorites-only favoriteScans - client-side. A query for a genuinely old,
    // non-favorite scan not yet loaded found nothing until loadMore() was tapped
    // enough times to reach it. null means "no active search" (query blank),
    // distinct from an empty result list, so filtered below knows whether to
    // fall back to the loaded window or use these DB-searched results directly.
    private val searchResults: Flow<List<ScanResult>?> = _query.debounce(200).flatMapLatest { q ->
        if (q.isBlank()) flowOf(null) else repo.searchHistory(q)
    }

    val filtered: StateFlow<List<ScanResult>> = combine(allScans, favoriteScans, searchResults, _favoritesOnly, sortAndCollator) { scans, favs, searched, favOnly, (sort, collator) ->
        val base = searched?.let { if (favOnly) it.filter { s -> s.favorite } else it } ?: (if (favOnly) favs else scans)
        base.let { list ->
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

    // Top 3 most-scanned products: name, count, and latest dbId for tap-to-open.
    // Previously grouped allScans (scan_history), which persist() upserts by
    // barcode - a rescan REPLACEs the same row in place, so any barcoded
    // product's count could never exceed 1 and this could only ever surface
    // barcode-less (photo-identified) products. Now derived from the
    // append-only scan_score_history log via a dedicated DAO query, so a
    // repeatedly-rescanned barcoded product is finally counted correctly.
    val topScanned: StateFlow<List<Triple<String, Int, Long>>> = repo.observeTopScanned(limit = 3)
        .map { rows -> rows.map { Triple(it.productName, it.cnt, it.dbId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Average audit score across all scans, null when history is empty. */
    val avgScore: StateFlow<Int?> = allScans
        .map { scans -> if (scans.isEmpty()) null else (scans.sumOf { it.audit.score } / scans.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Grade-band distribution — how many scans land in A/B/C/D.
    val gradeDistribution: StateFlow<List<Pair<String, Int>>> = allScans
        .map { scans ->
            val bands = linkedMapOf("A" to 0, "B" to 0, "C" to 0, "D" to 0)
            scans.forEach {
                // Grade has 6 values (A+, A, B, C, D, F) but the UI only has 4 color bands —
                // A+ folds into A (still the best band) and F folds into D (still the worst),
                // instead of both silently falling through to an "else" bucket the map never declared.
                val band = when (it.audit.grade.label) {
                    "A+" -> "A"
                    "F"  -> "D"
                    else -> it.audit.grade.label
                }
                bands[band] = (bands[band] ?: 0) + 1
            }
            bands.entries.filter { it.value > 0 }.map { it.key to it.value }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setFavoritesOnly(value: Boolean) { _favoritesOnly.value = value }
    fun setSort(value: HistorySort) { _sort.value = value }
    fun setScoreRange(range: Pair<Int, Int>?) { _scoreRange.value = range }

    // toggleFavorite()/delete() previously called repo's Room writes completely
    // unguarded - unlike every sibling tracker ViewModel (Weight/Activity/Dashboard/
    // MealPlan/Templates all wrap theirs in runCatching), so a write failure here
    // wasn't just silent, it was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

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
            runCatching { repo.setFavorite(scan.dbId, !current) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { runCatching { repo.delete(id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }
}
