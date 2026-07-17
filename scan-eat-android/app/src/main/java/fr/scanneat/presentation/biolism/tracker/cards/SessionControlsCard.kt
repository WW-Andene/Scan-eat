package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.ScanEatOutlinedButton
import fr.scanneat.presentation.ui.theme.ScanEatPrimaryButton
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal

@Composable
internal fun SessionControls(
    running: Boolean, elapsed: Double, saved: Boolean,
    onStartPause: () -> Unit, onSave: () -> Unit, onReset: () -> Unit,
) {
    val hasElapsed = elapsed > 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        ScanEatPrimaryButton(
            onClick = onStartPause,
            modifier = Modifier.weight(1f),
            containerColor = Gold,
        ) {
            Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black)
            Spacer(Modifier.width(Spacing.XS))
            Text(if (running) stringResource(R.string.biolism_sessctrl_pause) else if (hasElapsed) stringResource(R.string.biolism_sessctrl_resume) else stringResource(R.string.biolism_sessctrl_start),
                color = Color.Black, fontWeight = FontWeight.Bold)
        }
        if (hasElapsed) {
            if (!running) {
                if (saved) {
                    ScanEatOutlinedButton(onClick = {}, enabled = false) {
                        Icon(Icons.Default.Check, null, tint = Teal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.XS))
                        Text(stringResource(R.string.biolism_sessctrl_saved), color = Teal)
                    }
                } else {
                    ScanEatOutlinedButton(onClick = onSave) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.XS))
                        Text(stringResource(R.string.biolism_sessctrl_save))
                    }
                }
            }
            // Unlike its siblings (Start/Pause, Save, Saved all carry adjacent text),
            // Reset was the only icon-only control here with a null contentDescription -
            // a TalkBack user heard nothing for it.
            ScanEatOutlinedButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, stringResource(R.string.biolism_sessctrl_reset), modifier = Modifier.size(16.dp))
            }
        }
    }
}
