package fr.scanneat.presentation.profileshare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.profileshare.ProfileShareCodec
import fr.scanneat.data.repository.profileshare.SharedProfilePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Roadmap N8: share the active profile as an encrypted QR, or import one. */
@HiltViewModel
class ProfileShareViewModel @Inject constructor(private val prefs: UserPreferences) : ViewModel() {
    data class Outgoing(val qrText: String, val code: String, val profileName: String)
    sealed class Import {
        data object Scanning : Import()
        data class NeedCode(val raw: String, val wrongCode: Boolean = false) : Import()
        data object Working : Import()
        data class Done(val name: String) : Import()
    }

    private val _outgoing = MutableStateFlow<Outgoing?>(null)
    val outgoing: StateFlow<Outgoing?> = _outgoing.asStateFlow()
    private val _import = MutableStateFlow<Import>(Import.Scanning)
    val import: StateFlow<Import> = _import.asStateFlow()

    fun prepareShare() = viewModelScope.launch {
        val profile = prefs.profile.first()
        val code = ProfileShareCodec.newCode()
        val qr = withContext(Dispatchers.Default) { ProfileShareCodec.seal(ProfileShareCodec.payloadOf(profile), code) }
        _outgoing.value = Outgoing(qr, code, profile.name)
    }

    fun onQr(raw: String) {
        if (_import.value is Import.Scanning && ProfileShareCodec.isShareQr(raw)) _import.value = Import.NeedCode(raw)
    }

    fun submitCode(code: String, receivedLabel: String) {
        val s = _import.value as? Import.NeedCode ?: return
        _import.value = Import.Working
        viewModelScope.launch {
            val payload: SharedProfilePayload? = withContext(Dispatchers.Default) { ProfileShareCodec.open(s.raw, code) }
            if (payload == null) { _import.value = s.copy(wrongCode = true); return@launch }
            val previous = prefs.activeProfileId.first()
            val id = prefs.createProfile(payload.name)
            val profile = ProfileShareCodec.toProfile(payload, id, receivedLabel)
            prefs.saveProfile(profile)          // saveProfile also activates it...
            prefs.setActiveProfileId(previous)  // ...so the user's own profile stays active
            _import.value = Import.Done(profile.name)
        }
    }

    fun restartImport() { _import.value = Import.Scanning }
}
