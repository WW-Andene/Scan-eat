package fr.scanneat.presentation.recipes.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.Bulb
import compose.icons.tablericons.Plus
import fr.scanneat.R
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground

/**
 * RecipesScreen's FloatingScreenScaffold `actions` row (planning switcher +
 * suggest/import-url/import-photo/menu-scan/new) — extracted verbatim (§T1
 * composition-root split) so the screen's own body only wires callbacks.
 */
@Composable
fun RecipesTopBarActions(
    onNavigateToPlanning: (PlanningDestination) -> Unit,
    onSuggest: () -> Unit,
    onImportUrl: () -> Unit,
    onImportPhoto: () -> Unit,
    onMenuScan: () -> Unit,
    onNew: () -> Unit,
) {
    PlanningSwitcherMenu(current = PlanningDestination.RECIPES, onNavigate = onNavigateToPlanning)
    IconButton(onClick = onSuggest) { Icon(TablerIcons.Bulb, stringResource(R.string.recipes_cd_suggest), tint = OnBackground) }
    IconButton(onClick = onImportUrl) { Icon(Icons.Rounded.Link, stringResource(R.string.recipes_cd_import_url), tint = OnBackground) }
    IconButton(onClick = onImportPhoto) { Icon(Icons.Rounded.PhotoCamera, stringResource(R.string.recipes_cd_import_photo), tint = OnBackground) }
    IconButton(onClick = onMenuScan) { Icon(Icons.Rounded.Restaurant, stringResource(R.string.recipes_cd_menu_scan), tint = OnBackground) }
    IconButton(onClick = onNew) { Icon(TablerIcons.Plus, stringResource(R.string.recipes_cd_new), tint = AccentCoral) }
}
