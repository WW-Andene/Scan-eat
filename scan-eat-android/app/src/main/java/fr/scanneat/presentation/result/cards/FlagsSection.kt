package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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

@Composable
internal fun FlagsSection(redFlags: List<String>, greenFlags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Icon(if (isRed) Icons.Default.Warning else Icons.Default.CheckCircle, null,
            tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnBackground, modifier = Modifier.weight(1f))
    }
}
