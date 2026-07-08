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

    private val allScans = repo.observeHistory(limit = 200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<ScanResult>> = combine(allScans, _query) { scans, q ->
        if (q.isBlank()) scans
        else scans.filter { it.product.name.contains(q, ignoreCase = true) || it.barcode?.contains(q) == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }
}
