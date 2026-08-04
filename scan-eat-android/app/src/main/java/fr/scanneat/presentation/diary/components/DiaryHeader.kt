package fr.scanneat.presentation.diary.components

import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.TablerIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

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
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.L, vertical = Spacing.S)
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
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(CardRadius.PROMINENT), ambientColor = ShadowTint, spotColor = ShadowTint)
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                .hazeEffect(state = hazeState, style = FrostedGlassStyle),
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.L).padding(top = Spacing.M, bottom = Spacing.S)) {
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
                Spacer(Modifier.height(10.dp))
                // Was a fixed Row with each tab forced to Modifier.weight(1f), then a
                // horizontally-scrollable icon+label row - both still forced a
                // horizontal scroll to reach Treatment/Expenses on most phone widths,
                // one more scroll gesture on top of the day-picker/list scrolling this
                // screen already asks for, and it turned out inactive icon-only tabs
                // still didn't reliably fit either. Replaced with a single button
                // showing the active tab, opening a popup menu (DropdownMenu) listing
                // all seven - same pattern CollapsibleFilterBar now uses for filters,
                // so there is no list to scroll or expand at all, on any screen width.
                var tabMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { tabMenuExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = ChipBackgroundAccent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCoral.copy(alpha = CHIP_BORDER_ALPHA)),
                    ) {
                        Row(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.M),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                        ) {
                            Icon(activeTab.icon, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(IconSize.Inline))
                            Text(
                                stringResource(activeTab.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentCoral, fontWeight = FontWeight.Bold,
                            )
                            Icon(TablerIcons.ChevronDown, contentDescription = null, tint = AccentCoral)
                        }
                    }
                    DropdownMenu(expanded = tabMenuExpanded, onDismissRequest = { tabMenuExpanded = false }, shape = RoundedCornerShape(CardRadius.CONTROL), containerColor = SurfaceVariant.copy(alpha = 0.94f), shadowElevation = 0.dp, modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL))) {
                        DiaryTab.entries.forEach { tab ->
                            val isActive = tab == activeTab
                            val label = stringResource(tab.labelRes)
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { Icon(tab.icon, null, tint = if (isActive) AccentCoral else OnBackground.copy(0.6f)) },
                                trailingIcon = { if (isActive) Icon(TablerIcons.Check, null, tint = AccentCoral) },
                                onClick = { onTabChange(tab); tabMenuExpanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
