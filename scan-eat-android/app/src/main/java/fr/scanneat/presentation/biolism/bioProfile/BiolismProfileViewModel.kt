package fr.scanneat.presentation.biolism.bioProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.domain.engine.biolism.BiolismProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiolismProfileViewModel @Inject constructor(private val repo: BiolismRepository) : ViewModel() {
    val profile: StateFlow<BiolismProfile> = repo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun save(p: BiolismProfile) = viewModelScope.launch { repo.saveProfile(p); _saved.value = true }
    fun clearSaved() { _saved.value = false }
}
