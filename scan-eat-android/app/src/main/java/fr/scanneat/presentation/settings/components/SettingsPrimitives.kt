package fr.scanneat.presentation.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.ScanEatPrimaryButton
import fr.scanneat.presentation.ui.theme.Spacing

/** Reusable, stateless layout primitives local to the Settings screen. */

/**
 * Each Settings section now renders as its own [ScanEatCard], the same glass
 * card primitive Dashboard's widgets use, instead of a bare title-plus-Column
 * sitting directly on the ambient background — previously every section on
 * this screen (and Profile's) was the one place in the app that never got
 * the card treatment everywhere else already has.
 */
@Composable
internal fun SettingsSection(title: String, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    ScanEatCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                // A 17-section settings list with title-text-only headers had no
                // scanning anchors - this was the one confirmed gap in an otherwise
                // near-zero-drift screen (every section already shared this same
                // wrapper). icon is optional so a section can still omit it, but
                // every section below now supplies one.
                if (icon != null) {
                    Icon(icon, null, tint = OnBackground.copy(0.6f), modifier = Modifier.size(IconSize.Inline))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            }
            content()
        }
    }
}

@Composable
internal fun SaveButtonRow(saved: Boolean, onSave: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
        ScanEatPrimaryButton(onClick = onSave) {
            Text(stringResource(R.string.common_save))
        }
        AnimatedVisibility(visible = saved) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Icon(Icons.Default.Check, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Inline))
                Text(stringResource(R.string.settings_saved_confirmation), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
            }
        }
    }
}
