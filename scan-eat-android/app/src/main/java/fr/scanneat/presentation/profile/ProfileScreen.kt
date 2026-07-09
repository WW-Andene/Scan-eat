package fr.scanneat.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val profile = viewModel.profile.collectAsStateWithLifecycle()
    val bmi     = viewModel.bmiValue.collectAsStateWithLifecycle()
    val tdee    = viewModel.tdee.collectAsStateWithLifecycle()
    val saved   = viewModel.saved.collectAsStateWithLifecycle()

    // Local mutable state mirrors the saved profile
    var name       by remember(profile.value.id) { mutableStateOf(profile.value.name) }
    var sex        by remember(profile.value.id) { mutableStateOf(profile.value.sex) }
    var age        by remember(profile.value.id) { mutableStateOf(profile.value.ageYears?.toString() ?: "") }
    var heightCm   by remember(profile.value.id) { mutableStateOf(profile.value.heightCm?.toString() ?: "") }
    var weightKg   by remember(profile.value.id) { mutableStateOf(profile.value.weightKg?.toString() ?: "") }
    var goalWeightKg by remember(profile.value.id) { mutableStateOf(profile.value.goalWeightKg?.toString() ?: "") }
    var activity   by remember(profile.value.id) { mutableStateOf(profile.value.activityLevel) }
    var goal       by remember(profile.value.id) { mutableStateOf(profile.value.goal) }
    var diet       by remember(profile.value.id) { mutableStateOf(profile.value.diet) }
    var allergens  by remember(profile.value.id) { mutableStateOf(profile.value.allergens) }
    var isMenstruating by remember(profile.value.id) { mutableStateOf(profile.value.isMenstruating) }

    LaunchedEffect(saved.value) {
        if (saved.value) { viewModel.clearSaved(); onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mon profil", color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = OnBackground) } },
                actions = {
                    TextButton(onClick = {
                        viewModel.save(Profile(
                            name          = name.trim(),
                            sex           = sex,
                            ageYears      = age.toIntOrNull(),
                            heightCm      = heightCm.toDoubleOrNull(),
                            weightKg      = weightKg.toDoubleOrNull(),
                            goalWeightKg  = goalWeightKg.toDoubleOrNull(),
                            activityLevel = activity,
                            goal          = goal,
                            diet          = diet,
                            allergens     = allergens,
                            isMenstruating = isMenstruating,
                        ))
                    }) {
                        Text("Sauvegarder", color = AccentGreen, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---- BMI / TDEE preview ----
            if (bmi.value != null || tdee.value != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        bmi.value?.let { MetricChip("IMC", "${it}") }
                        tdee.value?.let { MetricChip("TDEE", "${it.roundToInt()} kcal") }
                    }
                }
            }

            // ---- Identity ----
            ProfileSection("Identité") {
                OutlinedInput("Prénom (optionnel)", name) { name = it }
                SexSelector(sex) { sex = it }
                OutlinedInput("Âge (ans)", age, KeyboardType.Number) { age = it }
            }

            // ---- Body ----
            ProfileSection("Corps") {
                OutlinedInput("Taille (cm)", heightCm, KeyboardType.Decimal) { heightCm = it }
                OutlinedInput("Poids actuel (kg)", weightKg, KeyboardType.Decimal) { weightKg = it }
                OutlinedInput("Poids cible (kg)", goalWeightKg, KeyboardType.Decimal) { goalWeightKg = it }
                if (sex == Sex.FEMALE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isMenstruating,
                            onCheckedChange = { isMenstruating = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentGreen),
                        )
                        Text("En âge menstruel (ajuste le besoin en fer)", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
                    }
                }
            }

            // ---- Activity ----
            ProfileSection("Activité") {
                ActivitySelector(activity) { activity = it }
                GoalSelector(goal) { goal = it }
            }

            // ---- Diet ----
            ProfileSection("Alimentation") {
                DietSelector(diet) { diet = it }
            }

            // ---- Allergens ----
            ProfileSection("Allergènes déclarés") {
                AllergenSelector(allergens) { allergens = it }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

}

// ---- Sub-composables ----

@Composable
private fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun OutlinedInput(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValue,
        label         = { Text(label, fontSize = 13.sp) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape  = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentGreen,
            unfocusedBorderColor = OnBackground.copy(0.2f),
            focusedTextColor     = OnBackground,
            unfocusedTextColor   = OnBackground,
        ),
    )
}

@Composable
private fun SexSelector(current: Sex, onSelect: (Sex) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.values().forEach { s ->
            val label = when (s) { Sex.MALE -> "Homme"; Sex.FEMALE -> "Femme"; Sex.NOT_SPECIFIED -> "Non précisé" }
            FilterChip(
                selected = current == s, onClick = { onSelect(s) }, label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                    labelColor = OnBackground.copy(0.7f),
                ),
            )
        }
    }
}

