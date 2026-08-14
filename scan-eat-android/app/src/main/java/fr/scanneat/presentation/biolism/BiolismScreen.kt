package fr.scanneat.presentation.biolism

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
//
// User-reported: bumped +52dp after this header's own outer margin was fixed
// to match FloatingTopBar's 1(sides):2(top/bottom) ratio (FloatingChromeMargin,
// vertical=32dp each edge) instead of its previous ad-hoc Spacing.S(6dp) — see
// DiaryHeaderHeight's identical fix.
private val BiolismHeaderHeight = 140.dp + 52.dp

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
    val hazeState = remember { HazeState() }

    val fgColor = MaterialTheme.colorScheme.onBackground
    // User-reported: unlike DiaryScreen's identical internal-tab-header pattern
    // (see DiaryScreen.kt's own doc comment on this exact fix), this content Box
    // used Modifier.padding(top/bottom) - which shrinks the Box's own layout
    // bounds, so none of the 4 tab screens could ever draw into the reserved
    // header/nav zone even while scrolling. That left hazeSource with nothing
    // there to blur (no content ever visible "through" the glass header while
    // scrolling) and made each tab's own fixed Spacing.L top gap the only real
    // clearance under the header - correct relative to each other, but not
    // actually accounting for the header's real height, so it read as stuck
    // directly under it. Threaded as embeddedTopPadding/embeddedBottomPadding
    // into each tab's own scrollable content instead, exactly like Diary's
    // MealsTab/WeightScreen/etc - content now scrolls the full screen height
    // and is genuinely visible (blurred) behind the floating header.
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + BiolismHeaderHeight
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBottomNavHeight
    // User-reported: MainShell's floating bottom nav looked "wrong" - no visible
    // glass chrome - on the Biolism tab. Root cause: this screen only registered
    // its own local [hazeState] (for BiolismScreen's own internal header) and
    // never registered MainShell's shared bottomNavHazeState, so the bottom
    // nav's hazeEffect had nothing to blur here and rendered as a no-op.
    val bottomNavHazeState = LocalBottomNavHazeState.current
    Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .hazeSource(bottomNavHazeState),
        ) {
            when (activeTab) {
                BiolismTab.TRACKER   -> TrackerScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
                BiolismTab.DATA      -> DataScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
                BiolismTab.EVOLUTION -> EvolutionScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
                BiolismTab.PROFILE   -> BiolismProfileScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance)
            }
        }

        // ── Internal 4-tab header — floating/detached like the rest of the app's
        // chrome (FloatingTopBar/MainShell's nav), in Biolism's own Gold accent
        // rather than the shared AccentCoral, so it stays recognizably Biolism's
        // own header instead of borrowing Scan'eat's exact component. ──
        // User-reported: matches DiaryHeader's identical fix - this used to be
        // an outer Box(glassSheen's own clip) wrapping an inner Surface (its
        // own separate shadow/clip/hazeEffect), the exact construction already
        // fixed on FloatingTopBar/ScanEatCard/MainShell's nav/DiaryHeader (see
        // their own doc comments). Collapsed into a single Column.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                // User-reported: matches DiaryHeader's identical fix - this bespoke
                // floating-pill header (Biolism needs an extra tab row FloatingTopBar
                // doesn't support) used its own ad-hoc horizontal=Spacing.L/vertical=
                // Spacing.S margin, the inverse of the 1(sides):2(top/bottom) ratio
                // FloatingTopBar's own doc comment establishes as this app's standard.
                .padding(horizontal = FloatingChromeMargin.horizontal, vertical = FloatingChromeMargin.vertical)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(CardRadius.PROMINENT))
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                .hazeEffect(state = hazeState, style = FrostedGlassStyle)
                .glassSheen(edgeAlpha = 0.26f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = Gold, glowAlpha = 0.06f)
                .padding(horizontal = Spacing.L)
                .padding(top = Spacing.M, bottom = Spacing.S),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                // User-reported: was headlineSmall — every other screen's title (via
                // FloatingTopBar, Dashboard being the cited reference) renders at
                // titleLarge; standardized here too. Gold tint kept (deliberate,
                // Biolism's own accent — see this header's own doc comment above).
                Text(stringResource(R.string.tab_biolism), style = MaterialTheme.typography.titleLarge, color = LocalGoldAccent.current, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.biolism_subtitle), style = MaterialTheme.typography.labelSmall, color = fgColor.copy(0.4f), letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            // Sub-tab row
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
                        shape = RoundedCornerShape(8.dp),
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
        }
    }
}
