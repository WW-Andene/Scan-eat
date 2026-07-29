package fr.scanneat.presentation.onboarding.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ModeCard(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(CardRadius.CONTROL),
        color   = if (selected) AccentCoral.copy(0.15f) else SurfaceVariant,
        // art-direction-engine §CARDS: this used to borrow ButtonDefaults'
        // outlined-button border, which resolves to Material's generic
        // colorScheme.outline gray - unrelated to the AccentCoral fill this
        // card already commits to when selected. A selected card showed a
        // coral tint with a plain gray border around it.
        border  = if (selected) BorderStroke(1.5.dp, AccentCoral) else null,
        modifier = Modifier.fillMaxWidth(),
        // design-aesthetic-audit §DH: this standalone selectable card had no
        // shadowElevation at all, unlike the rest of the card system.
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            // onClick = null: the whole Surface above is already clickable (onClick = onClick) —
            // a second independent actionable control nested inside it is a real screen-reader/
            // interaction conflict (two actionable elements claiming the same tap), not just redundant.
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AccentCoral))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            }
        }
    }
}
