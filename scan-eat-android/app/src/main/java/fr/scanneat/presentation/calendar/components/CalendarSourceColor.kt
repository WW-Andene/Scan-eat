package fr.scanneat.presentation.calendar.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.scanneat.presentation.calendar.CalendarSource
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Violet
import fr.scanneat.presentation.ui.theme.Warm
import fr.scanneat.presentation.ui.theme.semanticGreen

@Composable
internal fun colorFor(source: CalendarSource): Color = when (source) {
    CalendarSource.MEALS      -> AccentCoral
    CalendarSource.WEIGHT     -> Gold
    CalendarSource.ACTIVITY   -> Warm
    CalendarSource.HYDRATION  -> Teal
    CalendarSource.FASTING    -> Violet
    CalendarSource.MEDICATION -> semanticGreen()
    CalendarSource.NOTE       -> OnBackground.copy(0.5f)
}
