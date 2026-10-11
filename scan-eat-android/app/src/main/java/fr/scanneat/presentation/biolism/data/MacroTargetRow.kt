package fr.scanneat.presentation.biolism.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatDivider
import fr.scanneat.presentation.ui.theme.SeparatorExtraLight
import fr.scanneat.presentation.ui.theme.Spacing
import java.util.Locale

@Composable
internal fun MacroTargetRow(label: String, grams: Double, unit: String, note: String, color: Color, kcal: Double? = null) {
    // Spacing.XS - same reasoning as HormoneRow: MacroTargetsCard renders this
    // next to InfoRow (Spacing.XS), and the two previously had a visibly
    // different vertical rhythm (8.dp vs 4.dp) despite being the same "label/
    // value row" shape.
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.1f $unit".format(Locale.US, grams), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
            if (kcal != null) {
                Text("%.0f kcal".format(Locale.US, kcal), style = MaterialTheme.typography.labelSmall, color = color.copy(0.6f))
            }
        }
    }
    ScanEatDivider(color = SeparatorExtraLight)
}
