package fr.scanneat.presentation.hub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Settings
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/** One tool in a hub: icon, title, one-line description. */
data class HubEntry(val icon: ImageVector, val title: Int, val subtitle: Int, val onClick: () -> Unit)
data class HubGroup(val label: Int, val entries: List<HubEntry>)

/**
 * A tab-root list of tools grouped by purpose - the Cuisine and Santé tabs.
 * Replaces tools that were scattered across Dashboard tiles, Diary sub-tabs
 * and "Ma sécurité": one place per domain, one tap to each tool.
 */
@Composable
fun HubScreen(title: Int, groups: List<HubGroup>, onOpenSettings: () -> Unit) {
    FloatingScreenScaffold(
        title = { Text(stringResource(title), color = OnBackground) },
        hasNavigationIcon = false,
        showBottomNavClearance = true,
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(TablerIcons.Settings, stringResource(R.string.tab_settings), tint = OnBackground) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }
            groups.forEach { g ->
                item {
                    Text(stringResource(g.label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), fontWeight = FontWeight.SemiBold)
                }
                item {
                    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(vertical = Spacing.XS)) {
                        g.entries.forEach { e -> HubRow(e) }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}

@Composable
private fun HubRow(e: HubEntry) {
    Surface(onClick = e.onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Icon(e.icon, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Inline))
            Column(Modifier.weight(1f)) {
                Text(stringResource(e.title), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Text(stringResource(e.subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.55f))
            }
            Icon(TablerIcons.ChevronRight, null, tint = OnBackground.copy(0.3f), modifier = Modifier.size(IconSize.Compact))
        }
    }
}
