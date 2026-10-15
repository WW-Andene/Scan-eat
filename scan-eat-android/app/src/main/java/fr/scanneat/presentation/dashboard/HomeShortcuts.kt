package fr.scanneat.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import compose.icons.TablerIcons
import compose.icons.tablericons.Bell
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.FileInvoice
import compose.icons.tablericons.Heart
import compose.icons.tablericons.History
import compose.icons.tablericons.Pill
import compose.icons.tablericons.Search
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Star
import fr.scanneat.R
import fr.scanneat.presentation.shell.AppRoutes
import fr.scanneat.presentation.ui.theme.*

/** Restructuration step 3: the tools a user can pin to Accueil (4 at most). */
internal enum class HomeShortcut(val route: String, val labelRes: Int, val icon: ImageVector) {
    MEAL_PLAN(AppRoutes.MEAL_PLAN, R.string.dashboard_tile_mealplan, TablerIcons.Calendar),
    GROCERY(AppRoutes.GROCERY, R.string.dashboard_tile_grocery, TablerIcons.ShoppingCart),
    TREATMENT(AppRoutes.TREATMENT, R.string.diary_tab_treatment, TablerIcons.Pill),
    FOOD_SEARCH(AppRoutes.FOOD_SEARCH, R.string.dashboard_tile_search, TablerIcons.Search),
    RECIPES(AppRoutes.RECIPES, R.string.dashboard_tile_recipes, TablerIcons.ClipboardList),
    TEMPLATES(AppRoutes.TEMPLATES, R.string.dashboard_tile_templates, Icons.AutoMirrored.Filled.ListAlt),
    PANTRY(AppRoutes.PANTRY, R.string.dashboard_tile_pantry, Icons.Rounded.Kitchen),
    FAVORITES(AppRoutes.FAVORITES, R.string.dashboard_tile_favorites, TablerIcons.Star),
    CUSTOM_FOODS(AppRoutes.CUSTOM_FOODS, R.string.dashboard_tile_customfoods, Icons.Rounded.Fastfood),
    SEASONAL(AppRoutes.SEASONAL_PRODUCE, R.string.dashboard_tile_seasonal, Icons.Rounded.Eco),
    CALENDAR(AppRoutes.CALENDAR, R.string.dashboard_tile_calendar, Icons.Rounded.EventNote),
    HISTORY(AppRoutes.SCAN_HISTORY, R.string.dashboard_tile_history, TablerIcons.History),
    EXPENSES(AppRoutes.EXPENSES, R.string.diary_tab_expenses, TablerIcons.FileInvoice),
    SYMPTOMS(AppRoutes.SYMPTOMS, R.string.dashboard_tile_symptoms, TablerIcons.Heart),
    WITHDRAWAL(AppRoutes.WITHDRAWAL, R.string.dashboard_tile_withdrawal, Icons.Rounded.SmokeFree),
    MY_SAFETY(AppRoutes.MY_SAFETY, R.string.my_safety_title, TablerIcons.ShieldCheck),
    RECALLS(AppRoutes.RECALLS, R.string.recalls_title, TablerIcons.Bell),
    ;

    companion object {
        const val MAX = 4
        val DEFAULT = listOf(MEAL_PLAN, GROCERY, TREATMENT, FOOD_SEARCH)

        fun parse(csv: String): List<HomeShortcut> =
            if (csv.isBlank()) DEFAULT
            else csv.split(",").mapNotNull { n -> entries.firstOrNull { it.name == n.trim() } }.distinct().take(MAX)

        fun serialize(list: List<HomeShortcut>) = list.joinToString(",") { it.name }
    }
}

@Composable
internal fun HomeShortcutsRow(shortcuts: List<HomeShortcut>, onOpen: (String) -> Unit, onEdit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_shortcuts_title), style = MaterialTheme.typography.titleSmall, color = OnBackground,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.common_edit), color = AccentCoral) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            shortcuts.forEach { s ->
                FeatureTile(s.icon, stringResource(s.labelRes), Modifier.weight(1f), onClick = { onOpen(s.route) })
            }
            repeat(HomeShortcut.MAX - shortcuts.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun HomeShortcutsDialog(current: List<HomeShortcut>, onSave: (List<HomeShortcut>) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_shortcuts_title)) },
        text = {
            Column(Modifier.heightIn(max = 384.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(stringResource(R.string.home_shortcuts_hint, HomeShortcut.MAX), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                HomeShortcut.entries.forEach { s ->
                    val on = s in picked
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, enabled = on || picked.size < HomeShortcut.MAX,
                            onCheckedChange = { picked = if (on) picked - s else picked + s })
                        Text(stringResource(s.labelRes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
