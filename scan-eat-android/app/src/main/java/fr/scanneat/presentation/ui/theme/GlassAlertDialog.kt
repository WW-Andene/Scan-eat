package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * User-reported: every AlertDialog in the app used a near-opaque fill
 * (SurfaceVariant.copy(alpha = 0.94f/0.85f), borrowed from DropdownMenu's
 * popup treatment) instead of literally being the app's own ScanEatCard -
 * an approximation of the card look rather than the same component.
 *
 * A first attempt tried to fix this by hand-rolling a custom Dialog with its
 * own tinted scrim (disabling the platform window dim via the
 * DialogWindowProvider escape hatch) so ScanEatCard's real low-alpha fill
 * would stay legible - that broke centering/sizing badly (untestable without
 * a device) and was reverted. This version is deliberately much smaller:
 * `BasicAlertDialog` is Material3's own bare Dialog primitive - modal
 * window, scrim, back/outside-tap dismiss, all standard library code, no
 * custom window manipulation - with zero built-in Surface/styling of its
 * own. [ScanEatCard] is placed directly inside it as the entire visible
 * body, so the dialog **is** the same card component every screen already
 * uses (same shadow, same glassSheen, same fill alpha/color), not a
 * separate re-implementation of its look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(CardRadius.PROMINENT),
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        ScanEatCard(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = shape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.L),
        ) {
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
