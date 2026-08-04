package fr.scanneat.presentation.result.cards

import compose.icons.tablericons.CircleCheck
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.presentation.ui.theme.IconSize

@Composable
internal fun FlagsSection(redFlags: List<String>, greenFlags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        redFlags.forEach { FlagRow(it, true) }
        greenFlags.forEach { FlagRow(it, false) }
    }
}

@Composable
private fun FlagRow(text: String, isRed: Boolean) {
    val color = if (isRed) semanticRed() else semanticGreen()
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius.CONTROL))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Icon(if (isRed) TablerIcons.AlertTriangle else TablerIcons.CircleCheck, null,
            tint = color, modifier = Modifier.size(IconSize.Small))
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnBackground, modifier = Modifier.weight(1f))
    }
}
