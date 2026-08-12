package fr.scanneat.presentation.nonfood

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nonfood.NonFoodScanItem
import fr.scanneat.data.repository.nonfood.NonFoodScanRepository
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * User-requested: non-food scans (shampoo/gel douche/cosmétiques...) get a
 * history + favorites view, same as food's ScanHistoryViewModel/
 * ScanHistoryScreen - see NonFoodScanEntity's own doc comment for why this
 * is a separate, smaller repository rather than reusing ScanRepository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NonFoodHistoryViewModel @Inject constructor(
    private val repo: NonFoodScanRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {

    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()
    fun setFavoritesOnly(value: Boolean) { _favoritesOnly.value = value }

    private val allScans = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val favoriteScans = activeProfileId.flatMapLatest { id -> repo.observeFavorites(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<NonFoodScanItem>> = combine(allScans, favoriteScans, _favoritesOnly) { all, favs, favOnly ->
        if (favOnly) favs else all
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Same "unguarded write" fix and race-safety read as ScanHistoryViewModel.toggleFavorite -
    // see its own doc comment.
    fun toggleFavorite(item: NonFoodScanItem) {
        guardedLaunch {
            val current = allScans.value.firstOrNull { it.id == item.id }?.favorite
                ?: favoriteScans.value.firstOrNull { it.id == item.id }?.favorite
                ?: item.favorite
            repo.setFavorite(item.id, !current)
        }
    }

    fun delete(id: Long) = guardedLaunch { repo.delete(id) }
}
