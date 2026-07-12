package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Guarantees the Material/WCAG-recommended 48x48dp minimum touch target on a
 * tappable composable whose visual footprint (icon, glyph, small chip) is
 * smaller than that — e.g. a bare Text("+") or Text("−") used as a stepper
 * button. `IconButton` already does this internally, but plain `clickable`
 * modifiers on `Text`/`Box`/custom shapes do not, so small glyph-only controls
 * end up with touch targets accessibility guidelines (and users with motor
 * impairments) require. Apply before `.clickable {}` so the enlarged box is
 * itself the hit-test area; padding/visual sizing can still be layered after.
 */
fun Modifier.minTouchTarget(): Modifier = this.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
