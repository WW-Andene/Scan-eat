package fr.scanneat.presentation.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Sex
import fr.scanneat.presentation.profile.components.ActivitySelector
import fr.scanneat.presentation.profile.components.GoalSelector
import fr.scanneat.presentation.profile.components.OutlinedInput
import fr.scanneat.presentation.profile.components.SexSelector
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.ProfileCapturePage(
    sex: Sex, onSexChange: (Sex) -> Unit,
    ageText: String, onAgeTextChange: (String) -> Unit,
    heightText: String, onHeightTextChange: (String) -> Unit,
    weightText: String, onWeightTextChange: (String) -> Unit,
    activity: ActivityLevel, onActivityChange: (ActivityLevel) -> Unit,
    goal: Goal, onGoalChange: (Goal) -> Unit,
    onSaveAndContinue: suspend (Sex, Int?, Double?, Double?, ActivityLevel, Goal) -> Unit,
    onSaveAndGoToProfile: suspend (Sex, Int?, Double?, Double?, ActivityLevel, Goal) -> Unit,
    onGoToProfileWithoutSaving: () -> Unit,
    onSkip: () -> Unit,
) {
    Text(stringResource(R.string.onboarding_profile_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.onboarding_profile_body),
        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
    )
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        SexSelector(sex, onSexChange)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            OutlinedInput(stringResource(R.string.profile_field_age), ageText, KeyboardType.Number, Modifier.weight(1f)) { onAgeTextChange(it.filter(Char::isDigit)) }
            // ProfileScreen already normalizes comma->period before filtering -
            // this abbreviated onboarding capture didn't, so on this app's
            // default French locale (whose numeric keypad decimal key is a
            // comma) typing "75,5" had the comma silently stripped instead of
            // converted, concatenating both digit groups into "755".
            OutlinedInput(stringResource(R.string.profile_field_height), heightText, KeyboardType.Number, Modifier.weight(1f)) { onHeightTextChange(it.replace(',', '.').filter { c -> c.isDigit() || c == '.' }) }
            OutlinedInput(stringResource(R.string.profile_field_weight), weightText, KeyboardType.Number, Modifier.weight(1f)) { onWeightTextChange(it.replace(',', '.').filter { c -> c.isDigit() || c == '.' }) }
        }
        ActivitySelector(activity, onActivityChange)
        GoalSelector(goal, onGoalChange)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM), modifier = Modifier.fillMaxWidth()) {
        val scope = rememberCoroutineScope()
        val canSave = sex != Sex.NOT_SPECIFIED && ageText.toIntOrNull() != null && heightText.toDoubleOrNull() != null && weightText.toDoubleOrNull() != null
        ScanEatPrimaryButton(
            onClick = {
                scope.launch {
                    onSaveAndContinue(sex, ageText.toIntOrNull()?.coerceIn(1, 120), heightText.toDoubleOrNull()?.coerceIn(50.0, 250.0), weightText.toDoubleOrNull()?.coerceIn(20.0, 400.0), activity, goal)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) { Text(stringResource(R.string.onboarding_continue_button), style = MaterialTheme.typography.titleMedium) }
        TextButton(
            // "More options" still routes to the full Profile screen (diet,
            // allergens, health conditions, goal weight aren't captured here) -
            // whatever was already filled in above is saved first so it isn't
            // silently discarded by following this link instead of "Continue".
            onClick = {
                scope.launch {
                    if (canSave) onSaveAndGoToProfile(sex, ageText.toIntOrNull()?.coerceIn(1, 120), heightText.toDoubleOrNull()?.coerceIn(50.0, 250.0), weightText.toDoubleOrNull()?.coerceIn(20.0, 400.0), activity, goal)
                    else onGoToProfileWithoutSaving()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onboarding_profile_cta), color = OnBackground.copy(0.7f)) }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onboarding_profile_skip), color = OnBackground.copy(0.5f)) }
    }
}
