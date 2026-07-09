package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun BiolismProfileScreen(viewModel: BiolismProfileViewModel = hiltViewModel()) {
    val profile = viewModel.profile.collectAsStateWithLifecycle()
    val saved   = viewModel.saved.collectAsStateWithLifecycle()

    val p = profile.value

    // Local mutable state
    var sex        by remember(p) { mutableStateOf(p.sex) }
    var age        by remember(p) { mutableStateOf(p.ageYears.takeIf { it > 0 }?.toString() ?: "") }
    var height     by remember(p) { mutableStateOf(p.heightCm.takeIf { it > 0 }?.toString() ?: "") }
    var weight     by remember(p) { mutableStateOf(p.weightKg.takeIf { it > 0 }?.toString() ?: "") }
    var activityId by remember(p) { mutableStateOf(p.activityId) }
    var ethnicityId by remember(p) { mutableStateOf(p.ethnicityId) }
    var waist      by remember(p) { mutableStateOf(p.waistCm.takeIf { it > 0 }?.toString() ?: "") }
    var hip        by remember(p) { mutableStateOf(p.hipCm.takeIf { it > 0 }?.toString() ?: "") }
    var neck       by remember(p) { mutableStateOf(p.neckCm.takeIf { it > 0 }?.toString() ?: "") }
    var cycleDay   by remember(p) { mutableStateOf(p.cycleDay.toString()) }

    LaunchedEffect(saved.value) { if (saved.value) viewModel.clearSaved() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        if (saved.value) {
            Surface(shape = RoundedCornerShape(10.dp), color = Teal.copy(0.1f), border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                Text("✓ Profil sauvegardé", modifier = Modifier.padding(12.dp), color = Teal, fontWeight = FontWeight.Bold)
            }
        }

        // ── Identity ──────────────────────────────────────────────────────────
        ProfileSection("Identité") {
            // Sex
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiolismSex.values().forEach { s ->
                    val label = when(s) { BiolismSex.MALE -> "♂ Homme"; BiolismSex.FEMALE -> "♀ Femme"; else -> "Non précisé" }
                    FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold, labelColor = OnBackground.copy(0.6f)))
                }
            }
            BioInput("Âge (ans)", age, KeyboardType.Number) { age = it }
            BioInput("Taille (cm)", height, KeyboardType.Decimal) { height = it }
            BioInput("Poids (kg)", weight, KeyboardType.Decimal) { weight = it }
        }

        // ── Activity ──────────────────────────────────────────────────────────
        ProfileSection("Niveau d'activité") {
            ACTIVITY_LEVELS.forEach { lvl ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(lvl.label, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = if (activityId == lvl.id) FontWeight.SemiBold else FontWeight.Normal)
                        Text(lvl.note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    }
                    RadioButton(selected = activityId == lvl.id, onClick = { activityId = lvl.id },
                        colors = RadioButtonDefaults.colors(selectedColor = Gold))
                }
                if (lvl != ACTIVITY_LEVELS.last()) HorizontalDivider(color = OnBackground.copy(0.06f))
            }
        }

        // ── Ethnicity ─────────────────────────────────────────────────────────
        ProfileSection("Origine ethnique (correction IMC/BF%)") {
            Text("Ajuste le calcul de la masse grasse selon Deurenberg et al. 1998 et les seuils OMS 2004.",
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            ETHNICITY_OPTIONS.forEach { opt ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(opt.label, style = MaterialTheme.typography.bodySmall, color = OnBackground, fontWeight = FontWeight.Medium)
                        Text(opt.note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.35f))
                    }
                    RadioButton(selected = ethnicityId == opt.id, onClick = { ethnicityId = opt.id },
                        colors = RadioButtonDefaults.colors(selectedColor = Gold))
                }
                HorizontalDivider(color = OnBackground.copy(0.05f))
            }
        }

        // ── Circumferences ────────────────────────────────────────────────────
        ProfileSection("Circonférences (déverrouille BF% Navy + indicateurs viscéraux)") {
            Text("Optionnel — fournit Navy BF%, WHtR, WHR (méthode Hodgdon & Beckett 1984).",
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            BioInput("Tour de taille (cm)", waist, KeyboardType.Decimal) { waist = it }
            BioInput("Tour des hanches (cm) — femmes", hip, KeyboardType.Decimal) { hip = it }
            BioInput("Tour de cou (cm)", neck, KeyboardType.Decimal) { neck = it }
        }

        // ── Cycle (female) ────────────────────────────────────────────────────
        if (sex == BiolismSex.FEMALE) {
            ProfileSection("Cycle menstruel (hormones phase-précises)") {
                Text("Jour 1 = premier jour des règles. Utilisé pour modéliser E2 et progestérone selon la phase.",
                    style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                BioInput("Jour du cycle (1–28)", cycleDay, KeyboardType.Number) { v -> if (v.toIntOrNull()?.let { it in 1..28 } != false) cycleDay = v }
                Slider(value = (cycleDay.toIntOrNull() ?: 14).toFloat(), onValueChange = { cycleDay = it.toInt().toString() },
                    valueRange = 1f..28f, steps = 26, colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
                val cd = cycleDay.toIntOrNull() ?: 14
                val phaseLabel = when {
                    cd <= 5  -> "Menstruelle · E2 bas ~50 pg/mL"
                    cd <= 13 -> "Folliculaire · E2 en hausse 60–200 pg/mL"
                    cd == 14 -> "Ovulation · pic E2 ~350 pg/mL"
                    cd <= 21 -> "Lutéale précoce · E2 100–200 pg/mL, progest. en hausse"
                    else     -> "Lutéale tardive · E2 et progest. en baisse"
                }
                Text("Jour $cd : $phaseLabel", style = MaterialTheme.typography.bodySmall, color = Violet)
            }
        }

        // ── Save ──────────────────────────────────────────────────────────────
        Button(
            onClick = {
                viewModel.save(BiolismProfile(
                    sex         = sex,
                    ageYears    = age.toIntOrNull() ?: 0,
                    heightCm    = height.toDoubleOrNull() ?: 0.0,
                    weightKg    = weight.toDoubleOrNull() ?: 0.0,
                    activityId  = activityId,
                    ethnicityId = ethnicityId,
                    waistCm     = waist.toDoubleOrNull() ?: 0.0,
                    hipCm       = hip.toDoubleOrNull() ?: 0.0,
                    neckCm      = neck.toDoubleOrNull() ?: 0.0,
                    cycleDay    = cycleDay.toIntOrNull()?.coerceIn(1, 28) ?: 14,
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Sauvegarder le profil", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Gold, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun BioInput(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.18f),
            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
        ),
    )
}
