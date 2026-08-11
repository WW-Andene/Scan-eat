package fr.scanneat.presentation.loyalty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.loyalty.LoyaltyCard
import fr.scanneat.data.repository.loyalty.LoyaltyCardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LoyaltyCardsViewModel @Inject constructor(
    private val repo: LoyaltyCardRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val cards: StateFlow<List<LoyaltyCard>> = activeProfileId.flatMapLatest { id -> repo.cards(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _actionFailed = MutableStateFlow(false)
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun addCard(storeName: String, code: String) {
        viewModelScope.launch {
            runCatching { repo.add(storeName, code, activeProfileId.value) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun removeCard(id: String) {
        viewModelScope.launch {
            runCatching { repo.remove(id, activeProfileId.value) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
