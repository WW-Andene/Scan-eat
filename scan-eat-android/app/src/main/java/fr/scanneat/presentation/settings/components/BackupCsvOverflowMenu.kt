package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.Table
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatDropdownMenu
import fr.scanneat.presentation.ui.theme.ScanEatOutlinedButton
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.rememberTrackedWidth

/**
 * Weight/Activity/Hydration/Medication/Fasting/Prices/CustomFoods/MealTemplates/
 * Recipes/ScanHistory/Medications previously had no CSV export at all (only Diary
 * and Biolism did) - grouped behind one overflow menu rather than 10 more stacked
 * full-width buttons, same MoreVert/DropdownMenu pattern already used to
 * consolidate a long action list elsewhere (RecipeCard etc.). Extracted from
 * BackupSection (§T1 composition-root split).
 */
@Composable
internal fun BackupCsvOverflowMenu(
    enabled: Boolean,
    onPrepareWeightCsvExport: () -> Unit,
    onPrepareActivityCsvExport: () -> Unit,
    onPrepareHydrationCsvExport: () -> Unit,
    onPrepareMedicationCsvExport: () -> Unit,
    onPrepareFastingCsvExport: () -> Unit,
    onPreparePricesCsvExport: () -> Unit,
    onPrepareCustomFoodsCsvExport: () -> Unit,
    onPrepareMealTemplatesCsvExport: () -> Unit,
    onPrepareRecipesCsvExport: () -> Unit,
    onPrepareScanHistoryCsvExport: () -> Unit,
    onPrepareMedicationsCsvExport: () -> Unit,
    onPrepareSymptomCsvExport: () -> Unit,
    onPreparePantryCsvExport: () -> Unit,
) {
    var moreCsvExpanded by remember { mutableStateOf(false) }
    val (anchorWidth, widthTracker) = rememberTrackedWidth()
    Box {
        ScanEatOutlinedButton(
            onClick = { moreCsvExpanded = true },
            enabled = enabled,
            modifier = widthTracker,
        ) {
            Icon(TablerIcons.Table, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.settings_more_csv_export_button), color = OnBackground)
        }
        ScanEatDropdownMenu(expanded = moreCsvExpanded, onDismissRequest = { moreCsvExpanded = false }, anchorWidth = anchorWidth) {
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_weight_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareWeightCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_activity_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareActivityCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_hydration_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareHydrationCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_medication_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareMedicationCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_fasting_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareFastingCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_prices_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPreparePricesCsvExport() })
            // Last batch of domains that had JSON backup but no CSV equivalent -
            // same reasoning/pattern as the five entries above.
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_customfoods_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareCustomFoodsCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_mealtemplates_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareMealTemplatesCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_recipes_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareRecipesCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_scanhistory_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareScanHistoryCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_medications_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareMedicationsCsvExport() })
            // app-audit §X: Symptom/Pantry were the last two domains with JSON
            // backup but no CSV equivalent - same reasoning as the batches above.
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_symptom_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPrepareSymptomCsvExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_pantry_csv_export_button)) },
                onClick = { moreCsvExpanded = false; onPreparePantryCsvExport() })
        }
    }
}
