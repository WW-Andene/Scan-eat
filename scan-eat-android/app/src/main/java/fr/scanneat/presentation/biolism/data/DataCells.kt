package fr.scanneat.presentation.biolism.data

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.GLOW_BORDER_ALPHA
import fr.scanneat.presentation.ui.theme.GLOW_HAZE_ALPHA
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.OnBackgroundMuted

/** Small display-primitive helpers shared across the Biolism Data screen's cards. */

// ── 2-column grid utility (see MetCellGrid below)
@Composable
internal fun MetCellGrid(items: List<Triple<String, String, String>>, accents: List<Color> = emptyList()) {
    items.chunked(2).forEachIndexed { row, pair ->
        // bottom padding now matches this Row's own horizontal spacedBy(Spacing.S) -
        // previously 6.dp against an 8.dp column gap, so a 2-column grid of cells
        // had a visibly tighter gap between rows than between columns.
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.S), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            pair.forEachIndexed { col, (label, value, sub) ->
                val accent = accents.getOrNull(row * 2 + col) ?: OnBackground
                MetCell(label, value, sub, accent, Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MetCell(label: String, value: String, sub: String, accent: Color = OnBackground, modifier: Modifier = Modifier.fillMaxWidth()) {
    Surface(shape = RoundedCornerShape(8.dp), color = OnBackground.copy(0.04f), modifier = modifier) {
        // Spacing.S (8dp), not the literal 8.dp this had - one dp off the actual
        // scale for no reason, next to every other Biolism cell/row padding here.
        Column(Modifier.padding(Spacing.S)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackgroundMuted, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.SemiBold)
            // Bumped from 0.3f - a UI/UX audit flagged this as real informational
            // content (not decorative) rendered too faint against the dark surface.
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String, note: String, color: Color = OnBackground) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            // Bumped from 0.3f - a UI/UX audit flagged this as real informational
            // content (not decorative) rendered too faint against the dark surface.
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Label(text: String, color: Color = OnBackgroundMuted) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.padding(bottom = Spacing.S))
}

@Composable
internal fun TintedPanel(color: Color, content: @Composable ColumnScope.() -> Unit) {
    // Alpha steps were ad hoc (0.06f/0.15f) instead of the phi-derived Haze
    // (0.10) / Border (0.162) alpha family every named Gold/Teal/Violet
    // token elsewhere in this module uses - this is the shared container
    // ~9 cards route through, so it was silently reintroducing an
    // independent alpha scale across most of the Data tab regardless of
    // which accent color a card passed in.
    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = color.copy(GLOW_HAZE_ALPHA),
        border = BorderStroke(2.dp, color.copy(GLOW_BORDER_ALPHA)), modifier = Modifier.fillMaxWidth()) {
        // Inner spacing now matches this Column's own Spacing.S padding, instead of
        // a literal 6.dp that didn't agree with the container it sits inside.
        Column(Modifier.padding(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S), content = content)
    }
}
