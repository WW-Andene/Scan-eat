package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * User-reported: every AlertDialog in the app used a near-opaque fill
 * (SurfaceVariant.copy(alpha = 0.94f), borrowed from DropdownMenu's popup
 * treatment) instead of ScanEatCard's actual glass standard
 * (SurfaceVariant.copy(alpha = 0.24f) in dark theme) - visibly flatter/more
 * opaque than every card in the app, which reads as "not the same glass."
 *
 * The reason the higher alpha was used in the first place: Material3's
 * AlertDialog renders in its own separate Android window over a flat black
 * scrim, so there's no colorful screen content behind it for a translucent
 * fill to blend with - at ScanEatCard's low alpha, dialog text would lose
 * contrast against plain black. This composable closes that gap properly
 * instead of just picking a compromise alpha: it replaces the platform's
 * flat black scrim with the app's own warm-tinted one (disabling the
 * window's default dim via the DialogWindowProvider escape hatch, then
 * painting the tint as an ordinary Compose background so it can carry the
 * app's own color), so the dialog's fill can drop to the same translucent
 * level as a real card and still stay legible - it's blending with the
 * app's atmosphere instead of neutral darkness.
 *
 * Deliberately NOT a drop-in AlertDialog replacement at the type level -
 * title/text/buttons are plain composable slots rendered in a fixed layout
 * (title, then text, then a bottom-aligned button row) rather than
 * reproducing Material3's exact AlertDialog internals, since this app's own
 * call sites only ever use that same simple shape.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(CardRadius.PROMINENT),
    containerColor: Color = SurfaceVariant.copy(alpha = if (isLightBackground()) 0.75f else 0.24f),
    scrimTint: Color = AccentCoral,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        // The platform's own dim draws behind this Box regardless of what's
        // composed here - zeroing it out is the only way to replace it with
        // a tinted scrim instead of stacking a second dim on top of the first.
        SideEffect { dialogWindowProvider?.window?.setDimAmount(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .background(scrimTint.copy(alpha = 0.08f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .padding(horizontal = Spacing.XL)
                    .widthIn(max = 560.dp)
                    .shadow(elevation = 6.dp, shape = shape, ambientColor = ShadowTint, spotColor = ShadowTint)
                    .clip(shape)
                    .glassSheen(edgeAlpha = 0.22f, shape = shape, glowAlpha = 0.05f)
                    // Swallows taps so they don't fall through to the scrim's
                    // own dismiss-on-click-outside behavior above.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                shape = shape,
                color = containerColor,
                shadowElevation = 0.dp,
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.L)) {
                    title?.let {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall, it)
                        Spacer(Modifier.height(Spacing.M))
                    }
                    text?.let {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, it)
                        Spacer(Modifier.height(Spacing.L))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}
