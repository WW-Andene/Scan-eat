package fr.scanneat.presentation.scan.components

import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.medication.generateMedicationHints
import fr.scanneat.domain.engine.nonconsumable.generateNonConsumableHints
import fr.scanneat.presentation.result.FactsCautionsColumn
import fr.scanneat.presentation.scan.ScanUiState
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.ErrorBanner
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.semanticRed

@Composable
internal fun BoxScope.ScanStateOverlay(
    state: ScanUiState,
    hasCamera: Boolean,
    cameraUnavailable: Boolean,
    bottomNavClearance: Dp,
    language: String,
    healthConditions: Set<String>,
    onRetryScore: () -> Unit,
    onDismissError: () -> Unit,
    onDismissFound: () -> Unit,
    onSaveDetectedMedication: (fr.scanneat.domain.engine.medication.MedicationDbEntry) -> Unit,
    onPickMultiFood: (Long) -> Unit,
) {
    when (val s = state) {
        is ScanUiState.Idle, is ScanUiState.Scanning, is ScanUiState.Success -> Unit
        is ScanUiState.Error -> {
            if (hasCamera && !cameraUnavailable) {
                if (s.needsPhoto) {
                    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + 96.dp)
                        .glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
                        color = SurfaceVariant.copy(alpha = 0.42f), shape = RoundedCornerShape(CardRadius.CONTROL), shadowElevation = 3.dp) {
                        Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CameraAlt, null, tint = AccentCoral)
                            Spacer(Modifier.width(Spacing.S))
                            Text(stringResource(R.string.scan_needs_photo),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            // The IconButton itself must keep Material's default 48dp touch
                            // target (WCAG 2.5.5/2.5.8) - sizing it down to 32dp shrank the
                            // tappable area, not just the glyph. Constrain the icon instead.
                            IconButton(onClick = onDismissError) {
                                Icon(TablerIcons.X, stringResource(R.string.common_close), tint = OnSurface, modifier = Modifier.size(IconSize.Inline))
                            }
                        }
                    }
                } else {
                    ErrorBanner(
                        message     = s.message,
                        modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + 96.dp),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction    = onRetryScore,
                        onDismiss   = onDismissError,
                    )
                }
            } else {
                // Same error surface as the camera path above, but reachable from the
                // no-camera/camera-unavailable fallbacks too - those flows call
                // viewModel.score() straight from manual barcode entry, with no FAB or
                // camera preview underneath, so a scoring failure there still needs
                // somewhere to show up instead of silently going nowhere.
                ErrorBanner(
                    message     = s.message,
                    modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + 24.dp),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction    = onRetryScore,
                    onDismiss   = onDismissError,
                )
            }
        }
        is ScanUiState.MedicationFound -> {
            val hints = remember(s.entry, language, healthConditions) {
                generateMedicationHints(s.entry, healthConditions, language)
            }
            AlertDialog(
                onDismissRequest = onDismissFound,
                containerColor = SurfaceVariant,
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                title = { Text(stringResource(R.string.scan_medication_found_title), color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(stringResource(R.string.scan_medication_found_body, s.entry.name), color = OnBackground.copy(0.7f))
                        FactsCautionsColumn(hints.facts, hints.cautions)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSaveDetectedMedication(s.entry) }) {
                        Text(stringResource(R.string.scan_medication_found_add), color = Teal)
                    }
                },
                dismissButton = { TextButton(onClick = onDismissFound) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
            )
        }
        is ScanUiState.NonConsumableFound -> {
            val hints = remember(s.entry, language) { generateNonConsumableHints(s.entry.category, language) }
            AlertDialog(
                onDismissRequest = onDismissFound,
                containerColor = SurfaceVariant,
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                title = { Text(stringResource(R.string.scan_nonconsumable_found_title), color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(stringResource(R.string.scan_nonconsumable_found_body, s.entry.name, s.entry.brand), color = OnBackground.copy(0.8f))
                        Text(stringResource(R.string.scan_nonconsumable_safety_line), color = semanticRed(), fontWeight = FontWeight.SemiBold)
                        FactsCautionsColumn(hints.facts, hints.cautions)
                    }
                },
                confirmButton = { TextButton(onClick = onDismissFound) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
            )
        }
        is ScanUiState.MultiFoodFound -> {
            MultiFoodFoundDialog(items = s.items, onPick = onPickMultiFood, onDismiss = onDismissFound)
        }
    }
}
