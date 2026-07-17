package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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
import fr.scanneat.presentation.ui.theme.semanticRed

/**
 * Self-contained lightbulb entry point for the hint panel - owns its own
 * open/closed state and shows the panel on tap. Previously ResultScreen was
 * the only place the hint panel was reachable from, via a plain always-amber
 * icon with no signal of what was behind it; a user with a declared allergy
 * or diet had to tap in blind to discover an allergen/diet-violation risk was
 * even there. This turns red with a count badge whenever [hints] carries a
 * risk, and its contentDescription speaks the actual counts to TalkBack
 * instead of a generic "view information."
 */
@Composable
fun HintIconButton(hints: ProductHints, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    var showHints by remember { mutableStateOf(false) }
    val hasRisks = hints.risks.isNotEmpty()
    val totalCount = hints.benefits.size + hints.risks.size + hints.facts.size + hints.pairWell.size + hints.avoidPairing.size
    val baseLabel = stringResource(R.string.hint_cd_open)
    val cd = when {
        hasRisks -> stringResource(R.string.hint_cd_open_with_risks, baseLabel, pluralStringResource(R.plurals.hint_cd_risks_count, hints.risks.size, hints.risks.size))
        totalCount > 0 -> stringResource(R.string.hint_cd_open_with_count, baseLabel, pluralStringResource(R.plurals.hint_cd_info_count, totalCount, totalCount))
        else -> baseLabel
    }
    BadgedBox(
        modifier = modifier,
        badge = {
            if (hasRisks) Badge(containerColor = semanticRed()) { Text("${hints.risks.size}") }
        },
    ) {
        IconButton(onClick = { showHints = true }) {
            Icon(Icons.Default.Lightbulb, cd, tint = if (hasRisks) semanticRed() else semanticAmber(), modifier = Modifier.size(iconSize))
        }
    }
    if (showHints) {
        HintPanel(hints = hints, onDismiss = { showHints = false })
    }
}

/** The "💡" hint panel — benefits / risks / pairings / facts, each traced to a concrete product field (see ProductHints.kt). */
@Composable
fun HintPanel(hints: ProductHints, onDismiss: () -> Unit) {
    val green = semanticGreen()
    val amber = semanticAmber()
    val neutral = OnBackground.copy(0.7f)
    val isEmpty = hints.benefits.isEmpty() && hints.risks.isEmpty() && hints.facts.isEmpty() &&
        hints.pairWell.isEmpty() && hints.avoidPairing.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.hint_panel_title), color = OnBackground) },
        text = {
            // A long ingredient list can produce enough benefit/risk/fact lines to
            // overflow AlertDialog's unconstrained text slot on a small screen —
            // scroll within a capped height instead of letting the dialog grow
            // past the viewport with no way to reach the close button.
            Column(
                modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                // Was a fixed pairwise chain of "is the section before me AND am I
                // non-empty" checks - fine for 3 sections, unreadable once pairWell/
                // avoidPairing made it 5. A running "was anything shown yet" flag
                // scales to any section count without combinatorial conditions.
                var shownAny = false
                @Composable fun section(title: String, lines: List<String>, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
                    if (lines.isEmpty()) return
                    if (shownAny) HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
                    HintSection(title, lines, accent, icon)
                    shownAny = true
                }
                section(stringResource(R.string.hint_section_benefits), hints.benefits, green, Icons.Default.ThumbUp)
                section(stringResource(R.string.hint_section_risks), hints.risks, amber, Icons.Default.WarningAmber)
                section(stringResource(R.string.hint_section_pair_well), hints.pairWell, green, Icons.Default.Restaurant)
                section(stringResource(R.string.hint_section_avoid_pairing), hints.avoidPairing, amber, Icons.Default.Block)
                section(stringResource(R.string.hint_section_facts), hints.facts, neutral, Icons.Default.Lightbulb)
                if (isEmpty) {
                    Text(stringResource(R.string.hint_panel_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) }
        },
    )
}

/**
 * Shared facts/cautions layout for scan-result dialogs that aren't full
 * food scoring — MedicationFound and NonConsumableFound in ScanScreen.kt
 * both use this instead of duplicating the section/scroll/divider
 * plumbing HintPanel already has above.
 */
@Composable
fun FactsCautionsColumn(facts: List<String>, cautions: List<String>) {
    val amber = semanticAmber()
    val neutral = OnBackground.copy(0.7f)
    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        if (cautions.isNotEmpty()) {
            HintSection(stringResource(R.string.hint_section_risks), cautions, amber, Icons.Default.WarningAmber)
        }
        if (cautions.isNotEmpty() && facts.isNotEmpty()) {
            HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
        }
        if (facts.isNotEmpty()) {
            HintSection(stringResource(R.string.hint_section_facts), facts, neutral, Icons.Default.Lightbulb)
        }
    }
}

@Composable
internal fun HintSection(title: String, lines: List<String>, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier = Modifier.padding(bottom = Spacing.S)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(0.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        }
        lines.forEach { line ->
            Row(modifier = Modifier.padding(top = Spacing.XS)) {
                Text("•  ", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
                // weight(1f) so a wrapped second line stays within the dialog's
                // width instead of the un-weighted Text being measured at its
                // natural (unwrapped) width and overflowing the Row.
                Text(line, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f), modifier = Modifier.weight(1f))
            }
        }
    }
}
