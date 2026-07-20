package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.util.formatDecimal

/** Reusable, stateless form primitives local to the Biolism profile edit screen. */

// Wraps every section in the app's one card primitive (glassSheen + grey
// SurfaceVariant fill, same as every other card in the app) - this used to be
// a bare Column with no background at all, so Identity/Activity/Ethnicity/
// Circumferences/Cycle all rendered directly on the screen background while
// the "Overview" section above them (which already used ScanEatCard) was the
// only "real" card on this screen. ScanEatCard's content lambda has the exact
// same ColumnScope signature this already had, so every call site is unaffected.
@Composable
internal fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ScanEatCard(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Gold, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
internal fun OverviewRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        Text(value ?: "—", style = MaterialTheme.typography.bodySmall, color = if (value != null) OnBackground else OnBackground.copy(0.3f), fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun BioInputUnit(
    metricLabel: String,
    imperialLabel: String,
    metricValue: String,
    useImperial: Boolean,
    toImperial: (Double) -> Double,
    toMetric: (Double) -> Double,
    onMetricChange: (String) -> Unit,
) {
    // On a French-locale device (this app's default), the decimal keyboard types a
    // comma, but String.toDoubleOrNull() only ever accepts a period — every keystroke
    // with a decimal silently reset the field. Normalize comma->period before parsing,
    // and force Locale.US when formatting back so the displayed/stored value is always
    // period-based and re-parses correctly on the next edit.
    val display = if (useImperial) metricValue.toDoubleOrNull()?.let { toImperial(it).formatDecimal() } ?: "" else metricValue
    OutlinedTextField(
        value = display,
        onValueChange = { input ->
            val d = input.replace(',', '.').toDoubleOrNull()
            onMetricChange(when {
                input.isBlank() -> ""
                d == null -> metricValue
                useImperial -> toMetric(d).formatDecimal(2)
                else -> input
            })
        },
        label = { Text(if (useImperial) imperialLabel else metricLabel, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.18f),
            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
        ),
    )
}

@Composable
internal fun BioInput(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.18f),
            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
        ),
    )
}
