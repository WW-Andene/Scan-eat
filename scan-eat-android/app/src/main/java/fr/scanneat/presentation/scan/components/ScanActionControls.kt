package fr.scanneat.presentation.scan.components

import compose.icons.tablericons.History
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.CircularProgressIndicator
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
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.minTouchTarget

@Composable
internal fun BoxScope.ScanScoreFab(scanState: ScanUiState, bottomNavClearance: Dp, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = bottomNavClearance + 20.dp),
        containerColor = AccentCoral,
        shape = CircleShape,
    ) {
        // Exhaustive over all 7 ScanUiState variants (no `else`) - a future
        // 8th variant now fails to compile here instead of silently falling
        // through to the generic search icon unnoticed.
        when (scanState) {
            is ScanUiState.Scanning -> CircularProgressIndicator(
                color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
    Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 84.dp, bottom = bottomNavClearance + 28.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            multiHint,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.75f),
        )
        Box(modifier = Modifier.glassSheen(edgeAlpha = 0.20f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = AccentCoral, glowAlpha = 0.06f)) {
            Surface(
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                color = SurfaceVariant.copy(0.9f),
                modifier = Modifier
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.PROMINENT), ambientColor = ShadowTint, spotColor = ShadowTint)
                    .clip(RoundedCornerShape(CardRadius.PROMINENT))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = multiHint,
                    ),
                shadowElevation = 0.dp,
            ) {
                Row(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fastfood, null, tint = AccentCoral, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_identify_food_button), style = MaterialTheme.typography.labelSmall, color = OnSurface)
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ScanRecentBarcodesRow(recentBarcodes: List<String>, bottomNavClearance: Dp, onQuickScan: (String) -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.BottomStart)
            .padding(start = 20.dp, bottom = bottomNavClearance + 84.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        recentBarcodes.takeLast(3).reversed().forEach { bc ->
            Box(Modifier.glassSheen(edgeAlpha = 0.12f, shape = RoundedCornerShape(20.dp), glowAlpha = 0f, reliefAlpha = 0f)) {
                Surface(
                    onClick = { onQuickScan(bc) },
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceVariant.copy(0.85f),
                    modifier = Modifier
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp), ambientColor = ShadowTint, spotColor = ShadowTint)
                        .clip(RoundedCornerShape(20.dp)),
                    shadowElevation = 0.dp,
                ) {
                    Row(Modifier.padding(horizontal = Spacing.SM, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(TablerIcons.History, null, tint = AccentCoral, modifier = Modifier.size(12.dp))
                        Text(bc, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.85f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ScanInstantModeFab(instantMode: Boolean, bottomNavClearance: Dp, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier       = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = bottomNavClearance + 20.dp),
        containerColor = if (instantMode) AccentCoral else SurfaceVariant,
        shape          = CircleShape,
    ) {
        Icon(Icons.Rounded.Bolt, stringResource(R.string.scan_instant_toggle), tint = if (instantMode) Color.Black else OnSurface)
    }
}

/**
 * Toggles shelf-scan mode (hybrid live-boxes/tap-to-identify) — top-end,
 * stacked below the flash toggle (same corner, same 40dp size) rather than
 * the bottom-start corner, which already holds the instant-mode FAB and,
 * conditionally, the recent-barcodes chip column right above it. Always
 * reserves the flash button's own height even when this device has no flash
 * unit (CameraPreview's hasFlash isn't exposed to this screen to condition
 * on) — a small unused gap above it there is a minor cosmetic cost, not a
 * collision with anything else in that corner.
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
        modifier       = Modifier.align(Alignment.TopEnd).padding(top = topInset + Spacing.L + 48.dp, end = Spacing.L)
            .minTouchTarget() // was a fixed 40dp, below the 48dp WCAG/Material touch-target minimum
            .semantics { traversalIndex = -1f },
        containerColor = if (shelfMode) Teal else SurfaceVariant,
    ) {
        Icon(Icons.Rounded.GridView, stringResource(R.string.scan_shelf_mode_toggle), tint = if (shelfMode) Color.Black else OnSurface)
    }
}
