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
        viewModelScope.launch { repo.setFavorite(scan.dbId, !scan.favorite) }
    }
}
