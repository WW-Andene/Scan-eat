package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's one primary-button recipe — accent fill (AccentCoral by
 * default), 12dp corners, black semibold label — previously hand-copied
 * across ~8 files with visible drift (two call sites silently reverted to
 * Material's default pill shape). The Part B4/B10 Layer-3 fix: the shape
 * now only needs to change here, not at every call site. [containerColor]
 * lets sub-brands (e.g. the biolism Gold accent) reuse the same shape/label
 * recipe instead of re-deriving it by hand.
 *
 * art-direction-engine audit, §BUTTONS: two states were left at Material's
 * defaults instead of this button's own accent — exactly the "one state
 * that gives away the template" pattern the audit calls out.
 * disabledContainerColor previously fell back to Material's generic
 * onSurface-at-12%-alpha neutral gray fill regardless of whether the button
 * was coral or Biolism gold — the state a user sees constantly (every
 * disabled Save/Add button app-wide) carried zero brand identity. Now
 * derived from the button's own [containerColor] instead. Active/press
 * feedback was also missing beyond Material's default ripple — this app
 * already built [pressScale] for exactly this (see Motion.kt) and applies
 * it to every tappable ScanEatCard, but never to its own button primitive.
 */
@Composable
fun ScanEatPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AccentCoral,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(interactionSource),
        enabled = enabled,
        interactionSource = interactionSource,
        shape   = RoundedCornerShape(CardRadius.CONTROL),
        // contentColor/disabledContentColor (not a hardcoded Text style) is what
        // actually flows through LocalContentColor per enabled state - the previous
        // ProvideTextStyle(... color = Color.Black ...) below set an explicit,
        // non-Unspecified Text color that always wins over LocalContentColor, so
        // Button's own correctly-dimmed disabledContentColor never had any effect:
        // a disabled button's label stayed fully opaque black, with only the
        // container dimming.
        colors  = ButtonDefaults.buttonColors(
            containerColor         = containerColor,
            contentColor           = Color.Black,
            disabledContainerColor = containerColor.copy(alpha = 0.28f),
            disabledContentColor   = Color.Black.copy(alpha = 0.38f),
        ),
    ) {
        ProvideTextStyle(LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)) {
            content()
        }
    }
}

/**
 * The app's one secondary/outlined-button recipe — 12dp corners to match
 * ScanEatPrimaryButton, so an outlined action next to a primary one doesn't
 * silently drift back to Material's default pill shape. Previously
 * hand-copied 3x within SessionControlsCard.kt alone.
 */
@Composable
fun ScanEatOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape   = RoundedCornerShape(CardRadius.CONTROL),
        content = content,
    )
}
