package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import compose.icons.TablerIcons
import compose.icons.tablericons.Star
import compose.icons.tablericons.X
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing

/**
 * Bumped whenever a batch of features warrants a one-time Dashboard
 * spotlight - app-audit §F found Pantry/Symptom-journal/Pregnancy-tracking
 * shipped with zero notice to existing users (no onboarding mention, no
 * "what's new"), unlike every other feature in this app which at minimum
 * gets a Dashboard tile a user stumbles onto. Bump this constant (and its
 * matching strings) the next time a similarly invisible feature ships.
 */
object WhatsNewContent {
    const val CURRENT_VERSION = 1
}

/**
 * One-time dismissible Dashboard spotlight for features a user could easily
 * never discover otherwise - see [WhatsNewContent]'s own doc comment. Shown
 * only while [seenVersion] is behind [WhatsNewContent.CURRENT_VERSION];
 * dismissing persists the current version so it never reappears for
 * features already announced, but a future bump surfaces again for
 * whatever's new at that point.
 */
@Composable
internal fun WhatsNewCard(onDismiss: () -> Unit) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Icon(TablerIcons.Star, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Compact))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
                    Text(
                        stringResource(R.string.dashboard_whats_new_title),
                        style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.dashboard_whats_new_body),
                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f),
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(TablerIcons.X, stringResource(R.string.common_close), tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Tiny))
            }
        }
    }
}
