package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen

/** The "💡" hint panel — benefits / risks / facts, each traced to a concrete product field (see ProductHints.kt). */
@Composable
fun HintPanel(hints: ProductHints, onDismiss: () -> Unit) {
    val green = semanticGreen()
    val amber = semanticAmber()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.hint_panel_title), color = OnBackground) },
        text = {
            Column(modifier = Modifier.widthIn(max = 320.dp)) {
                if (hints.benefits.isNotEmpty()) {
                    HintSection(stringResource(R.string.hint_section_benefits), hints.benefits, green)
                }
                if (hints.risks.isNotEmpty()) {
                    HintSection(stringResource(R.string.hint_section_risks), hints.risks, amber)
                }
                if (hints.facts.isNotEmpty()) {
                    HintSection(stringResource(R.string.hint_section_facts), hints.facts, OnBackground.copy(0.6f))
                }
                if (hints.benefits.isEmpty() && hints.risks.isEmpty() && hints.facts.isEmpty()) {
                    Text(stringResource(R.string.hint_panel_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) }
        },
    )
}

@Composable
private fun HintSection(title: String, lines: List<String>, accent: androidx.compose.ui.graphics.Color) {
    Column(modifier = Modifier.padding(bottom = Spacing.S)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        lines.forEach { line ->
            Row(modifier = Modifier.padding(top = Spacing.XS)) {
                Text("• ", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
                Text(line, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
            }
        }
    }
}
