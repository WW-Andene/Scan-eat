package fr.scanneat.presentation.diary.components

import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.TablerIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.delay

/** Found on review: arming has no way to expire on its own - a user who arms
 *  a tab then gets distracted (switches to another tab and back, backgrounds
 *  the app) with nothing left showing them it's still armed would otherwise
 *  have their next unrelated tap on a primary tab silently swap it in. */
private const val ARM_AUTO_CANCEL_MS = 10_000L

/**
 * Merged floating glass header - title row + tab row in one card, both
 * registered against the same hazeState the content Box in DiaryScreen feeds,
 * matching BiolismScreen's own internal header instead of a separate
 * flat, non-blurred ScanEatCard sitting underneath a title-only bar.
 */
@Composable
internal fun BoxScope.DiaryHeader(
    hazeState: HazeState,
    isTabRoot: Boolean,
    onBack: () -> Unit,
    activeTab: DiaryTab,
    onTabChange: (DiaryTab) -> Unit,
    // User-requested: the three always-visible tabs were a fixed literal
    // (MEALS/WEIGHT/WATER) - now caller-owned so a tab held-and-picked out of
    // the "more" dropdown below can swap into one of these slots and have
    // that choice persist (see DiaryScreen's wiring to
    // DiaryViewModel.primaryDiaryTabsOrder).
    primaryTabs: List<DiaryTab>,
    onPrimaryTabsChange: (List<DiaryTab>) -> Unit,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            // User-reported: this header's own outer margin (horizontal=Spacing.L,
            // vertical=Spacing.S) was the inverse of the 1(sides):2(top/bottom) ratio
            // FloatingTopBar's own doc comment already establishes as this app's
            // standard (see FloatingChromeMargin) - DiaryHeader is a separate, bespoke
            // floating-pill implementation (Diary needs an extra tab row FloatingTopBar
            // doesn't support) that never got that same fix applied to it.
            .padding(horizontal = FloatingChromeMargin.horizontal, vertical = FloatingChromeMargin.vertical)
            .glassSheen(edgeAlpha = 0.28f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = AccentCoral),
    ) {
        Surface(
            shape           = RoundedCornerShape(CardRadius.PROMINENT),
            color           = Color.Transparent,
            // User-reported: this header used an untinted shadowElevation while
            // FloatingTopBar/ScanEatCard/MainShell's nav all moved to a tinted
            // Modifier.shadow — standardized here too.
            shadowElevation = 0.dp,
            modifier        = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(CardRadius.PROMINENT))
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                .hazeEffect(state = hazeState, style = FrostedGlassStyle),
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.L).padding(top = Spacing.M, bottom = Spacing.M)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isTabRoot) {
                        IconButton(onClick = onBack, modifier = Modifier.padding(end = Spacing.XS)) {
                            Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
                        }
                    }
                    // User-reported: was headlineSmall — every other screen's title
                    // (via FloatingTopBar, Dashboard being the cited reference) renders
                    // at titleLarge; standardized here so font size actually matches.
                    Text(stringResource(R.string.diary_header), style = MaterialTheme.typography.titleLarge, color = OnBackground, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(Spacing.M))
                // User-reported (2nd round): the single button below (showing only the
                // active tab, everything else behind a DropdownMenu) read as "one tab"
                // instead of a real tab row - MEALS/WEIGHT/WATER (the three most-used
                // trackers) are now always-visible, real tab buttons; ACTIVITY/FASTING/
                // TREATMENT/EXPENSES stay behind the same popup pattern as before, now
                // as the 4th slot instead of the only one. Was a fixed Row with each tab
                // forced to Modifier.weight(1f) before that (see history) - a full
                // horizontally-scrollable 7-tab row still didn't reliably fit any phone
                // width, which is why only 3 are direct buttons here, not all 7.
                val overflowTabs = DiaryTab.entries.filter { it !in primaryTabs }

                // User-requested: hold-to-arm-then-tap-to-replace instead of a
                // continuous drag. A continuous drag out of the "more" dropdown
                // below is unreliable — DropdownMenu renders in its own Android
                // popup window, a separate window from this Row, so tracking one
                // finger's motion across that window boundary in real time (and
                // surviving the popup dismissing mid-gesture) never worked
                // consistently. Long-pressing an item "arms" it (the popup closes,
                // haptic feedback fires), then a normal tap on any of the three
                // primary tab buttons below completes the swap - two independent,
                // ordinary gestures instead of one gesture that has to survive a
                // window handoff.
                var armedOverflowTab by remember { mutableStateOf<DiaryTab?>(null) }
                val haptics = LocalHapticFeedback.current
                LaunchedEffect(armedOverflowTab) {
                    if (armedOverflowTab != null) {
                        delay(ARM_AUTO_CANCEL_MS)
                        armedOverflowTab = null
                    }
                }

                if (armedOverflowTab != null) {
                    Text(
                        stringResource(R.string.diary_tab_pick_replacement),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCoral,
                    )
                    Spacer(Modifier.height(Spacing.T2))
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    primaryTabs.forEach { tab ->
                        DiaryTabButton(
                            tab = tab,
                            isActive = tab == activeTab,
                            isReplaceTarget = armedOverflowTab != null,
                            onClick = {
                                val armed = armedOverflowTab
                                if (armed != null) {
                                    onPrimaryTabsChange(primaryTabs.map { if (it == tab) armed else it })
                                    onTabChange(armed)
                                    armedOverflowTab = null
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    onTabChange(tab)
                                }
                            },
                        )
                    }
                    var tabMenuExpanded by remember { mutableStateOf(false) }
                    val overflowActive = activeTab in overflowTabs
                    Box {
                        Surface(
                            onClick = {
                                if (armedOverflowTab != null) armedOverflowTab = null else tabMenuExpanded = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (overflowActive) ChipBackgroundAccent else SurfaceVariant.copy(alpha = 0.4f),
                            border = if (overflowActive) BorderStroke(1.dp, AccentCoral.copy(alpha = CHIP_BORDER_ALPHA)) else null,
                        ) {
                            Row(
                                Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.M),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                // User-requested: this used to also show the active overflow
                                // tab's text label next to its icon, which was often wide
                                // enough that the whole tab row needed the horizontalScroll
                                // above just to reach it - icon-only reads unambiguously
                                // enough on its own (same as every primary tab button when
                                // scrolled off-screen) without forcing that scroll.
                                if (overflowActive) {
                                    Icon(activeTab.icon, contentDescription = stringResource(activeTab.labelRes), tint = AccentCoral, modifier = Modifier.size(IconSize.Inline))
                                } else {
                                    Text(
                                        stringResource(R.string.diary_tab_more),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OnBackground.copy(0.7f),
                                    )
                                }
                                Icon(TablerIcons.ChevronDown, contentDescription = null, tint = if (overflowActive) AccentCoral else OnBackground.copy(0.7f))
                            }
                        }
                        // DROPDOWN_MENU_GAP - app-wide standard gap between a DropdownMenu and its trigger (see its own doc comment).
                        DropdownMenu(expanded = tabMenuExpanded, onDismissRequest = { tabMenuExpanded = false }, shape = RoundedCornerShape(CardRadius.CONTROL), containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha), shadowElevation = 0.dp, modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL)), offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = DROPDOWN_MENU_GAP)) {
                            overflowTabs.forEach { tab ->
                                val isActive = tab == activeTab
                                HoldToArmMenuItem(
                                    tab = tab,
                                    isActive = isActive,
                                    onTap = { onTabChange(tab); tabMenuExpanded = false },
                                    onArmed = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        armedOverflowTab = tab
                                        tabMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A DropdownMenu row that behaves like a normal [DropdownMenuItem] on a quick
 * tap (calls [onTap]), but "arms" instead (calls [onArmed]) on a long-press.
 *
 * User-reported (three times): earlier hand-rolled attempts at this gesture
 * (a bare hold timer, then a drag-based detector, then a manually-consuming
 * pointer-event loop) each fixed one failure mode while introducing another -
 * needing a drag to register, or the whole tab-switch interaction becoming
 * slow/unresponsive with no press feedback. Replaced with
 * Modifier.combinedClickable(onClick, onLongClick) - Compose's own
 * battle-tested primitive for exactly this case, with built-in ripple
 * feedback and correct touch-slop/timing handled by the framework. Trade-off:
 * the hold duration is now the platform's standard long-press timeout
 * (~500ms), not a custom 2s.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HoldToArmMenuItem(
    tab: DiaryTab,
    isActive: Boolean,
    onTap: () -> Unit,
    onArmed: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(onClick = onTap, onLongClick = onArmed)
            .padding(horizontal = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Icon(tab.icon, null, tint = if (isActive) AccentCoral else OnBackground.copy(0.6f), modifier = Modifier.size(IconSize.Inline))
        // User-reported: was bodyLarge (16sp) - the real DropdownMenuItem this
        // row replaces applies Material3's standard menu-item text style,
        // labelLarge (14sp), internally. bodyLarge read as noticeably bigger
        // than every other menu/dropdown in the app.
        Text(
            stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = OnBackground,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.S),
        )
        if (isActive) Icon(TablerIcons.Check, null, tint = AccentCoral)
    }
}

@Composable
private fun DiaryTabButton(
    tab: DiaryTab,
    isActive: Boolean,
    onClick: () -> Unit,
    isReplaceTarget: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isReplaceTarget) AccentCoral.copy(alpha = 0.16f) else if (isActive) ChipBackgroundAccent else SurfaceVariant.copy(alpha = 0.4f),
        border = if (isReplaceTarget) BorderStroke(2.dp, AccentCoral.copy(alpha = 0.6f)) else if (isActive) BorderStroke(1.dp, AccentCoral.copy(alpha = CHIP_BORDER_ALPHA)) else null,
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Icon(tab.icon, contentDescription = null, tint = if (isActive) AccentCoral else OnBackground.copy(0.7f), modifier = Modifier.size(IconSize.Inline))
            Text(
                stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) AccentCoral else OnBackground.copy(0.7f),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
