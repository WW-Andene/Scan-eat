package fr.scanneat.presentation.store

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import fr.scanneat.R
import fr.scanneat.domain.engine.planning.groceryCategoryFor
import fr.scanneat.domain.engine.scoring.FamilyStatus
import fr.scanneat.presentation.grocery.GroceryViewModel
import fr.scanneat.presentation.scan.components.CameraPreview
import fr.scanneat.presentation.scan.rememberCameraPermissionState
import fr.scanneat.presentation.ui.theme.*

/**
 * Roadmap N7 "En magasin": one screen, one hand - camera on top, the list
 * still to buy grouped by aisle below. A scanned product ticks the matching
 * item and shows its grade, the family check and the last price paid.
 */
@Composable
fun StoreModeScreen(onBack: () -> Unit, grocery: GroceryViewModel = hiltViewModel(), store: StoreModeViewModel = hiltViewModel()) {
    val items = grocery.checkableItems.collectAsStateWithLifecycle().value
    val last = store.last.collectAsStateWithLifecycle().value
    val busy = store.busy.collectAsStateWithLifecycle().value
    val currency = grocery.currencySymbol.collectAsStateWithLifecycle().value
    var tickedName by remember { mutableStateOf<String?>(null) }
    val camera = rememberCameraPermissionState()
    val hasCamera by camera.hasCamera
    val todo = items.filter { !it.checked }

    Column(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.S)) {
            IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) }
            Text(stringResource(R.string.store_mode_title), style = MaterialTheme.typography.titleMedium, color = OnBackground)
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(IconSize.Inline), strokeWidth = 2.dp)
        }
        Box(Modifier.fillMaxWidth().height(256.dp).padding(horizontal = Spacing.L).clip(RoundedCornerShape(CardRadius.CONTROL))) {
            if (hasCamera) CameraPreview(
                onBarcodeDetected = { code ->
                    store.onBarcode(code) { result ->
                        fr.scanneat.presentation.store.matchGroceryItem(result.product.name, todo.map { it.item })?.let {
                            grocery.toggleChecked(it, true); tickedName = it.name
                        } ?: run { tickedName = null }
                    }
                },
                onPhotoCaptured = {},
            ) else Button(onClick = camera.requestPermission, modifier = Modifier.align(Alignment.Center)) { Text(stringResource(R.string.receipt_scan_grant_permission)) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.L), contentPadding = PaddingValues(vertical = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            last?.let { s ->
                item {
                    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text("${s.result.product.name} — ${s.result.audit.grade.label} (${s.result.audit.score}/100)",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = gradeColor(s.result.audit.grade))
                        tickedName?.let { Text(stringResource(R.string.store_mode_ticked, it), style = MaterialTheme.typography.bodySmall, color = semanticGreen()) }
                        s.lastPrice?.let { Text(stringResource(R.string.store_mode_last_price, String.format(java.util.Locale.US, "%.2f", it), currency), style = MaterialTheme.typography.bodySmall, color = OnBackground) }
                        s.family.forEach { v ->
                            val (txt, col) = when (v.status) {
                                FamilyStatus.BLOCKED -> "✕" to semanticRed()
                                FamilyStatus.CAUTION, FamilyStatus.UNVERIFIED -> "⚠" to semanticAmber()
                                FamilyStatus.OK -> "✓" to semanticGreen()
                            }
                            Text("$txt ${v.profileName.ifBlank { stringResource(R.string.profile_switcher_unnamed) }}" + (v.reason?.let { " — $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = col)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.store_mode_todo, todo.size), style = MaterialTheme.typography.titleSmall, color = OnBackground) }
            todo.groupBy { groceryCategoryFor(it.item.name) }.toSortedMap().forEach { (aisle, list) ->
                item { Text(fr.scanneat.presentation.grocery.components.categoryLabel(aisle), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.6f)) }
                items(list, key = { it.item.key }) { ci ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = false, onCheckedChange = { grocery.toggleChecked(ci.item, true) })
                        Text(ci.item.name, color = OnBackground)
                    }
                }
            }
        }
    }
}
