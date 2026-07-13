package fr.scanneat.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject



@Composable
fun ScanHistoryScreen(
    viewModel: ScanHistoryViewModel = hiltViewModel(),
    onOpenResult: (Long) -> Unit,
    onBack: () -> Unit,
    startFavoritesOnly: Boolean = false,
) {
    val scans = viewModel.filtered.collectAsStateWithLifecycle()
    val query = viewModel.query.collectAsStateWithLifecycle()
    val favoritesOnly = viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val sort = viewModel.sort.collectAsStateWithLifecycle()
    val canLoadMore = viewModel.canLoadMore.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Dashboard's "Favoris" shortcut opens History pre-filtered, rather than
    // needing a second favorites-only screen with its own list/delete/sort logic.
    LaunchedEffect(Unit) { if (startFavoritesOnly) viewModel.setFavoritesOnly(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.Sort, stringResource(R.string.history_sort), tint = OnBackground.copy(0.7f))
                        }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            val options = listOf(
                                HistorySort.RECENT to stringResource(R.string.history_sort_recent),
                                HistorySort.OLDEST to stringResource(R.string.history_sort_oldest),
                                HistorySort.NAME_AZ to stringResource(R.string.history_sort_name),
                                HistorySort.SCORE_DESC to stringResource(R.string.history_sort_score),
                            )
                            options.forEach { (value, label) ->
                                val isSelected = sort.value == value
                                DropdownMenuItem(
                                    text = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = AccentCoral)
                                    },
                                    modifier = Modifier.semantics { selected = isSelected; role = Role.RadioButton },
                                    onClick = { viewModel.setSort(value); sortMenuExpanded = false },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = query.value,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.S),
                placeholder = { Text(stringResource(R.string.history_search_placeholder), color = OnBackground.copy(0.4f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnBackground.copy(0.5f)) },
                trailingIcon = {
                    if (query.value.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_clear_search), tint = OnBackground.copy(0.5f))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = scanEatTextFieldColors(),
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
                FilterChip(
                    selected = favoritesOnly.value,
                    onClick  = { viewModel.setFavoritesOnly(!favoritesOnly.value) },
                    label    = { Text(stringResource(R.string.history_favorites_only)) },
                    leadingIcon = { Icon(Icons.Default.Star, null, tint = if (favoritesOnly.value) Gold else OnBackground.copy(0.5f), modifier = Modifier.size(16.dp)) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                items(scans.value, key = { it.dbId }) { scan ->
                    val gradeColor = gradeColor(scan.audit.grade)
                    val summary = stringResource(R.string.history_item_summary, scan.product.name, scan.audit.grade.label, scan.audit.score)
                    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(12.dp))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceVariant)
                                .clickable { if (scan.dbId > 0) onOpenResult(scan.dbId) }
                                .clearAndSetSemantics { contentDescription = summary }
                                .padding(Spacing.M),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                        ) {
                            Surface(shape = RoundedCornerShape(8.dp), color = gradeColor.copy(0.2f)) {
                                Text(
                                    scan.audit.grade.label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = gradeColor, fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.history_score_category, scan.audit.score, scan.product.category.key.replace('_', ' ')), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(scan) }) {
                                Icon(
                                    if (scan.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    stringResource(if (scan.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                                    tint = if (scan.favorite) Gold else OnSurface.copy(0.3f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(onClick = { deleteTarget = scan.dbId }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.common_delete), tint = OnSurface.copy(0.3f), modifier = Modifier.size(18.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = OnSurface.copy(0.3f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (scans.value.isEmpty()) {
                    item {
                        EmptyListState(
                            Icons.Default.History,
                            when {
                                query.value.isNotBlank() -> stringResource(R.string.history_empty_query, query.value)
                                favoritesOnly.value       -> stringResource(R.string.history_empty_favorites)
                                else                      -> stringResource(R.string.history_empty)
                            },
                        )
                    }
                }
                if (canLoadMore.value && !favoritesOnly.value) {
                    item {
                        TextButton(onClick = { viewModel.loadMore() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.history_load_more), color = AccentCoral)
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.XXL)) }
            }
        }
    }

    deleteTarget?.let { id ->
        val name = scans.value.firstOrNull { it.dbId == id }?.product?.name
        DeleteConfirmDialog(
            itemName  = name,
            onConfirm = { viewModel.delete(id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

