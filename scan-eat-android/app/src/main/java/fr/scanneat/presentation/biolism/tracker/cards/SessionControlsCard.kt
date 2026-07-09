package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.Teal

@Composable
internal fun SessionControls(
    running: Boolean, elapsed: Double, saved: Boolean,
    onStartPause: () -> Unit, onSave: () -> Unit, onReset: () -> Unit,
) {
    val hasElapsed = elapsed > 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStartPause,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black)
            Spacer(Modifier.width(4.dp))
            Text(if (running) stringResource(R.string.biolism_sessctrl_pause) else if (hasElapsed) stringResource(R.string.biolism_sessctrl_resume) else stringResource(R.string.biolism_sessctrl_start),
                color = Color.Black, fontWeight = FontWeight.Bold)
        }
        if (hasElapsed) {
            if (!running) {
                if (saved) {
                    OutlinedButton(onClick = {}, enabled = false, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Check, null, tint = Teal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.biolism_sessctrl_saved), color = Teal)
                    }
                } else {
                    OutlinedButton(onClick = onSave, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.biolism_sessctrl_save))
                    }
                }
            }
            OutlinedButton(onClick = onReset, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
