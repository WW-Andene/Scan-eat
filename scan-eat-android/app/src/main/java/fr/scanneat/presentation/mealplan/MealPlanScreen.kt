package fr.scanneat.presentation.mealplan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter


private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d")
private val MEALS   = listOf("breakfast", "lunch", "dinner", "snack")
private val MEAL_LABELS = mapOf("breakfast" to "Petit-dej", "lunch" to "Déjeuner", "dinner" to "Dîner", "snack" to "Collation")

@Composable
fun MealPlanScreen(
    viewModel: MealPlanViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val plan = viewModel.weekPlan.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan de repas", color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = OnBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(viewModel.weekDates) { date ->
                val dayPlan = plan.value[date] ?: DayPlan(date)
                val isToday = date == LocalDate.now()
                Surface(shape = RoundedCornerShape(14.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            date.format(DAY_FMT) + if (isToday) " · Aujourd'hui" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isToday) AccentGreen else OnSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        MEALS.forEach { meal ->
                            val slot = dayPlan[meal]
                            MealPlanRow(
                                meal  = MEAL_LABELS[meal] ?: meal,
                                slot  = slot,
                                onEdit = { text -> viewModel.setNote(date, meal, text) },
                                onClear = { viewModel.clear(date, meal) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

}

@Composable
private fun MealPlanRow(meal: String, slot: MealPlanSlot?, onEdit: (String) -> Unit, onClear: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(slot) { mutableStateOf((slot as? MealPlanSlot.NoteSlot)?.text ?: "") }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(meal, style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.6f), modifier = Modifier.width(72.dp))
        if (editing) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
            )
            IconButton(onClick = { onEdit(text); editing = false }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Check, "OK", tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
        } else {
            val label = when (slot) {
                is MealPlanSlot.NoteSlot     -> slot.text
                is MealPlanSlot.RecipeSlot   -> "📖 ${slot.name}"
                is MealPlanSlot.TemplateSlot -> "📋 ${slot.name}"
                null -> "—"
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = if (slot != null) OnSurface else OnSurface.copy(0.3f), modifier = Modifier.weight(1f))
            IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Modifier", tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
            if (slot != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Effacer", tint = OnSurface.copy(0.3f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
