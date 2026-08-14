package fr.scanneat.presentation.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.scan.ComparisonResult
import fr.scanneat.data.repository.scan.diffSnapshots
import fr.scanneat.data.repository.scan.toScoreSnapshot
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.dialogContainerColor
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed

/**
 * User-requested: "mode comparer 2 scans côte à côte" - unlike
 * ComparisonRepository's arm()/compare() pair (a "scan A then scan B"
 * sequence, auto-triggered and consumed the moment the second live scan
 * lands), this picks any two ALREADY-scanned products from History and
 * shows them as two side-by-side columns rather than a single "A → B" diff
 * line - reuses the same diffSnapshots() pure function so the delta/added-
 * removed-flags logic stays identical between both entry points.
 */
@Composable
fun CompareScansDialog(items: List<ScanResult>, onDismiss: () -> Unit) {
    var selectedA by remember { mutableStateOf<ScanResult?>(null) }
    var selectedB by remember { mutableStateOf<ScanResult?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.history_compare_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ScanPickerColumn(
                        label = stringResource(R.string.history_compare_product_a),
                        items = items, selected = selectedA, onSelect = { selectedA = it },
                        modifier = Modifier.weight(1f),
                    )
                    ScanPickerColumn(
                        label = stringResource(R.string.history_compare_product_b),
                        items = items, selected = selectedB, onSelect = { selectedB = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                val a = selectedA
                val b = selectedB
                if (a != null && b != null) {
                    val diff = remember(a, b) { diffSnapshots(a.toScoreSnapshot(), b.toScoreSnapshot()) }
                    CompareSideBySideResult(diff)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) }
        },
    )
}

@Composable
private fun ScanPickerColumn(
    label: String,
    items: List<ScanResult>,
    selected: ScanResult?,
    onSelect: (ScanResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f), fontWeight = FontWeight.SemiBold)
        if (selected != null) {
            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.12f)) {
                Text(
                    selected.product.name,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.XS),
                    style = MaterialTheme.typography.bodySmall, color = AccentCoral, maxLines = 2,
                )
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.history_search_placeholder), style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        val matches = remember(query, items) {
            if (query.isBlank()) emptyList() else items.filter { it.product.name.contains(query, ignoreCase = true) }.take(8)
        }
        if (matches.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                items(matches, key = { it.dbId }) { result ->
                    Text(
                        "${result.product.name} · ${result.audit.grade.label}",
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSelect(result); query = "" }
                            .padding(vertical = Spacing.XS),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareSideBySideResult(diff: ComparisonResult) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CompareColumn(diff.prev.name, diff.prev.score, diff.prev.grade, Modifier.weight(1f))
            CompareColumn(diff.next.name, diff.next.score, diff.next.grade, Modifier.weight(1f))
        }
        val delta = diff.scoreDelta
        val dColor = when { delta > 0 -> semanticGreen(); delta < 0 -> semanticRed(); else -> OnBackground.copy(0.5f) }
        val dSign = if (delta > 0) "+" else ""
        Text(
            stringResource(R.string.result_comparison_score, "$dSign$delta"),
            style = MaterialTheme.typography.bodyMedium, color = dColor, fontWeight = FontWeight.Bold,
        )
        if (diff.addedRedFlags.isNotEmpty()) {
            Text(stringResource(R.string.result_comparison_new_issues, diff.addedRedFlags.joinToString()), style = MaterialTheme.typography.bodySmall, color = semanticRed())
        }
        if (diff.removedRedFlags.isNotEmpty()) {
            Text(stringResource(R.string.result_comparison_resolved_issues, diff.removedRedFlags.joinToString()), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
    }
}

@Composable
private fun CompareColumn(name: String, score: Int, grade: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f), maxLines = 2)
        Text(grade, style = MaterialTheme.typography.headlineSmall, color = OnSurface, fontWeight = FontWeight.Bold)
        Text("$score/100", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
    }
}
