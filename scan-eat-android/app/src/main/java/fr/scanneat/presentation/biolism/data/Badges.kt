package fr.scanneat.presentation.biolism.data

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Violet

@Composable
internal fun TealBadge(text: String) = Badge(text, Teal)
@Composable
internal fun GoldBadge(text: String) = Badge(text, Gold)
@Composable
internal fun VioletBadge(text: String) = Badge(text, Violet)

@Composable
internal fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(CardRadius.BADGE), color = color.copy(0.12f), border = BorderStroke(1.dp, color.copy(0.25f))) {
        Text(text, modifier = Modifier.padding(horizontal = Spacing.SM, vertical = Spacing.XS),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
