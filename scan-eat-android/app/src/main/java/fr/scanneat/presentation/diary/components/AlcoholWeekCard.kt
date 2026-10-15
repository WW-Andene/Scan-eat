package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.AlcoholWeek
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardEmphasis
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import java.util.Locale

/** Last 7 days of alcohol against the French low-risk guidelines (see AlcoholWeek). */
@Composable
internal fun AlcoholWeekCard(a: AlcoholWeek) {
    ScanEatCard(
        emphasis = CardEmphasis.SECONDARY,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Text(stringResource(R.string.diary_alcohol_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(stringResource(R.string.diary_alcohol_total, String.format(Locale.getDefault(), "%.1f", a.standardDrinks), a.daysWithAlcohol),
            style = MaterialTheme.typography.bodyMedium, color = if (a.overWeekly) AccentCoral else OnSurface)
        if (a.pregnancy) Text(stringResource(R.string.diary_alcohol_pregnancy), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
        if (a.daysOverTwo > 0) Text(stringResource(R.string.diary_alcohol_over_two, a.daysOverTwo), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
        if (a.daysWithAlcohol >= 7) Text(stringResource(R.string.diary_alcohol_every_day), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
        if (a.unknownStrength > 0) Text(stringResource(R.string.diary_alcohol_unknown, a.unknownStrength), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
        Text(stringResource(R.string.diary_alcohol_source), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
    }
}
