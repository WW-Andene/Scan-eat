package fr.scanneat.presentation.reaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import fr.scanneat.R
import fr.scanneat.data.repository.reaction.AllergicReaction
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate

/** Roadmap N10: "Carnet de réactions". Leads, never conclusions - see sharedReactionSuspects. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionsScreen(viewModel: ReactionsViewModel = hiltViewModel(), onBack: () -> Unit) {
    val reactions = viewModel.reactions.collectAsStateWithLifecycle().value
    val suspects = viewModel.suspects.collectAsStateWithLifecycle().value
    val recentScans = viewModel.recentScans.collectAsStateWithLifecycle().value
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AllergicReaction?>(null) }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.reactions_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = { IconButton(onClick = { showAdd = true }) { Icon(TablerIcons.Plus, stringResource(R.string.reactions_add), tint = OnBackground) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.L)) }
            item { Text(stringResource(R.string.reactions_emergency), style = MaterialTheme.typography.labelMedium, color = semanticRed(), fontWeight = FontWeight.SemiBold) }
            if (suspects.isNotEmpty()) item {
                ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(stringResource(R.string.reactions_suspects_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = OnBackground)
                    Text(stringResource(R.string.reactions_suspects_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(alpha = 0.6f))
                    suspects.take(8).forEach { s ->
                        Text("${s.label} — ${s.reactionCount}/${s.totalReactions}", style = MaterialTheme.typography.bodySmall,
                            color = if (s.isAnnexIIAllergen) semanticAmber() else OnBackground)
                    }
                }
            }
            if (reactions.isEmpty()) item { EmptyListState(TablerIcons.Plus, stringResource(R.string.reactions_empty)) }
            items(reactions, key = { it.id }) { r ->
                ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.productName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = OnBackground)
                            Text("${r.date} · " + stringResource(severityRes(r.severity)) +
                                if (r.epinephrineUsed) " · " + stringResource(R.string.reactions_epinephrine) else "",
                                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = { deleteTarget = r }) { Icon(TablerIcons.Trash, stringResource(R.string.common_delete), tint = OnBackground.copy(alpha = 0.6f)) }
                    }
                    if (r.symptoms.isNotBlank()) Text(r.symptoms, style = MaterialTheme.typography.bodySmall, color = OnBackground)
                    if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.7f))
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (showAdd) AddReactionDialog(recentScans, onDismiss = { showAdd = false }) { date, scan, name, symptoms, severity, epi, notes ->
        viewModel.add(date, scan, name, symptoms, severity, epi, notes); showAdd = false
    }
    deleteTarget?.let { r ->
        DeleteConfirmDialog(
            itemName = r.productName,
            onConfirm = { viewModel.delete(r.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun severityRes(severity: Int) = when (severity) {
    3 -> R.string.reactions_severity_severe
    2 -> R.string.reactions_severity_moderate
    else -> R.string.reactions_severity_mild
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddReactionDialog(
    recentScans: List<ScanResult>,
    onDismiss: () -> Unit,
    onAdd: (LocalDate, ScanResult?, String, String, Int, Boolean, String) -> Unit,
) {
    var daysAgo by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ScanResult?>(null) }
    var freeName by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }
    var severity by remember { mutableIntStateOf(1) }
    var epinephrine by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    val valid = selected != null || freeName.isNotBlank()

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reactions_add), color = OnBackground) },
        text = {
            Column(Modifier.heightIn(max = 512.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    listOf(R.string.reactions_today, R.string.reactions_yesterday, R.string.reactions_two_days_ago).forEachIndexed { i, res ->
                        FilterChip(selected = daysAgo == i, onClick = { daysAgo = i }, label = { Text(stringResource(res)) })
                    }
                }
                Text(stringResource(R.string.reactions_pick_product), style = MaterialTheme.typography.labelMedium, color = OnBackground)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    recentScans.distinctBy { it.product.name }.take(12).forEach { s ->
                        FilterChip(selected = selected == s, onClick = { selected = if (selected == s) null else s; freeName = "" },
                            label = { Text(s.product.name, maxLines = 1) })
                    }
                }
                OutlinedTextField(value = freeName, onValueChange = { freeName = it; selected = null },
                    label = { Text(stringResource(R.string.reactions_other_product)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = symptoms, onValueChange = { symptoms = it },
                    label = { Text(stringResource(R.string.reactions_symptoms)) }, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    (1..3).forEach { lvl -> FilterChip(selected = severity == lvl, onClick = { severity = lvl }, label = { Text(stringResource(severityRes(lvl))) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reactions_epinephrine_question), modifier = Modifier.weight(1f), color = OnBackground)
                    Switch(checked = epinephrine, onCheckedChange = { epinephrine = it })
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.reactions_notes)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onAdd(LocalDate.now().minusDays(daysAgo.toLong()), selected, freeName, symptoms, severity, epinephrine, notes)
            }) { Text(stringResource(R.string.reactions_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
