package fr.scanneat.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
    val scoreRange = viewModel.scoreRange.collectAsStateWithLifecycle()
    val topScanned = viewModel.topScanned.collectAsStateWithLifecycle()
    val gradeDistribution = viewModel.gradeDistribution.collectAsStateWithLifecycle()
    val avgScore = viewModel.avgScore.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Score range filter options: null = all, else (min, max) inclusive
    val scoreRangeOptions = listOf(
        null to stringResource(R.string.history_score_range_all),
        (80 to 100) to stringResource(R.string.history_score_range_a),
        (60 to 79)  to stringResource(R.string.history_score_range_b),
        (40 to 59)  to stringResource(R.string.history_score_range_c),
        (0  to 39)  to stringResource(R.string.history_score_range_d),
    )

    // Dashboard's "Favoris" shortcut opens History pre-filtered, rather than
    // needing a second favorites-only screen with its own list/delete/sort logic.
    LaunchedEffect(Unit) { if (startFavoritesOnly) viewModel.setFavoritesOnly(true) }

    Scaffold(
        topBar = {
            FloatingTopBar(
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
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
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
                shape = RoundedCornerShape(CardRadius.CONTROL),
                colors = scanEatTextFieldColors(),
            )

            // Improvement: score-range filter chips so users can drill into a grade band
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.L, vertical = Spacing.XS),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                item {
                    FilterChip(
                        selected = favoritesOnly.value,
                        onClick  = { viewModel.setFavoritesOnly(!favoritesOnly.value) },
                        label    = { Text(stringResource(R.string.history_favorites_only)) },
                        leadingIcon = { Icon(Icons.Default.Star, null, tint = if (favoritesOnly.value) Gold else OnBackground.copy(0.5f), modifier = Modifier.size(16.dp)) },
                        colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold),
                    )
                }
                items(scoreRangeOptions) { (range, label) ->
                    val isSelected = scoreRange.value == range
                    FilterChip(
                        selected = isSelected,
                        onClick  = { viewModel.setScoreRange(if (isSelected) null else range) },
                        label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCoral.copy(0.15f),
                            selectedLabelColor     = AccentCoral,
                        ),
                    )
                }
            }

            avgScore.value?.let { avg ->
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.08f)) {
                        Text(
                            stringResource(R.string.history_avg_score, avg),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCoral,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                // New: frequently scanned section — top 3 products by scan count
                if (topScanned.value.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.history_top_scanned_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnBackground.copy(0.5f),
                            modifier = Modifier.padding(top = Spacing.S, bottom = Spacing.XS),
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                        ) {
                            topScanned.value.forEach { (name, count, dbId) ->
                                Surface(
                                    onClick  = { if (dbId > 0) onOpenResult(dbId) },
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(CardRadius.CONTROL),
                                    color    = SurfaceVariant,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Spacing.S),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            count.toString(),
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color      = AccentCoral,
                                        )
                                        Text(
                                            name,
                                            style    = MaterialTheme.typography.labelSmall,
                                            color    = OnSurface.copy(0.7f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Grade distribution — A/B/C/D breakdown across full scan history
                if (gradeDistribution.value.isNotEmpty()) {
                    item {
                        val total = gradeDistribution.value.sumOf { it.second }.coerceAtLeast(1)
                        Text(
                            stringResource(R.string.history_grade_distribution_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnBackground.copy(0.5f),
                            modifier = Modifier.padding(top = Spacing.S, bottom = Spacing.XS),
                        )
                        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                            gradeDistribution.value.forEach { (grade, count) ->
                                val color = when (grade) {
                                    "A" -> semanticGreen()
                                    "B" -> semanticAmber()
                                    "C" -> AccentCoral
                                    else -> semanticRed()
                                }
                                Box(Modifier.weight(count.toFloat() / total).fillMaxHeight().background(color))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            gradeDistribution.value.forEach { (grade, count) ->
                                val color = when (grade) {
                                    "A" -> semanticGreen()
                                    "B" -> semanticAmber()
                                    "C" -> AccentCoral
                                    else -> semanticRed()
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
                                    Text("$grade $count", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                                }
                            }
                        }
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.S), color = OnBackground.copy(0.08f))
                    }
                }

                items(scans.value, key = { it.dbId }) { scan ->
                    val gradeColor = gradeColor(scan.audit.grade)
                    val summary = stringResource(R.string.history_item_summary, scan.product.name, scan.audit.grade.label, scan.audit.score)
                    ScanEatCard(
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                        color = SurfaceVariant,
                        contentPadding = PaddingValues(Spacing.M),
                        onClick = { if (scan.dbId > 0) onOpenResult(scan.dbId) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics { contentDescription = summary },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                        ) {
                            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = gradeColor.copy(0.2f)) {
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
                            Icon(Icons.Default.ChevronRight, null, tint = OnSurface.copy(0.3f), modifier = Modifier.size(IconSize.Inline))
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

