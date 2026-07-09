package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Teal

@Composable
internal fun LiveWeightCard(liveWeight: Double, baseWeight: Double, fatLostKg: Double, glycoLostKg: Double, ketosisOn: Boolean) {
    val color = if (ketosisOn) Teal else Gold
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(0.04f),
        border = BorderStroke(1.dp, color.copy(0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Poids en direct", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(if (ketosisOn) "9 300 kcal/kg (triglycérides)" else "Wishnofsky · 7 700 kcal/kg",
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(String.format("%.4f", liveWeight), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W500), color = color)
                Text("kg", style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.5f))
                val deltaG = (liveWeight - baseWeight) * 1000.0
                Text("Δ ${String.format("%.4f", deltaG)} g", style = MaterialTheme.typography.labelSmall, color = color.copy(0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Base: ${String.format("%.3f", baseWeight)} kg", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                Text("−", color = OnBackground.copy(0.3f))
                Text("${String.format("%.4f", fatLostKg * 1000)} g graisses", style = MaterialTheme.typography.labelSmall, color = color.copy(0.8f))
                if (ketosisOn && glycoLostKg > 0) {
                    Text("−", color = OnBackground.copy(0.3f))
                    Text("${String.format("%.2f", glycoLostKg * 1000)} g glycogène+H₂O", style = MaterialTheme.typography.labelSmall, color = Gold.copy(0.8f))
                }
            }
        }
    }
}
