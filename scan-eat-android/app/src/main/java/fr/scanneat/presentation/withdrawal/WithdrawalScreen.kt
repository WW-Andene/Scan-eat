package fr.scanneat.presentation.withdrawal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import fr.scanneat.R
import fr.scanneat.domain.engine.withdrawal.*
import fr.scanneat.presentation.ui.theme.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Tobacco cessation and the follow-up of a doctor-prescribed medication taper. */
@Composable
fun WithdrawalScreen(viewModel: WithdrawalViewModel = hiltViewModel(), onBack: () -> Unit) {
    val plan = viewModel.tobaccoPlan.collectAsStateWithLifecycle()
    val stats = viewModel.tobaccoStats.collectAsStateWithLifecycle()
    val taper = viewModel.taperPlan.collectAsStateWithLifecycle()
    val now = viewModel.now.collectAsStateWithLifecycle()
    var editTaper by remember { mutableStateOf(false) }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.withdrawal_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }
            item {
                val p = plan.value
                val s = stats.value
                if (p == null || s == null) TobaccoSetupCard(viewModel::startTobacco)
                else TobaccoCard(p, s, now.value, onAdd = { viewModel.cigarette(1) }, onRemove = { viewModel.cigarette(-1) }, onStop = viewModel::stopTobacco)
            }
            item { TaperCard(taper.value, onEdit = { editTaper = true }, onDelete = viewModel::deleteTaper) }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
    if (editTaper) TaperEditorDialog(taper.value, onDismiss = { editTaper = false }) { name, steps, note ->
        viewModel.saveTaper(name, steps, note); editTaper = false
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) = ScanEatCard(
    shape = RoundedCornerShape(CardRadius.CONTROL),
    verticalArrangement = Arrangement.spacedBy(Spacing.S),
    content = content,
)

@Composable
private fun TitleText(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = OnBackground)

@Composable
private fun Hint(text: String, color: androidx.compose.ui.graphics.Color = OnBackground.copy(0.6f)) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)

@Composable
private fun TobaccoHelp() {
    val uri = LocalUriHandler.current
    Hint(stringResource(R.string.withdrawal_tobacco_help))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        OutlinedButton(onClick = { runCatching { uri.openUri("tel:3989") } }) { Text(stringResource(R.string.withdrawal_tobacco_call)) }
        TextButton(onClick = { runCatching { uri.openUri("https://www.tabac-info-service.fr") } }) { Text("tabac-info-service.fr") }
    }
}

@Composable
private fun TobaccoSetupCard(onStart: (Int, Double, Int, Int?) -> Unit) {
    var base by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var perPack by remember { mutableStateOf("20") }
    var reduce by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    Card {
        TitleText(stringResource(R.string.withdrawal_tobacco_title))
        Hint(stringResource(R.string.withdrawal_tobacco_intro))
        OutlinedTextField(base, { base = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.withdrawal_tobacco_baseline)) }, singleLine = true, keyboardOptions = num)
        OutlinedTextField(price, { price = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) }, label = { Text(stringResource(R.string.withdrawal_tobacco_pack_price)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(perPack, { perPack = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.withdrawal_tobacco_per_pack)) }, singleLine = true, keyboardOptions = num)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            FilterChip(selected = !reduce, onClick = { reduce = false }, label = { Text(stringResource(R.string.withdrawal_tobacco_mode_quit)) })
            FilterChip(selected = reduce, onClick = { reduce = true }, label = { Text(stringResource(R.string.withdrawal_tobacco_mode_reduce)) })
        }
        if (reduce) OutlinedTextField(target, { target = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.withdrawal_tobacco_target)) }, singleLine = true, keyboardOptions = num)
        val b = base.toIntOrNull()
        val pr = price.replace(',', '.').toDoubleOrNull()
        val pp = perPack.toIntOrNull()?.takeIf { it > 0 }
        val t = if (reduce) target.toIntOrNull() else null
        Button(
            onClick = { onStart(b!!, pr!!, pp!!, t) },
            enabled = b != null && b > 0 && pr != null && pp != null && (!reduce || (t != null && t < b)),
        ) { Text(stringResource(R.string.withdrawal_tobacco_start)) }
        TobaccoHelp()
    }
}

