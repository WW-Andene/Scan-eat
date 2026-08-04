package fr.scanneat.presentation.diary.components

import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Edit
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import kotlin.math.roundToInt

@Composable
internal fun DiaryEntryCard(entry: DiaryEntry, warning: String? = null, recommended: Boolean = false, onDelete: () -> Unit, onEdit: () -> Unit) {
    ScanEatCard(
        onClick = onEdit,
        shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.productName, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.diary_entry_summary, entry.portionG.roundToInt(), entry.consumed.energyKcal.roundToInt()),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                // Same checkUserAllergens()/checkDiet() warning Recipes/Grocery/
                // Templates already show live - previously nothing in the Diary
                // ever surfaced this, so a logged allergen never resurfaced here.
                // semanticAmber(), not the generic brand accent - this is a
                // safety-relevant allergen/diet-violation message (the icon is
                // literally WarningAmber), exactly what the colorblind-safe
                // accessor exists for; AccentCoral doesn't adapt under colorblind mode.
                if (warning != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(14.dp))
                        Text(warning, style = MaterialTheme.typography.bodySmall, color = semanticAmber(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                // Positive counterpart to the warning above - only shown when the
                // user has actually declared an allergen/diet/health condition (see
                // DiaryViewModel.diaryFoodStatus.hasDeclaredProfile), so this reads
                // as "checked against your profile and fine", not a meaningless
                // default shown on every single entry.
                } else if (recommended) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Icon(TablerIcons.CircleCheck, contentDescription = null, tint = semanticGreen(), modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.diary_entry_recommended), style = MaterialTheme.typography.bodySmall, color = semanticGreen(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Left at IconButton's default 48dp touch target (Material/WCAG minimum) -
            // a UI/UX audit found this row forcing both controls to 32dp.
            IconButton(onClick = onEdit) {
                Icon(TablerIcons.Edit, stringResource(R.string.common_edit), tint = OnSurface.copy(0.5f), modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(TablerIcons.X, stringResource(R.string.common_delete), tint = OnSurface.copy(0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
