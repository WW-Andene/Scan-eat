package fr.scanneat.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/** Roadmap A11: show/hide and reorder dashboard cards; changes are saved immediately. */
@Composable
internal fun DashboardLayoutDialog(layout: DashboardLayout, onChange: (DashboardLayout) -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dash_customize), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.dash_customize_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                LazyColumn(Modifier.heightIn(max = 384.dp)) {
                    items(layout.order, key = { it.name }) { card ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = card !in layout.hidden, onCheckedChange = { onChange(layout.toggled(card)) })
                            Text(stringResource(card.labelRes), color = OnBackground, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onChange(layout.moved(card, -1)) }) { Icon(TablerIcons.ChevronUp, stringResource(R.string.dash_move_up)) }
                            IconButton(onClick = { onChange(layout.moved(card, 1)) }) { Icon(TablerIcons.ChevronDown, stringResource(R.string.dash_move_down)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dash_done)) } },
        dismissButton = { TextButton(onClick = { onChange(DashboardLayout.DEFAULT) }) { Text(stringResource(R.string.dash_reset)) } },
    )
}
