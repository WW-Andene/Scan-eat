package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AmberWarning
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.glassSheen

@Composable
internal fun WarningsSection(warnings: List<String>) {
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(8.dp))) {
        Column(modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AmberWarning.copy(0.1f))
            .padding(12.dp)) {
            Text(stringResource(R.string.result_notes_title), style = MaterialTheme.typography.labelMedium,
                color = AmberWarning, fontWeight = FontWeight.SemiBold)
            warnings.forEach { Text(stringResource(R.string.result_warning_item, it), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f)) }
        }
    }
}