@Composable
private fun TobaccoCard(plan: TobaccoPlan, s: TobaccoStats, now: LocalDateTime, onAdd: () -> Unit, onRemove: () -> Unit, onStop: () -> Unit) {
    Card {
        TitleText(stringResource(R.string.withdrawal_tobacco_title))
        Hint(plan.targetPerDay?.let { stringResource(R.string.withdrawal_tobacco_plan_reduce, it, plan.startDate.format(dateFmt)) }
            ?: stringResource(R.string.withdrawal_tobacco_plan_quit, plan.startDate.format(dateFmt)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(stringResource(R.string.withdrawal_tobacco_today, s.todayCount), style = MaterialTheme.typography.bodyLarge,
                color = if (s.todayOverTarget) AccentCoral else OnBackground, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRemove, enabled = s.todayCount > 0) { Text("−") }
            Button(onClick = onAdd) { Text("+1") }
        }
        Text(stringResource(R.string.withdrawal_tobacco_free_days, s.smokeFreeDays), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
        Text(stringResource(R.string.withdrawal_tobacco_saved, s.cigarettesAvoided, String.format(Locale.getDefault(), "%.2f", s.moneySavedEuros)),
            style = MaterialTheme.typography.bodyMedium, color = OnBackground)
        if (s.todayCount == 0) {
            reachedMilestones(s.lastSmoke, now).lastOrNull()?.let { Hint("✓ " + milestoneLabel(it), Gold) }
            nextMilestone(s.lastSmoke, now)?.let { m ->
                Hint(stringResource(R.string.withdrawal_tobacco_next, formatLeft(Duration.between(now, s.lastSmoke.plus(m.after))), milestoneLabel(m)))
            }
            Hint(stringResource(R.string.withdrawal_tobacco_who), OnBackground.copy(0.4f))
        }
        Hint(stringResource(R.string.withdrawal_tobacco_nrt))
        TobaccoHelp()
        TextButton(onClick = onStop) { Text(stringResource(R.string.withdrawal_tobacco_stop), color = OnBackground.copy(0.6f)) }
    }
}

@Composable
private fun milestoneLabel(m: QuitMilestone): String = stringResource(when (m) {
    QuitMilestone.HEART_RATE -> R.string.withdrawal_m_heart_rate
    QuitMilestone.CARBON_MONOXIDE -> R.string.withdrawal_m_co
    QuitMilestone.CIRCULATION -> R.string.withdrawal_m_circulation
    QuitMilestone.COUGH -> R.string.withdrawal_m_cough
    QuitMilestone.HEART_RISK_HALF -> R.string.withdrawal_m_heart_half
    QuitMilestone.STROKE -> R.string.withdrawal_m_stroke
    QuitMilestone.LUNG_CANCER_HALF -> R.string.withdrawal_m_lung
    QuitMilestone.HEART_RISK_NORMAL -> R.string.withdrawal_m_heart_normal
})

@Composable
private fun formatLeft(d: Duration): String = when {
    d.toDays() >= 1 -> stringResource(R.string.withdrawal_left_days, d.toDays().toInt())
    d.toHours() >= 1 -> stringResource(R.string.withdrawal_left_hours, d.toHours().toInt())
    else -> stringResource(R.string.withdrawal_left_minutes, d.toMinutes().toInt().coerceAtLeast(1))
}

@Composable
private fun TaperCard(plan: TaperPlan?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card {
        TitleText(stringResource(R.string.withdrawal_taper_title))
        Hint(stringResource(R.string.withdrawal_taper_warning), AccentCoral)
        if (plan == null) {
            Hint(stringResource(R.string.withdrawal_taper_intro))
            Button(onClick = onEdit) { Text(stringResource(R.string.withdrawal_taper_enter)) }
        } else {
            val st = taperStatus(plan, LocalDate.now())
            Text(plan.medicationName, style = MaterialTheme.typography.bodyLarge, color = OnBackground)
            Text(st.current?.let { stringResource(R.string.withdrawal_taper_current, st.stepIndex + 1, st.stepCount, it.dose) }
                ?: stringResource(R.string.withdrawal_taper_not_started), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
            st.next?.let { Hint(stringResource(R.string.withdrawal_taper_next, it.startDate.format(dateFmt), it.dose)) }
                ?: st.current?.let { Hint(stringResource(R.string.withdrawal_taper_last_step)) }
            plan.sortedSteps.forEach { Hint("${it.startDate.format(dateFmt)} — ${it.dose}") }
            if (plan.note.isNotBlank()) Hint(plan.note)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.common_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), color = OnBackground.copy(0.6f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaperEditorDialog(plan: TaperPlan?, onDismiss: () -> Unit, onSave: (String, List<TaperStep>, String) -> Unit) {
    var name by remember { mutableStateOf(plan?.medicationName ?: "") }
    var note by remember { mutableStateOf(plan?.note ?: "") }
    var steps by remember { mutableStateOf(plan?.sortedSteps ?: emptyList()) }
    var newDate by remember { mutableStateOf<LocalDate?>(null) }
    var newDose by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (newDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { newDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                pickDate = false
            }) { Text(stringResource(R.string.common_save)) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state = state) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.withdrawal_taper_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Hint(stringResource(R.string.withdrawal_taper_editor_hint))
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.withdrawal_taper_name)) }, singleLine = true)
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.startDate.format(dateFmt)} — ${s.dose}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { steps = steps.filterIndexed { j, _ -> j != i } }) { Text("✕") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    TextButton(onClick = { pickDate = true }) { Text(newDate?.format(dateFmt) ?: stringResource(R.string.withdrawal_taper_step_date)) }
                    OutlinedTextField(newDose, { newDose = it.take(60) }, label = { Text(stringResource(R.string.withdrawal_taper_step_dose)) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton(
                    onClick = { steps = (steps + TaperStep(newDate!!, newDose.trim())).sortedBy { it.startDate }; newDate = null; newDose = "" },
                    enabled = newDate != null && newDose.isNotBlank() && steps.none { it.startDate == newDate },
                ) { Text(stringResource(R.string.withdrawal_taper_add_step)) }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text(stringResource(R.string.withdrawal_taper_note)) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, steps, note) }, enabled = name.isNotBlank() && steps.isNotEmpty()) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
