package fr.scanneat.presentation.loyalty

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.loyalty.LoyaltyCard
import fr.scanneat.data.repository.loyalty.LoyaltyCardRepository
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LoyaltyCardsViewModel @Inject constructor(
    private val repo: LoyaltyCardRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val cards: StateFlow<List<LoyaltyCard>> = activeProfileId.flatMapLatest { id -> repo.cards(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCard(storeName: String, code: String) {
        guardedLaunch { repo.add(storeName, code, activeProfileId.value) }
    }

    fun removeCard(id: String) {
        guardedLaunch { repo.remove(id, activeProfileId.value) }
    }
}