@Composable
private fun ActivitySelector(current: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    val labels = mapOf(
        ActivityLevel.SEDENTARY to "Sédentaire", ActivityLevel.LIGHTLY_ACTIVE to "Peu actif",
        ActivityLevel.MODERATELY_ACTIVE to "Modéré", ActivityLevel.VERY_ACTIVE to "Très actif",
        ActivityLevel.EXTRA_ACTIVE to "Extrême",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ActivityLevel.values().forEach { lvl ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(labels[lvl] ?: lvl.name, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                RadioButton(
                    selected = current == lvl, onClick = { onSelect(lvl) },
                    colors = RadioButtonDefaults.colors(selectedColor = AccentGreen),
                )
            }
            if (lvl != ActivityLevel.EXTRA_ACTIVE) HorizontalDivider(thickness = 0.5.dp, color = OnBackground.copy(0.08f))
        }
    }
}

@Composable
private fun GoalSelector(current: Goal, onSelect: (Goal) -> Unit) {
    val labels = mapOf(Goal.LOSE to "Perdre du poids", Goal.MAINTAIN to "Maintenir", Goal.GAIN to "Prendre du poids")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Goal.values().forEach { g ->
            FilterChip(
                selected = current == g, onClick = { onSelect(g) },
                label = { Text(labels[g] ?: g.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                    labelColor = OnBackground.copy(0.7f),
                ),
            )
        }
    }
}

@Composable
private fun DietSelector(current: DietKey, onSelect: (DietKey) -> Unit) {
    val diets = DietKey.values().filter { it != DietKey.NONE }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = current == DietKey.NONE, onClick = { onSelect(DietKey.NONE) },
            label = { Text("Aucun", fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                labelColor = OnBackground.copy(0.7f),
            ),
        )
        diets.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    FilterChip(
                        selected = current == d, onClick = { onSelect(d) },
                        label = { Text(d.labelFr, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                            labelColor = OnBackground.copy(0.7f),
                        ),
                    )
                }
            }
        }
    }
}

private val ALLERGEN_LABELS = mapOf(
    "gluten" to "Gluten", "lactose" to "Lactose", "eggs" to "Œufs",
    "nuts" to "Fruits à coque", "peanuts" to "Arachides", "soy" to "Soja",
    "fish" to "Poisson", "crustaceans" to "Crustacés", "molluscs" to "Mollusques",
    "sesame" to "Sésame", "celery" to "Céleri", "mustard" to "Moutarde",
    "sulfites" to "Sulfites", "lupin" to "Lupin",
)

@Composable
private fun AllergenSelector(current: Set<String>, onSelect: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sélectionne les allergènes à surveiller", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        ALLERGEN_LABELS.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, label) ->
                    FilterChip(
                        selected = key in current,
                        onClick  = {
                            onSelect(if (key in current) current - key else current + key)
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberWarning.copy(0.2f), selectedLabelColor = AmberWarning,
                            labelColor = OnBackground.copy(0.7f),
                        ),
                    )
                }
            }
        }
    }
}
