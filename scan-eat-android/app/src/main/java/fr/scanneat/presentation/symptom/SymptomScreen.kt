package fr.scanneat.presentation.symptom

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Book
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.symptom.SymptomEntry
import fr.scanneat.data.repository.symptom.SymptomType
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * User-requested: a symptom journal correlated against the diary - see
 * SymptomViewModel/symptomFoodCorrelations' own doc comments. Deliberately
 * simple (flat list + one add dialog, no calendar view) since this is meant
 * to be a quick, frictionless log, not a second Journal.
 */
@Composable
fun SymptomScreen(viewModel: SymptomViewModel = hiltViewModel(), onBack: () -> Unit) {
    val entries = viewModel.entries.collectAsStateWithLifecycle()
    val loggedTypes = viewModel.loggedTypes.collectAsStateWithLifecycle()
    val selectedType = viewModel.selectedType.collectAsStateWithLifecycle()
    val correlations = viewModel.correlations.collectAsStateWithLifecycle()
    val medicationCorrelations = viewModel.medicationCorrelations.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.symptom_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = { IconButton(onClick = { showAdd = true }) { Icon(TablerIcons.Plus, stringResource(R.string.common_add), tint = AccentCoral) } },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.L)) }

            if (loggedTypes.value.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text(stringResource(R.string.symptom_correlation_filter_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.6f))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            FilterChip(
                                selected = selectedType.value == null,
                                onClick = { viewModel.setSelectedType(null) },
                                label = { Text(stringResource(R.string.symptom_filter_all), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                            )
                            loggedTypes.value.forEach { type ->
                                FilterChip(
                                    selected = selectedType.value == type,
                                    onClick = { viewModel.setSelectedType(type) },
                                    label = { Text(symptomTypeLabel(type), style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                                )
                            }
                        }
                    }
                }
            }

            if (correlations.value.isNotEmpty()) {
                item { SymptomCorrelationCard(correlations.value) }
            }
            if (medicationCorrelations.value.isNotEmpty()) {
                item { SymptomMedicationCorrelationCard(medicationCorrelations.value) }
            }

            if (entries.value.isEmpty()) {
                item { EmptyListState(TablerIcons.Book, stringResource(R.string.symptom_empty)) }
            } else {
                items(entries.value, key = { it.id }) { entry ->
                    SymptomRow(entry = entry, onDelete = { viewModel.delete(entry.id) })
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (showAdd) {
        AddSymptomDialog(
            onDismiss = { showAdd = false },
            onAdd = { date, type, customLabel, severity, notes ->
                viewModel.add(date, type, customLabel, severity, notes)
                showAdd = false
            },
        )
    }
}

@Composable
internal fun symptomTypeLabel(type: SymptomType): String = stringResource(
    when (type) {
        SymptomType.BLOATING     -> R.string.symptom_type_bloating
        SymptomType.FATIGUE      -> R.string.symptom_type_fatigue
        SymptomType.SLEEP        -> R.string.symptom_type_sleep
        SymptomType.HEADACHE     -> R.string.symptom_type_headache
        SymptomType.STOMACH_PAIN -> R.string.symptom_type_stomach_pain
        SymptomType.SKIN         -> R.string.symptom_type_skin
        SymptomType.OTHER        -> R.string.symptom_type_other
    },
)

@Composable
private fun SymptomRow(entry: SymptomEntry, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val label = if (entry.type == SymptomType.OTHER && entry.customLabel.isNotBlank()) entry.customLabel else symptomTypeLabel(entry.type)
                Text(label, style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                Text(
                    entry.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " · " + stringResource(R.string.symptom_severity_short, entry.severity) +
                        (entry.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                )
            }
            IconButton(onClick = onDelete) { Icon(TablerIcons.Trash, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f)) }
        }
    }
}

@Composable
private fun SymptomCorrelationCard(correlations: List<fr.scanneat.domain.engine.symptom.FoodCorrelation>) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.symptom_correlation_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.symptom_correlation_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        correlations.take(5).forEach { c ->
            Text(
                stringResource(R.string.symptom_correlation_row, c.productName, c.symptomDaysWithFood, c.totalSymptomDays),
                style = MaterialTheme.typography.bodySmall, color = semanticAmber(),
            )
        }
    }
}

/** app-audit §X: medication counterpart to [SymptomCorrelationCard] - see
 *  SymptomViewModel.medicationCorrelations' own doc comment. */
@Composable
private fun SymptomMedicationCorrelationCard(correlations: List<fr.scanneat.domain.engine.symptom.MedicationCorrelation>) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.symptom_medication_correlation_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.symptom_correlation_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        correlations.take(5).forEach { c ->
            Text(
                stringResource(R.string.symptom_correlation_row, c.medicationName, c.symptomDaysWithMedication, c.totalSymptomDays),
                style = MaterialTheme.typography.bodySmall, color = semanticAmber(),
            )
        }
    }
}

@Composable
private fun AddSymptomDialog(
    onDismiss: () -> Unit,
    onAdd: (date: LocalDate, type: SymptomType, customLabel: String, severity: Int, notes: String) -> Unit,
) {
    var type by rememberSaveable(stateSaver = fr.scanneat.presentation.onboarding.enumSaver<SymptomType>()) { mutableStateOf(SymptomType.BLOATING) }
    var customLabel by rememberSaveable { mutableStateOf("") }
    var severity by rememberSaveable { mutableIntStateOf(3) }
    var notes by rememberSaveable { mutableStateOf("") }
    val isValid = type != SymptomType.OTHER || customLabel.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.symptom_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    SymptomType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(symptomTypeLabel(t), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                        )
                    }
                }
                if (type == SymptomType.OTHER) {
                    OutlinedTextField(
                        value = customLabel, onValueChange = { customLabel = it },
                        label = { Text(stringResource(R.string.symptom_field_custom_label)) }, singleLine = true,
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                        colors = scanEatTextFieldColors(),
                    )
                }
                Column {
                    Text(stringResource(R.string.symptom_field_severity, severity), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    Slider(
                        value = severity.toFloat(), onValueChange = { severity = it.toInt() },
                        valueRange = 1f..5f, steps = 3,
                        colors = SliderDefaults.colors(thumbColor = AccentCoral, activeTrackColor = AccentCoral),
                    )
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.symptom_field_notes)) },
                    singleLine = false, minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(LocalDate.now(), type, customLabel, severity, notes) },
                enabled = isValid,
            ) { Text(stringResource(R.string.common_add), color = if (isValid) AccentCoral else OnBackground.copy(0.3f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
