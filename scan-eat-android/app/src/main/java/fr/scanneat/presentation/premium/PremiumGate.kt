package fr.scanneat.presentation.premium

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.EmptyListState
import fr.scanneat.presentation.ui.theme.FloatingScreenScaffold
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.ambientGloom
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PremiumGateViewModel @Inject constructor(prefs: UserPreferences) : ViewModel() {
    val isPremium: StateFlow<Boolean> = prefs.isPremium.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}

/**
 * Wraps a Premium-only screen (Biolism, AI photo scanning) - shows [content] as-is
 * for a Premium user, or a locked empty state with an upgrade CTA otherwise. See
 * UserPreferences.isPremium's own doc comment for why these two specifically are
 * the paid tier and everything else stays free.
 *
 * User-instructed, literal: "Utilise exactement le même navbar que Tableau
 * pour Métabolisme" - the locked state is what a non-Premium user actually
 * sees on the Métabolisme tab, and it previously had no header at all (a bare
 * centered message), unlike the unlocked BiolismScreen behind this same gate.
 * [title] wires it through FloatingScreenScaffold - the exact composable
 * Dashboard/Biolism's own unlocked screen call - so the locked state gets
 * the identical header/footer chrome instead of being the one state with none.
 */
@Composable
fun PremiumGate(
    title: @Composable () -> Unit,
    lockedMessage: String,
    onOpenSettings: () -> Unit,
    viewModel: PremiumGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val isPremium = viewModel.isPremium.collectAsStateWithLifecycle()
    if (isPremium.value) {
        content()
    } else {
        FloatingScreenScaffold(
            title = title,
            hasNavigationIcon = false,
            showBottomNavClearance = true,
        ) { padding ->
            // User-reported: the locked state rendered EmptyListState bare, with no
            // background at all - every unlocked screen behind this gate (Biolism)
            // uses ambientGloom(), so the locked state sat on MainShell's plain
            // Background fill instead, and (since there was no gloom to animate)
            // never picked up the "Fond animé" setting either.
            Box(
                Modifier.fillMaxSize()
                    .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyListState(
                    icon = Icons.Default.WorkspacePremium,
                    message = lockedMessage,
                    ctaLabel = stringResource(R.string.settings_premium_enable_button),
                    onCta = onOpenSettings,
                )
            }
        }
    }
}
