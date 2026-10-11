package fr.scanneat.presentation.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassPopupSurface

/**
 * User-requested: an interactive walkthrough shown over a *real* scan result
 * (not a static onboarding slide with mocked-up content) - explains the score
 * ring, the functional/allergen badges and the improvement tips section using
 * whatever the user actually just scanned. Shown at most once per install,
 * gated by UserPreferences.scanTutorialSeen (ResultViewModel wires this) -
 * dismissing at any step (X, "Passer", or finishing the last step) marks it
 * seen for good, same one-shot semantics as onboarding itself.
 *
 * Three fixed steps regardless of what the product actually has (some
 * products have no badges/tips) - the walkthrough explains what these
 * sections *mean*, not "look, here's one", so it stays useful even for a
 * simple product with nothing flagged.
 */
@Composable
fun ScanResultTutorialDialog(onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        R.string.scan_tutorial_step1_title to R.string.scan_tutorial_step1_body,
        R.string.scan_tutorial_step2_title to R.string.scan_tutorial_step2_body,
        R.string.scan_tutorial_step3_title to R.string.scan_tutorial_step3_body,
    )
    val (titleRes, bodyRes) = steps[step]

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(titleRes), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(stringResource(bodyRes), color = OnBackground.copy(0.8f))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StepDots(total = steps.size, active = step)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (step < steps.lastIndex) step++ else onDismiss() }) {
                Text(
                    stringResource(if (step < steps.lastIndex) R.string.scan_tutorial_next else R.string.scan_tutorial_done),
                    color = AccentCoral,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.onboarding_skip_all), color = OnBackground.copy(0.5f))
            }
        },
    )
}

@Composable
private fun StepDots(total: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == active) 24.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == active) AccentCoral else OnBackground.copy(0.2f)),
            )
        }
    }
}
