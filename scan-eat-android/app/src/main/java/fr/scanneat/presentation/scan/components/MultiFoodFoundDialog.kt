package fr.scanneat.presentation.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.gradeColor

/**
 * identifyMultiFromPhotos() success dialog - a plate photo returned several
 * distinct foods, each already scored and persisted (see ScanViewModel's
 * ScanUiState.MultiFoodFound doc comment), so this is purely a picker: tapping
 * a row hands its scan_history id to [onPick], which navigates straight to the
 * existing Result screen exactly like the single-item Success path does.
 * Styled like the sibling MedicationFound/NonConsumableFound AlertDialogs above,
 * with a pickable-row list modeled on SuggestRecipesDialog's LazyColumn-in-an-
 * AlertDialog pattern and a grade badge matching ScanHistoryScreen's ScanHistoryRow.
 */
@Composable
internal fun MultiFoodFoundDialog(
    items: List<Pair<ScanResult, Long>>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = 0.94f),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.scan_identify_multi_found_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.scan_identify_multi_found_body), color = OnBackground.copy(0.7f))
                HorizontalDivider(color = OnBackground.copy(0.1f))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    itemsIndexed(items, key = { _, (_, persistedId) -> persistedId }) { _, (result, persistedId) ->
                        val grade = gradeColor(result.audit.grade)
                        Surface(
                            shape = RoundedCornerShape(CardRadius.CONTROL),
                            color = OnBackground.copy(0.05f),
                            onClick = { onPick(persistedId) },
                            modifier = Modifier.fillMaxWidth()
                                .shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
                                .clip(RoundedCornerShape(CardRadius.CONTROL)),
                            // design-aesthetic-audit §DH: matching the dialog
                            // list-row elevation established elsewhere - had none.
                            shadowElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.SM),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                            ) {
                                Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = grade.copy(0.2f)) {
                                    Text(
                                        result.audit.grade.label,
                                        modifier = Modifier.padding(horizontal = Spacing.SM, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = grade, fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        result.product.name, style = MaterialTheme.typography.bodyMedium,
                                        color = OnSurface, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("${result.audit.score}", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
