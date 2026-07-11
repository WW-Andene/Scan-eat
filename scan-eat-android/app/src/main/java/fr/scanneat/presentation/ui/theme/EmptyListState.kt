package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Centered icon + message for a LazyColumn's empty-state item — the same
 * layout (40dp padding, 40dp icon, 8dp spacing, OnBackground 0.5f) was
 * independently duplicated in CustomFoodScreen, TemplatesScreen, and
 * RecipesScreen with only the icon/message varying. [ctaLabel]/[onCta] are
 * optional — only screens with a clear single next step (e.g. "add a
 * recipe") should pass them; an empty state with no obvious action stays
 * icon+text only, which is still a complete, restrained state.
 */
@Composable
fun EmptyListState(icon: ImageVector, message: String, ctaLabel: String? = null, onCta: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.EmptyState))
            Text(message, color = OnBackground.copy(0.5f))
            if (ctaLabel != null && onCta != null) {
                ScanEatPrimaryButton(onClick = onCta) { Text(ctaLabel) }
            }
        }
    }
}
