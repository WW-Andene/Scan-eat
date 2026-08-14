package fr.scanneat.presentation.scan.components

import compose.icons.tablericons.History
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.scan.ScanUiState
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.minTouchTarget
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.ScanEatLoadingIndicator

/**
 * User-reported: this whole bottom-anchored FAB cluster (this FAB, the
 * identify-food action, the recent-barcodes row) used bare 20/28/84dp
 * literals that didn't decompose cleanly onto the app's Spacing scale,
 * unlike every other screen's FAB corner margin (Spacing.L, e.g. Diary's
 * own FAB). ScanFabMargin unifies them onto that same convention; the
 * offsets below are built from margin + real FAB size (64.dp, Material's
 * own standard FAB dimension, not a spacing concern) + a named gap, the
 * same formula this file's own top-anchored stack (ScanShelfModeFab/
 * ScanInstantModeFab below) already uses.
 */
private val ScanFabMargin = Spacing.L

@Composable
internal fun BoxScope.ScanScoreFab(scanState: ScanUiState, bottomNavClearance: Dp, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = ScanFabMargin, bottom = bottomNavClearance + ScanFabMargin),
        containerColor = AccentCoral,
        shape = CircleShape,
    ) {
        // Exhaustive over all 7 ScanUiState variants (no `else`) - a future
        // 8th variant now fails to compile here instead of silently falling
        // through to the generic search icon unnoticed.
        when (scanState) {
            is ScanUiState.Scanning -> ScanEatLoadingIndicator(size = 24.dp, color = Color.Black)
            is ScanUiState.Idle, is ScanUiState.Success, is ScanUiState.Error,
            is ScanUiState.MedicationFound, is ScanUiState.NonConsumableFound,
            is ScanUiState.MultiFoodFound ->
                Icon(TablerIcons.Search, stringResource(R.string.scan_cd_scan), tint = Color.Black)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxScope.ScanIdentifyFoodAction(bottomNavClearance: Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    // Long-press is a hidden gesture by nature — this one-line caption (shown only
    // while the pill itself is, i.e. photos queued / no barcode / not scanning,
    // same gate the caller already applies) is what makes identifyMultiFromPhotos()
    // discoverable at all, instead of a feature nobody ever stumbles onto.
    val multiHint = stringResource(R.string.scan_identify_multi_hint)
    val isPrism = LocalThemeName.current == "prism"
    Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = ScanFabMargin + 64.dp + Spacing.SM, bottom = bottomNavClearance + ScanFabMargin + Spacing.SM),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Text(
            multiHint,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.75f),
        )
        // User-reported: this used to be an outer Box(glassSheen's own clip)
        // wrapping an inner Surface (its own separate shadow/clip) - two
        // independently-clipped objects, the exact construction already fixed
        // on ScanEatCard/FloatingTopBar/MainShell's nav/DiaryHeader/BioCard
        // (see their own doc comments). Collapsed into one Box.
        Box(
            Modifier
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.PROMINENT))
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                .background(if (isPrism) PrismFillColor else SurfaceVariant.copy(alpha = StandardCardAlpha), RoundedCornerShape(CardRadius.PROMINENT))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = multiHint,
                )
                .glassSheen(edgeAlpha = 0.20f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = AccentCoral, glowAlpha = 0.06f),
        ) {
            Row(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Fastfood, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Small))
                Spacer(Modifier.width(Spacing.S))
                Text(stringResource(R.string.scan_identify_food_button), style = MaterialTheme.typography.labelSmall, color = OnSurface)
            }
        }
    }
}

