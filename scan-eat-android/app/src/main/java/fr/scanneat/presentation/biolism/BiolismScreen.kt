package fr.scanneat.presentation.biolism

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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

// Taller than FloatingTopBarHeight (title + subtitle + tab row, not just a
// single title row) — not including the device's own status-bar inset, which
// is added separately via windowInsetsPadding below, same as FloatingTopBar.
private val BiolismHeaderHeight = 140.dp

@Composable
fun BiolismScreen(gateViewModel: BiolismProfileViewModel = hiltViewModel()) {
    val profile   = gateViewModel.profile.collectAsStateWithLifecycle()
    val onboarded = gateViewModel.onboarded.collectAsStateWithLifecycle()

    if (!onboarded.value && !profile.value.isValid) {
        BiolismOnboardingScreen()
        return
    }

    var activeTab by remember { mutableStateOf(BiolismTab.TRACKER) }
    val hazeState = remember { HazeState() }

    val fgColor = MaterialTheme.colorScheme.onBackground
    // True floating chrome, matching MainShell/FloatingScreenScaffold: the tab
    // content Box fills the whole frame and the header floats on top of it
    // (z-order, not push-down), registered as this header's own hazeSource so
    // it shows a real backdrop blur of whatever's passing underneath instead
    // of stopping short of it. Bottom clearance for MainShell's own floating
    // nav (Biolism is one of its TOP_TABS) is reserved here as a fixed gap
    // rather than true scroll-under, since none of the 4 tab screens below
    // expose a contentPadding hook of their own to thread it through precisely.
    Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = Gold, secondary = Teal)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(top = BiolismHeaderHeight)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBottomNavHeight),
        ) {
            when (activeTab) {
                BiolismTab.TRACKER   -> TrackerScreen()
                BiolismTab.DATA      -> DataScreen()
                BiolismTab.EVOLUTION -> EvolutionScreen()
                BiolismTab.PROFILE   -> BiolismProfileScreen()
            }
        }

        // ── Internal 4-tab header — floating/detached like the rest of the app's
        // chrome (FloatingTopBar/MainShell's nav), in Biolism's own Gold accent
        // rather than the shared AccentCoral, so it stays recognizably Biolism's
        // own header instead of borrowing Scan'eat's exact component. ──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.L, vertical = Spacing.S)
                .glassSheen(edgeAlpha = 0.26f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = Gold, glowAlpha = 0.06f),
        ) {
        Surface(
            shape           = RoundedCornerShape(CardRadius.PROMINENT),
            color           = Color.Transparent,
            shadowElevation = 8.dp,
            modifier        = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                .hazeEffect(state = hazeState, style = FrostedGlassStyle),
        ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.L)
                .padding(top = Spacing.M, bottom = Spacing.S),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(stringResource(R.string.tab_biolism), style = MaterialTheme.typography.headlineSmall, color = LocalGoldAccent.current)
            }
            Text(stringResource(R.string.biolism_subtitle), style = MaterialTheme.typography.labelSmall, color = fgColor.copy(0.4f), letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            // Sub-tab row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BiolismTab.values().forEach { tab ->
                    val isActive = tab == activeTab
                    Surface(
                        onClick = { activeTab = tab },
                        // heightIn(min = 48.dp) - without it this Surface wrapped its
                        // content height (label text + Spacing.S padding ≈ 32dp), well
                        // under the 48dp Material/WCAG minimum touch target.
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.Tab; selected = isActive },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) GoldHaze else OnBackground.copy(0.03f),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, GoldBorder) else null,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        }
        }
        }
    }
}
