package fr.scanneat.presentation.fasting.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import fr.scanneat.R
import fr.scanneat.domain.engine.health.FastingRisk
import fr.scanneat.domain.engine.health.FastingSafety
import fr.scanneat.presentation.ui.theme.*

/** Roadmap C13: profile-based fasting contraindications, shown above the timer. */
@Composable
internal fun FastingSafetyCard(safety: FastingSafety) {
    val tint = if (safety.blocked) semanticRed() else semanticAmber()
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = tint.copy(alpha = 0.15f),
        contentPadding = PaddingValues(Spacing.L),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Icon(TablerIcons.AlertTriangle, null, tint = tint, modifier = Modifier.size(IconSize.Inline))
                Text(
                    stringResource(if (safety.blocked) R.string.fasting_safety_title_blocked else R.string.fasting_safety_title_warning),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = OnBackground, modifier = Modifier.weight(1f),
                )
            }
            safety.risks.forEach { risk ->
                Text(
                    "• " + stringResource(when (risk) {
                        FastingRisk.PREGNANCY   -> R.string.fasting_risk_pregnancy
                        FastingRisk.MINOR       -> R.string.fasting_risk_minor
                        FastingRisk.DIABETES    -> R.string.fasting_risk_diabetes
                        FastingRisk.UNDERWEIGHT -> R.string.fasting_risk_underweight
                    }),
                    style = MaterialTheme.typography.bodySmall, color = OnBackground,
                )
            }
            Text(stringResource(R.string.fasting_safety_ed_help), style = MaterialTheme.typography.bodySmall,
                color = OnBackground.copy(alpha = 0.7f))
        }
    }
}
