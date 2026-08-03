package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 *
 * art-direction-engine §EMPTY/§BAN: the icon tint was a flat neutral
 * OnBackground-at-50% — the literal "gray empty state" pattern this skill's
 * own blacklist names, on the one component every list-empty moment in the
 * app shares. Tinted with the app's own accent instead, kept restrained
 * (still just icon+text) per this file's own doc comment above.
 *
 * Audit F5/F31 (docs/design-audit-step11-icons-illustration-dataviz-copy.md):
 * every empty state in the app was icon+text with zero illustration or
 * ambient life behind it — the least resolved of the app's contact points.
 * NOT a mascot or figurative illustration (explicitly ruled out) — just the
 * same breathing glow behind the icon that the score ring already carries
 * (rememberBreathingPulse, docs/design-audit-art-direction-brief.md), so an
 * empty list reads as "quietly alive, nothing to show yet" rather than dead.
 */
@Composable
fun EmptyListState(icon: ImageVector, message: String, ctaLabel: String? = null, onCta: (() -> Unit)? = null) {
    val breathingPulse = rememberBreathingPulse()
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(IconSize.EmptyState * 2.2f)
                        .background(
                            Brush.radialGradient(
                                listOf(AccentCoral.copy(alpha = 0.14f * breathingPulse), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
                Icon(icon, null, tint = AccentCoral.copy(0.45f), modifier = Modifier.size(IconSize.EmptyState))
            }
            // §E3 contrast audit: OnBackground.copy(0.5f) only reaches 3.18:1 on
            // Light (below 4.5:1 AA) - TextSecondary is theme-tuned to clear AA
            // in every theme, and this is the app's one shared empty-state
            // component, so the fix reaches every empty state at once.
            Text(message, color = TextSecondary)
            if (ctaLabel != null && onCta != null) {
                ScanEatPrimaryButton(onClick = onCta) { Text(ctaLabel) }
            }
        }
    }
}
