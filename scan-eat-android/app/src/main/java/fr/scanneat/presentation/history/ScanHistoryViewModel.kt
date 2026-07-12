package fr.scanneat.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanHistoryViewModel @Inject constructor(private val repo: ScanRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val allScans = repo.observeHistory(limit = 200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<ScanResult>> = combine(allScans, _query, _favoritesOnly) { scans, q, favOnly ->
        scans
            .filter { !favOnly || it.favorite }
            .filter { q.isBlank() || it.product.name.contains(q, ignoreCase = true) || it.barcode?.contains(q) == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setFavoritesOnly(value: Boolean) { _favoritesOnly.value = value }

    fun toggleFavorite(scan: ScanResult) {
        if (scan.dbId <= 0) return
        viewModelScope.launch {
            // Reads the ViewModel's own live cache instead of trusting the
            // `scan` snapshot the composable captured at click time — a rapid
            // double-tap (faster than the DB write's Flow re-emission) could
            // otherwise read the same stale `favorite` value twice and call
            // setFavorite(true) both times instead of toggling back off.
            val current = allScans.value.firstOrNull { it.dbId == scan.dbId }?.favorite ?: scan.favorite
            repo.setFavorite(scan.dbId, !current)
        }
    }
}
