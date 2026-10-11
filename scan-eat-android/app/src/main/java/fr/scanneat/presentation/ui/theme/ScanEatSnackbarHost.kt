package fr.scanneat.presentation.ui.theme

import compose.icons.TablerIcons
import compose.icons.tablericons.Trophy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A milestone snackbar (personal-record/streak celebrations) - distinct from
 * every routine confirmation/error passed as a plain String to
 * SnackbarHostState.showSnackbar(). Previously a fasting personal record or an
 * activity streak record used the exact same visuals as "Enregistré" or a
 * failed save - same container, same color, same lack of an icon - giving a
 * genuinely rare multi-day/multi-week milestone no more visual weight than a
 * routine settings toggle. See ScanEatSnackbarHost's own doc comment for how
 * this is rendered differently.
 */
data class CelebrationSnackbarVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Long,
) : SnackbarVisuals

/**
 * The app's one snackbar host - a thin wrapper around Material3's SnackbarHost
 * that adds a TalkBack liveRegion announcement. Before this, ErrorBanner
 * (a persistent inline surface) was the ONLY place in the entire app that
 * announced dynamically-appearing content to TalkBack - every transient
 * snackbar (delete-with-undo, action-failed, personal-record celebrations,
 * validation feedback) across ~17 screens went completely unannounced, a
 * screen-reader user had no way to know a snackbar even appeared unless they
 * happened to already be focused near it. Polite (not Assertive like
 * ErrorBanner) since most of these are transient confirmations, not
 * safety-relevant interruptions.
 *
 * art-direction-engine §DCO6: this rendered Material3's fully default Snackbar
 * appearance - inverseSurface container, inversePrimary action color - on a
 * component that fires constantly (every one of those ~17 screens' delete-
 * undo/error/celebration moments). None of it read as this app's own
 * dark-glass-panel identity; the action button in particular was a
 * theme-default purple/blue-ish inversePrimary, never AccentCoral. Now
 * themed explicitly instead of left at the library default.
 *
 * A [CelebrationSnackbarVisuals]-backed snackbar (personal records/streaks)
 * gets its own Gold-accented container plus a trophy icon, rather than
 * sharing the identical neutral treatment every routine snackbar uses - see
 * that class's own doc comment.
 */
@Composable
fun ScanEatSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) { data ->
        if (data.visuals is CelebrationSnackbarVisuals) {
            Snackbar(
                shape = RoundedCornerShape(CardRadius.CONTROL),
                containerColor = SurfaceVariant,
                contentColor = Gold,
                actionContentColor = Gold,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TablerIcons.Trophy, contentDescription = null, tint = Gold, modifier = Modifier.size(IconSize.Inline))
                    Spacer(Modifier.width(Spacing.S))
                    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(CardRadius.CONTROL),
                containerColor = SurfaceVariant,
                contentColor = OnSurface,
                actionColor = AccentCoral,
            )
        }
    }
}
