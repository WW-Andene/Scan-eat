package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.ui.theme.*

private data class OnboardStep(val icon: ImageVector, val title: String, val sub: String, val optional: Boolean = false)

private val ONBOARD_STEPS = listOf(
    OnboardStep(Icons.Default.MonitorHeart, "Bienvenue dans Biolism",
        "Configurons ton profil en 3 étapes rapides. Ces données restent sur ton appareil et alimentent chaque calcul."),
    OnboardStep(Icons.Default.Person, "Qui es-tu ?",
        "Sexe et âge calibrent ta formule de BMR. Taille et poids sont les entrées principales."),
    OnboardStep(Icons.Default.Straighten, "Mesures corporelles",
        "Débloque le BF% Navy, le ratio taille/hanches et les indicateurs de graisse viscérale.", optional = true),
    OnboardStep(Icons.Default.Bolt, "Niveau d'activité",
        "À quel point es-tu actif au quotidien ? Ceci multiplie ton BMR pour estimer ta dépense totale."),
)

@Composable
fun BiolismOnboardingScreen(viewModel: BiolismProfileViewModel = hiltViewModel()) {
    var step by remember { mutableStateOf(0) }
    var sex by remember { mutableStateOf(BiolismSex.NOT_SPECIFIED) }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var activityId by remember { mutableStateOf("sedentary") }

    fun buildProfile() = BiolismProfile(
        sex = sex, ageYears = age.toIntOrNull() ?: 0,
        heightCm = height.toDoubleOrNull() ?: 0.0, weightKg = weight.toDoubleOrNull() ?: 0.0,
        activityId = activityId,
        waistCm = waist.toDoubleOrNull() ?: 0.0, hipCm = hip.toDoubleOrNull() ?: 0.0, neckCm = neck.toDoubleOrNull() ?: 0.0,
    )

    val canAdvance = when (step) {
        1 -> sex != BiolismSex.NOT_SPECIFIED && age.isNotBlank() && height.isNotBlank() && weight.isNotBlank()
        else -> true
    }
    val s = ONBOARD_STEPS[step]

    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = SurfaceVariant, modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1 until ONBOARD_STEPS.size).forEach { i ->
                        Box(
                            Modifier.weight(1f).height(4.dp).background(
                                when {
                                    step > i -> Gold
                                    step == i -> Gold.copy(0.5f)
                                    else -> OnBackground.copy(0.15f)
                                },
                                RoundedCornerShape(2.dp),
                            ),
                        )
                    }
                }

                Icon(s.icon, null, tint = Gold, modifier = Modifier.size(36.dp))
                Text(s.title, style = MaterialTheme.typography.titleLarge, color = OnBackground, fontWeight = FontWeight.Bold)
                Row {
                    Text(s.sub, style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.6f))
                    if (s.optional) Text(" (optionnel)", style = MaterialTheme.typography.bodyMedium, color = Teal, fontWeight = FontWeight.Bold)
                }

                when (step) {
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(BiolismSex.MALE to "Homme", BiolismSex.FEMALE to "Femme").forEach { (v, label) ->
                                FilterChip(selected = sex == v, onClick = { sex = v }, label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold))
                            }
                        }
                        OnboardField("Âge (ans)", age, KeyboardType.Number) { age = it }
                        OnboardField("Taille (cm)", height, KeyboardType.Decimal) { height = it }
                        OnboardField("Poids (kg)", weight, KeyboardType.Decimal) { weight = it }
                    }
                    2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OnboardField("Tour de taille (cm)", waist, KeyboardType.Decimal) { waist = it }
                        OnboardField("Tour des hanches (cm)", hip, KeyboardType.Decimal) { hip = it }
                        OnboardField("Tour de cou (cm)", neck, KeyboardType.Decimal) { neck = it }
                    }
                    3 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ACTIVITY_LEVELS.forEach { lvl ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(if (activityId == lvl.id) GoldHaze else OnBackground.copy(0.03f))
                                    .clickable { activityId = lvl.id }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(lvl.label, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Medium)
                                    Text(lvl.note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                                }
                                RadioButton(selected = activityId == lvl.id, onClick = { activityId = lvl.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = Gold))
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step == 0) {
                        TextButton(onClick = { viewModel.skipOnboarding() }) {
                            Text("Passer", color = OnBackground.copy(0.5f))
                        }
                    } else {
                        TextButton(onClick = { step -= 1 }) { Text("Retour", color = OnBackground.copy(0.5f)) }
                    }
                    Button(
                        onClick = {
                            if (step < ONBOARD_STEPS.size - 1) step += 1
                            else viewModel.completeOnboarding(buildProfile())
                        },
                        enabled = canAdvance,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, disabledContainerColor = Gold.copy(0.3f)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (step < ONBOARD_STEPS.size - 1) "Suivant" else "Terminer", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardField(label: String, value: String, keyboardType: KeyboardType, onValue: (String) -> Unit) {
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
