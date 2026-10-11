package fr.scanneat.presentation.biolism

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.biolism.bioProfile.BiolismOnboardingScreen
import fr.scanneat.presentation.biolism.bioProfile.BiolismProfileScreen
import fr.scanneat.presentation.biolism.bioProfile.BiolismProfileViewModel
import fr.scanneat.presentation.biolism.data.DataScreen
import fr.scanneat.presentation.biolism.evolution.EvolutionScreen
import fr.scanneat.presentation.biolism.tracker.TrackerScreen
import fr.scanneat.presentation.ui.theme.*

private enum class BiolismTab(@androidx.annotation.StringRes val labelRes: Int) {
    TRACKER(R.string.biolism_tab_tracker), DATA(R.string.biolism_tab_data),
    EVOLUTION(R.string.biolism_tab_evolution), PROFILE(R.string.biolism_tab_profile)
}

@Composable
fun BiolismScreen(gateViewModel: BiolismProfileViewModel = hiltViewModel()) {
    val profile   = gateViewModel.profile.collectAsStateWithLifecycle()
    val onboarded = gateViewModel.onboarded.collectAsStateWithLifecycle()

    if (!onboarded.value && !profile.value.isValid) {
        BiolismOnboardingScreen()
        return
    }

    // Was plain remember - MainShell's bottom-nav switch uses popUpTo(saveState=true)/
    // restoreState=true, so leaving Biolism (e.g. to check Dashboard) and coming back
    // silently reset the user to Tracker, discarding their place on Data/Evolution/
    // Profile - same rememberSaveable pattern DiaryScreen's own sub-tab already uses.
    var activeTab by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = fr.scanneat.presentation.onboarding.enumSaver<BiolismTab>()) { mutableStateOf(BiolismTab.TRACKER) }
    val fgColor = MaterialTheme.colorScheme.onBackground

    // User-instructed, literal: "Utilise exactement le même navbar que
    // Tableau pour Métabolisme." Calls FloatingScreenScaffold exactly like
    // DashboardScreen.kt does - same composable, same parameters for a
    // tab-root screen (hasNavigationIcon = false, no accent override, since
    // Biolism never shows a back arrow, matching Dashboard's own
    // isTabRoot=true branch). Subtitle + tab row ride in FloatingTopBar's
    // extraContent slot, same mechanism Journal's own tab row uses.
    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.tab_biolism), color = OnBackground) },
        hasNavigationIcon = false,
        showBottomNavClearance = true,
        extraContent = {
            Text(stringResource(R.string.biolism_subtitle), style = MaterialTheme.typography.labelSmall, color = fgColor.copy(0.4f), letterSpacing = 1.sp)
            Spacer(Modifier.height(Spacing.M))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                BiolismTab.values().forEach { tab ->
                    val isActive = tab == activeTab
                    Surface(
                        onClick = { activeTab = tab },
                        // heightIn(min = 48.dp) - without it this Surface wrapped its
                        // content height (label text + Spacing.S padding ≈ 32dp), well
                        // under the 48dp Material/WCAG minimum touch target.
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.Tab; selected = isActive },
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                        color = if (isActive) GoldHaze else OnBackground.copy(0.03f),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, GoldBorder) else null,
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(tab.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActive) LocalGoldAccent.current else fgColor.copy(0.5f),
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        extraContentHeight = 64.dp,
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val bottomClearance = padding.calculateBottomPadding()
        Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
            when (activeTab) {
                BiolismTab.TRACKER   -> TrackerScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
                BiolismTab.DATA      -> DataScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
                BiolismTab.EVOLUTION -> EvolutionScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
                BiolismTab.PROFILE   -> BiolismProfileScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
            }
        }
    }
}
