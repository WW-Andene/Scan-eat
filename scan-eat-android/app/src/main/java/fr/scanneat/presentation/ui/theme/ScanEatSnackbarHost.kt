package fr.scanneat.presentation.ui.theme

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

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
 */
@Composable
fun ScanEatSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    ScanEatSnackbarHost(hostState, modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
