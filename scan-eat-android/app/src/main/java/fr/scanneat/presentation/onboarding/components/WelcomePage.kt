package fr.scanneat.presentation.onboarding.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ColumnScope.WelcomePage(onNext: () -> Unit) {
    Icon(Icons.Rounded.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(64.dp))
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = OnBackground, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyMedium,
        color = OnBackground.copy(0.7f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    ScanEatPrimaryButton(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.onboarding_start_button), style = MaterialTheme.typography.titleMedium) }
}
