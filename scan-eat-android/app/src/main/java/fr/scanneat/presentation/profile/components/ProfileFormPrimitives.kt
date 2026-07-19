package fr.scanneat.presentation.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.CardRadius

/** Reusable, stateless form primitives local to the Profile screen. */

@Composable
internal fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
    }
}

// Wraps every section in the app's one card primitive (glassSheen + grey
// SurfaceVariant fill, same as every other card in the app) - this used to be
// a bare Column with no background at all, so Identity/Body/Measurements/
// Activity/Allergens/Conditions/Diet all rendered directly on the screen
// background while the BMI/TDEE preview card above them (a hand-rolled
// Surface(color = SurfaceVariant)) was the only "real" card on this screen,
// making the rest of the page look visually inconsistent with the rest of
// the app. ScanEatCard's content lambda has the exact same ColumnScope
// signature this already had, so every call site is unaffected.
@Composable
internal fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ScanEatCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
internal fun OutlinedInput(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValue,
        label         = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier      = modifier,
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape  = RoundedCornerShape(CardRadius.CONTROL),
        colors = scanEatTextFieldColors(),
    )
}