@Composable
internal fun BoxScope.ScanRecentBarcodesRow(recentBarcodes: List<String>, bottomNavClearance: Dp, onQuickScan: (String) -> Unit) {
    val isPrism = LocalThemeName.current == "prism"
    Column(
        modifier = Modifier.align(Alignment.BottomStart)
            .padding(start = ScanFabMargin, bottom = bottomNavClearance + ScanFabMargin + 64.dp + Spacing.SM),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        recentBarcodes.takeLast(3).reversed().forEach { bc ->
            // User-reported: same two-layer construction already fixed elsewhere
            // (see ScanIdentifyFoodAction's own comment above) - collapsed into one Box.
            Box(
                Modifier
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isPrism) PrismFillColor else SurfaceVariant.copy(alpha = StandardCardAlpha), RoundedCornerShape(24.dp))
                    .clickable { onQuickScan(bc) }
                    .glassSheen(edgeAlpha = 0.12f, shape = RoundedCornerShape(24.dp), glowAlpha = 0f, reliefAlpha = 0f),
            ) {
                Row(Modifier.padding(horizontal = Spacing.SM, vertical = Spacing.XS), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Icon(TablerIcons.History, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Micro))
                    Text(bc, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.85f))
                }
            }
        }
    }
}

/**
 * User-requested: standard "cube" (square, not circular) shape, stacked
 * top-end below the shelf/multi-mode toggle ([ScanShelfModeFab]) instead of
 * its previous bottom-start FAB spot - the gallery-import button now takes
 * that spot instead (see ScanScreen.kt). Same stacking-offset formula
 * ScanShelfModeFab's own doc comment already documents (flash's real 56dp
 * height + Spacing.S + 6dp gap), one more tier down for this button below it.
 */
@Composable
internal fun BoxScope.ScanInstantModeFab(instantMode: Boolean, topInset: Dp, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier       = Modifier.align(Alignment.TopEnd)
            .padding(top = topInset + Spacing.L + (64.dp + Spacing.S + 6.dp) * 2, end = Spacing.L)
            .minTouchTarget(),
        containerColor = if (instantMode) AccentCoral else SurfaceVariant,
        shape          = RoundedCornerShape(CardRadius.CONTROL),
    ) {
        Icon(Icons.Rounded.Bolt, stringResource(R.string.scan_instant_toggle), tint = if (instantMode) Color.Black else OnSurface)
    }
}

/**
 * Toggles shelf-scan mode (hybrid live-boxes/tap-to-identify) — top-end,
 * stacked below the flash toggle rather than the bottom-start corner, which
 * already holds the instant-mode FAB and, conditionally, the recent-barcodes
 * chip column right above it. Always reserves the flash button's own height
 * even when this device has no flash unit (CameraPreview's hasFlash isn't
 * exposed to this screen to condition on) — a small unused gap above it
 * there is a minor cosmetic cost, not a collision with anything else in
 * that corner.
 *
 * User-reported: the 48.dp stacking offset this used to use predated the
 * flash button's own move to the 48dp WCAG touch-target minimum (see
 * CameraPreview.kt) - a *standard* FloatingActionButton is a fixed 56dp
 * regardless of that minimum, so 48dp under-reserved the flash button's
 * actual height and left this one overlapping it by 8dp instead of
 * following it. Now reserves the real 56dp FAB height plus the app's own
 * tight-inline-gap token (Spacing.S), the same tier already used for the
 * score-delta/legend-dot spacing this close together elsewhere.
 */
@Composable
internal fun BoxScope.ScanShelfModeFab(shelfMode: Boolean, topInset: Dp, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        // traversalIndex = -1f (before every other FAB in this Box's default 0f) -
        // this is the only one of the five controls in this overlay anchored to the
        // TOP of the screen, but it was declared last in ScanScreen's composition
        // order (after all four bottom-anchored controls), so TalkBack's default
        // traversal - which follows composition order here, not screen position -
        // announced it dead last: a swipe-through went bottom-end, bottom-end,
        // bottom-start, bottom-start, then jumped back up to this top-right button.
        // User-reported: the gap below the flash button wasn't visible on-device -
        // bumped by another 6dp on top of the existing Spacing.S gap.
        modifier       = Modifier.align(Alignment.TopEnd).padding(top = topInset + Spacing.L + 64.dp + Spacing.S + 6.dp, end = Spacing.L)
            .minTouchTarget() // was a fixed 40dp, below the 48dp WCAG/Material touch-target minimum
            .semantics { traversalIndex = -1f },
        containerColor = if (shelfMode) Teal else SurfaceVariant,
    ) {
        Icon(Icons.Rounded.GridView, stringResource(R.string.scan_shelf_mode_toggle), tint = if (shelfMode) Color.Black else OnSurface)
    }
}
