package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R

/**
 * The app's one error language — FlagRed at 15% alpha + icon + text, the
 * same treatment DietVetoBanner already used. Replaces three unreconciled
 * systems that all rendered the same underlying concept (ScanScreen's
 * Material colorScheme.error/errorContainer; SettingsScreen's bare Text
 * with no icon or container). An allergen/diet-safety error deserves the
 * same trustworthy treatment as a scan-network error, not a different one
 * depending on which screen it's on.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = semanticRed().copy(alpha = 0.15f), shape = RoundedCornerShape(CardRadius.CONTROL)) {
        Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = semanticRed())
            Spacer(Modifier.width(8.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = OnBackground)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = semanticRed()) }
            }
            if (onDismiss != null) {
                // Was Modifier.size(32.dp), shrinking the tap target below the
                // 48dp WCAG/Material minimum — IconButton's own default already
                // provides that minimum, so overriding it here undid it.
                IconButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = OnBackground.copy(0.7f))
                }
            }
        }
    }
}
