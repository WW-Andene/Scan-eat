package fr.scanneat.presentation.biolism.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared helpers used by 2+ cards/*.kt files. Section-specific composables
// (SubstrateLegendItem etc.) stay colocated in their own card file.

@Composable
internal fun StepperChip(label: String, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(0.08f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("−", modifier = Modifier.clickable { onMinus() }.padding(horizontal = 6.dp, vertical = 3.dp),
            color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(label, modifier = Modifier.padding(horizontal = 4.dp),
            color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        Text("+", modifier = Modifier.clickable { onPlus() }.padding(horizontal = 6.dp, vertical = 3.dp),
            color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

internal fun formatElapsed(sec: Double): String {
    val total = sec.toLong()
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "${h}h ${m.toString().padStart(2,'0')}m ${s.toString().padStart(2,'0')}s"
    else if (m > 0) "${m}m ${s.toString().padStart(2,'0')}s"
    else "${s}s"
}

internal fun formatFastingTime(fh: Double): String? {
    if (fh <= 0) return null
    return when {
        fh >= 720  -> "${"%.1f".format(fh / 720)}mo"
        fh >= 168  -> "${(fh / 168).toInt()}s ${((fh % 168) / 24).toInt()}j"
        fh >= 48   -> "${(fh / 24).toInt()}j ${(fh % 24).toInt()}h"
        fh >= 1    -> "${fh.toInt()}h ${((fh % 1) * 60).toInt()}m"
        else       -> "${(fh * 60).toInt()}m"
    }
}
