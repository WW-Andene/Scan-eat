package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.ui.theme.*

private data class OnboardStep(val icon: ImageVector, val title: String, val sub: String, val optional: Boolean = false)

@Composable
private fun rememberOnboardSteps(): List<OnboardStep> = listOf(
    OnboardStep(Icons.Default.MonitorHeart, stringResource(R.string.biolism_onboard_step0_title), stringResource(R.string.biolism_onboard_step0_sub)),
    OnboardStep(Icons.Default.Person, stringResource(R.string.biolism_onboard_step1_title), stringResource(R.string.biolism_onboard_step1_sub)),
    OnboardStep(Icons.Default.Straighten, stringResource(R.string.biolism_onboard_step2_title), stringResource(R.string.biolism_onboard_step2_sub), optional = true),
    OnboardStep(Icons.Default.Bolt, stringResource(R.string.biolism_onboard_step3_title), stringResource(R.string.biolism_onboard_step3_sub)),
)

@Composable
fun BiolismOnboardingScreen(viewModel: BiolismProfileViewModel = hiltViewModel()) {
    val language = viewModel.language.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(0) }
    var sex by remember { mutableStateOf(BiolismSex.NOT_SPECIFIED) }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var activityId by remember { mutableStateOf("sedentary") }

    // No bound at all previously - an errant value here (e.g. a typo'd extra
    // digit) silently propagates into BiolismEngine's TDEE/BMI/body-fat% math.
    // Clamped to the same sane human ranges BiolismProfileScreen's own save()
    // already uses, rather than rejecting outright, since a slightly-off real
    // value should still save, just not corrupt downstream math.
    fun buildProfile() = BiolismProfile(
        sex = sex, ageYears = age.toIntOrNull()?.coerceIn(1, 120) ?: 0,
        heightCm = height.replace(',', '.').toDoubleOrNull()?.coerceIn(50.0, 250.0) ?: 0.0,
        weightKg = weight.replace(',', '.').toDoubleOrNull()?.coerceIn(20.0, 400.0) ?: 0.0,
        activityId = activityId,
        waistCm = waist.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
        hipCm = hip.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
        neckCm = neck.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0,
    )

    val canAdvance = when (step) {
        1 -> sex != BiolismSex.NOT_SPECIFIED && age.isNotBlank() && height.isNotBlank() && weight.isNotBlank()
        else -> true
    }
    val onboardSteps = rememberOnboardSteps()
    val s = onboardSteps[step]

    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(CardRadius.PROMINENT), color = SurfaceVariant, modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Column(
                Modifier.padding(Spacing.XL).verticalScroll(rememberScrollState()).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.L),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1 until onboardSteps.size).forEach { i ->
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
                    if (s.optional) Text(stringResource(R.string.biolism_onboard_optional_suffix), style = MaterialTheme.typography.bodyMedium, color = Teal, fontWeight = FontWeight.Bold)
                }

                when (step) {
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            listOf(BiolismSex.MALE to stringResource(R.string.biolism_onboard_male), BiolismSex.FEMALE to stringResource(R.string.biolism_onboard_female)).forEach { (v, label) ->
                                FilterChip(selected = sex == v, onClick = { sex = v }, label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold))
                            }
                        }
                        OnboardField(stringResource(R.string.biolism_onboard_age_label), age, KeyboardType.Number) { age = it }
                        OnboardField(stringResource(R.string.biolism_onboard_height_label), height, KeyboardType.Decimal) { height = it }
                        OnboardField(stringResource(R.string.biolism_onboard_weight_label), weight, KeyboardType.Decimal) { weight = it }
                        // Live BMI preview — previously a user had no feedback on
                        // what their numbers meant until they finished onboarding
                        // and opened the Biolism data screen.
                        val h = height.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val w = weight.replace(',', '.').toDoubleOrNull() ?: 0.0
                        if (h > 0 && w > 0) {
                            val bmi = w / ((h / 100.0) * (h / 100.0))
                            val bmiColor = when {
                                bmi < 18.5 -> fr.scanneat.presentation.ui.theme.Teal
                                bmi < 25.0 -> semanticGreen()
                                bmi < 30.0 -> semanticAmber()
                                else       -> semanticRed()
                            }
                            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = bmiColor.copy(0.12f), border = androidx.compose.foundation.BorderStroke(1.dp, bmiColor.copy(0.3f))) {
                                Text(
                                    stringResource(R.string.biolism_onboard_bmi_preview, "%.1f".format(java.util.Locale.US, bmi)),
                                    modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                                    style = MaterialTheme.typography.labelMedium, color = bmiColor, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OnboardField(stringResource(R.string.biolism_onboard_waist_label), waist, KeyboardType.Decimal) { waist = it }
                        OnboardField(stringResource(R.string.biolism_onboard_hip_label), hip, KeyboardType.Decimal) { hip = it }
                        OnboardField(stringResource(R.string.biolism_onboard_neck_label), neck, KeyboardType.Decimal) { neck = it }
                    }
                    3 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ACTIVITY_LEVELS.forEach { lvl ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL))
                                    .background(if (activityId == lvl.id) GoldHaze else OnBackground.copy(0.03f))
                                    .selectable(selected = activityId == lvl.id, onClick = { activityId = lvl.id }, role = Role.RadioButton)
                                    .padding(Spacing.M),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(lvl.label(language.value), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Medium)
                                    Text(lvl.note(language.value), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                                }
                                RadioButton(selected = activityId == lvl.id, onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Gold))
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // Skip must stay reachable from every step, not just the
                    // first — a user mid-way through (e.g. stuck on step 1's
                    // required sex/age/height/weight fields) previously had no
                    // way out except repeatedly tapping Back to step 0 first.
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        if (step > 0) {
                            TextButton(onClick = { step -= 1 }) { Text(stringResource(R.string.biolism_onboard_back), color = OnBackground.copy(0.5f)) }
                        }
                        TextButton(onClick = { viewModel.skipOnboarding() }) {
                            Text(stringResource(R.string.biolism_onboard_skip), color = OnBackground.copy(0.5f))
                        }
                    }
                    Button(
                        onClick = {
                            if (step < onboardSteps.size - 1) step += 1
                            else viewModel.completeOnboarding(buildProfile())
                        },
                        enabled = canAdvance,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, disabledContainerColor = Gold.copy(0.3f)),
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                    ) {
                        Text(if (step < onboardSteps.size - 1) stringResource(R.string.biolism_onboard_next) else stringResource(R.string.biolism_onboard_finish), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardField(label: String, value: String, keyboardType: KeyboardType, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.18f),
            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
        ),
    )
}
