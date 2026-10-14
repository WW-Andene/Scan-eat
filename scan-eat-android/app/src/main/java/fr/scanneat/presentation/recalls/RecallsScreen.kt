package fr.scanneat.presentation.recalls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Ban
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * Roadmap D1: "Mes produits rappelés". Fed by the recall cache that both the
 * scan-time check and RecallSyncWorker's daily sync write to. RappelConso
 * texts are French-only official notices and are shown as published.
 */
@Composable
fun RecallsScreen(
    viewModel: RecallsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenResult: (Long) -> Unit,
) {
    val items = viewModel.items.collectAsStateWithLifecycle().value
    val uriHandler = LocalUriHandler.current

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.recalls_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.L)) }
            item {
                Text(stringResource(R.string.recalls_intro), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.7f))
            }
            when {
                items == null -> {}
                items.isEmpty() -> item { EmptyListState(TablerIcons.Ban, stringResource(R.string.recalls_empty)) }
                else -> items(items, key = { it.barcode }) { p ->
                    ScanEatCard(
                        shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticRed().copy(alpha = 0.12f),
                        contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
                        onClick = p.scanId?.let { id -> { onOpenResult(id) } },
                    ) {
                        Text(p.entry.productLabel?.takeIf { it.isNotBlank() } ?: p.barcode,
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = OnBackground)
                        Text(
                            listOfNotNull(p.barcode, p.entry.publicationDate?.take(10)).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(alpha = 0.6f),
                        )
                        p.entry.reasonFr?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OnBackground) }
                        p.entry.risksFr?.let { Text(stringResource(R.string.recalls_risks, it), style = MaterialTheme.typography.bodySmall, color = OnBackground) }
                        p.entry.instructionsFr?.let { Text(stringResource(R.string.recalls_instructions, it), style = MaterialTheme.typography.bodySmall, color = OnBackground) }
                        p.entry.recallSheetUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                                Text(stringResource(R.string.recalls_open_sheet))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}
