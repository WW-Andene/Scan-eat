package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared indeterminate "busy" spinner — restructuration audit (§XI): 9 call
 * sites (LogSheet, ResultScreen, ScanShelfOverlay, ScanActionControls,
 * SuggestRecipesDialog, OnlineSearchSection, RecipesImportStateDialogs,
 * ImportRecipeUrlDialog, ProfileScreen) each hand-rolled their own
 * CircularProgressIndicator for the same "something is loading" purpose,
 * unlike DeleteConfirmDialog/EmptyListState which were already centralized.
 * Defaults match the app's overwhelmingly common inline-spinner shape
 * (IconSize.Inline, AccentCoral, 2dp stroke) so most call sites need zero
 * params; size/color/strokeWidth/trackColor stay overridable for the couple
 * of larger full-screen/full-color loaders.
 *
 * Deliberately does NOT replace the app's *determinate* progress rings
 * (TodayMacroCard, HydrationRingAndControls, ScoreDisplay, ActiveFastCard) -
 * those pass a real `progress` value to visualize actual data (macro %,
 * fasting %, score), a different concept from "please wait, no known
 * duration" that this component covers.
 */
@Composable
fun ScanEatLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = IconSize.Inline,
    color: Color = AccentCoral,
    strokeWidth: Dp = 2.dp,
    // Null (the common case) omits the param entirely rather than guessing at
    // Material3's own default track color value/name for this exact library
    // version - only the couple of call sites that already customized it
    // (e.g. ResultScreen's SurfaceVariant) need to pass one explicitly.
    trackColor: Color? = null,
) {
    if (trackColor != null) {
        CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = strokeWidth, trackColor = trackColor)
    } else {
        CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = strokeWidth)
    }
}
