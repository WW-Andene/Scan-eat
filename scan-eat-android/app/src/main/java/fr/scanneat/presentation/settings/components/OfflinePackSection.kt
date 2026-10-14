package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.Download
import fr.scanneat.R
import fr.scanneat.data.repository.offline.OfflinePackRepository
import fr.scanneat.data.repository.offline.OfflinePackWorker
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Roadmap N9: offline product database toggle and status. */
@Composable
internal fun OfflinePackSection(repo: OfflinePackRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(repo.isEnabled) }
    var count by remember { mutableIntStateOf(-1) }
    LaunchedEffect(enabled) { count = repo.count() }
    SettingsSection(stringResource(R.string.offline_pack_title), icon = TablerIcons.Download) {
        Text(stringResource(R.string.offline_pack_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.offline_pack_toggle), color = OnBackground, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { on ->
                enabled = on; repo.setEnabled(on)
                if (on) OfflinePackWorker.enable(context) else { OfflinePackWorker.disable(context); scope.launch { repo.clear(); count = 0 } }
            })
        }
        if (enabled) {
            val last = repo.lastDownloadMs
            Text(
                if (last == 0L) stringResource(R.string.offline_pack_pending)
                else stringResource(R.string.offline_pack_status, count.coerceAtLeast(0), DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(last))),
                style = MaterialTheme.typography.bodySmall, color = OnBackground,
            )
            TextButton(onClick = { OfflinePackWorker.refreshNow(context) }) { Text(stringResource(R.string.offline_pack_refresh)) }
        }
    }
}
