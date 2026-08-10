package fr.scanneat.presentation.seasonal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.SeasonalProduce
import fr.scanneat.domain.engine.planning.findPairings
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.glassPopupSurface

/** Extracted from SeasonalProduceScreen (§T1 composition-root split). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeasonalPairingsDialog(produce: SeasonalProduce, isFrench: Boolean, onDismiss: () -> Unit) {
    val name = if (isFrench) produce.nameFr else produce.nameEn
    val pairs = remember(produce, isFrench) { findPairings(produce.nameFr, limit = 8, preferFrench = isFrench) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(name, color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.seasonal_pairings_title), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.6f), fontWeight = FontWeight.SemiBold)
                if (pairs.isEmpty()) {
                    Text(stringResource(R.string.seasonal_pairings_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        pairs.forEach { pair ->
                            Text(
                                pair,
                                style = MaterialTheme.typography.labelMedium,
                                color = OnBackground,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CardRadius.BADGE))
                                    .background(Teal.copy(alpha = 0.15f))
                                    .padding(horizontal = Spacing.S, vertical = Spacing.T2),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
