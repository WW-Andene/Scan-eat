package fr.scanneat.presentation.premium

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.presentation.ui.theme.EmptyListState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
internal class PremiumGateViewModel @Inject constructor(prefs: UserPreferences) : ViewModel() {
    val isPremium: StateFlow<Boolean> = prefs.isPremium.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}

/**
 * Wraps a Premium-only screen (Biolism, AI photo scanning) - shows [content] as-is
 * for a Premium user, or a locked empty state with an upgrade CTA otherwise. See
 * UserPreferences.isPremium's own doc comment for why these two specifically are
 * the paid tier and everything else stays free.
 */
@Composable
fun PremiumGate(
    lockedMessage: String,
    onOpenSettings: () -> Unit,
    viewModel: PremiumGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val isPremium = viewModel.isPremium.collectAsStateWithLifecycle()
    if (isPremium.value) {
        content()
    } else {
        EmptyListState(
            icon = Icons.Default.WorkspacePremium,
            message = lockedMessage,
            ctaLabel = stringResource(R.string.settings_premium_enable_button),
            onCta = onOpenSettings,
        )
    }
}
