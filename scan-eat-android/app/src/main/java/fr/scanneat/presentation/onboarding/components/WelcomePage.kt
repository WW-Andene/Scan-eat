package fr.scanneat.presentation.onboarding.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Barcode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * art-direction-engine §IMPACT/§BRIEF: this is literally the first thing a
 * brand-new user ever sees, yet the icon was a bare, unframed 64dp glyph
 * with no atmosphere at all - no glow, no glass frame - while every other
 * screen in the app carries the glassSheen/ambientGloom glow identity. The
 * app's highest-leverage first-impression moment looked like a stock
 * Material onboarding template. Reuses the same radial-glow-behind-an-icon
 * technique already proven in HydrationRingAndControls rather than
 * inventing new visual code.
 */
@Composable
internal fun ColumnScope.WelcomePage(onNext: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    Brush.radialGradient(listOf(AccentCoral.copy(alpha = 0.22f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Icon(TablerIcons.Barcode, null, tint = AccentCoral, modifier = Modifier.size(64.dp))
    }
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
