package fr.scanneat.presentation.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.engine.scoring.dietNote
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Sex
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SeparatorLight
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.semanticAmber

/** Profile-form selectors — stateless, all state lives in the caller (ProfileScreen/ProfileViewModel). */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SexSelector(current: Sex, onSelect: (Sex) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Sex.values().forEach { s ->
            val label = when (s) {
                Sex.MALE -> stringResource(R.string.sex_male)
                Sex.FEMALE -> stringResource(R.string.sex_female)
                Sex.NOT_SPECIFIED -> stringResource(R.string.sex_not_specified)
            }
            FilterChip(
                selected = current == s, onClick = { onSelect(s) }, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    labelColor = OnBackground.copy(0.7f),
                ),
            )
        }
    }
}

@Composable
internal fun ActivitySelector(current: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    val labels = mapOf(
        ActivityLevel.SEDENTARY to stringResource(R.string.activity_sedentary), ActivityLevel.LIGHTLY_ACTIVE to stringResource(R.string.activity_lightly_active),
        ActivityLevel.MODERATELY_ACTIVE to stringResource(R.string.activity_moderately_active), ActivityLevel.VERY_ACTIVE to stringResource(R.string.activity_very_active),
        ActivityLevel.EXTRA_ACTIVE to stringResource(R.string.activity_extra_active),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ActivityLevel.values().forEach { lvl ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .selectable(selected = current == lvl, onClick = { onSelect(lvl) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(labels[lvl] ?: lvl.name, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                RadioButton(
                    selected = current == lvl, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = AccentCoral),
                )
            }
            if (lvl != ActivityLevel.EXTRA_ACTIVE) HorizontalDivider(thickness = 0.5.dp, color = SeparatorLight)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalSelector(current: Goal, onSelect: (Goal) -> Unit) {
    val labels = mapOf(
        Goal.LOSE to stringResource(R.string.goal_lose),
        Goal.MAINTAIN to stringResource(R.string.goal_maintain),
        Goal.GAIN to stringResource(R.string.goal_gain),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Goal.values().forEach { g ->
            FilterChip(
                selected = current == g, onClick = { onSelect(g) },
                label = { Text(labels[g] ?: g.name, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    labelColor = OnBackground.copy(0.7f),
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DietSelector(current: DietKey, onSelect: (DietKey) -> Unit) {
    val diets = DietKey.values().filter { it != DietKey.NONE }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FilterChip(
            selected = current == DietKey.NONE, onClick = { onSelect(DietKey.NONE) },
            label = { Text(stringResource(R.string.diet_none), style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                labelColor = OnBackground.copy(0.7f),
            ),
        )
        val isEnglish = Locale.current.language == "en"
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            diets.forEach { d ->
                FilterChip(
                    selected = current == d, onClick = { onSelect(d) },
                    label = { Text(if (isEnglish) d.labelEn else d.labelFr, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                        labelColor = OnBackground.copy(0.7f),
                    ),
                )
            }
        }
        // Diet chips previously carried zero explanation of what each diet actually
        // excludes (or, for vegan, what supplementation it implies) - every other
        // definitional detail in DietChecker.kt's DietDef was already there and
        // simply never reached the UI.
        if (current != DietKey.NONE) {
            dietNote(current, if (isEnglish) "en" else "fr")?.let { note ->
                Text(
                    note, style = MaterialTheme.typography.bodySmall,
                    color = OnBackground.copy(0.6f),
                )
            }
        }
    }
}

@Composable
internal fun allergenLabels(): Map<String, String> = mapOf(
    "gluten" to stringResource(R.string.allergen_gluten), "lactose" to stringResource(R.string.allergen_lactose),
    "eggs" to stringResource(R.string.allergen_eggs), "nuts" to stringResource(R.string.allergen_nuts),
    "peanuts" to stringResource(R.string.allergen_peanuts), "soy" to stringResource(R.string.allergen_soy),
    "fish" to stringResource(R.string.allergen_fish), "crustaceans" to stringResource(R.string.allergen_crustaceans),
    "molluscs" to stringResource(R.string.allergen_molluscs), "sesame" to stringResource(R.string.allergen_sesame),
    "celery" to stringResource(R.string.allergen_celery), "mustard" to stringResource(R.string.allergen_mustard),
    "sulfites" to stringResource(R.string.allergen_sulfites), "lupin" to stringResource(R.string.allergen_lupin),
)

@Composable
internal fun conditionLabels(): Map<String, String> = mapOf(
    "diabetes" to stringResource(R.string.condition_diabetes),
    "hypertension" to stringResource(R.string.condition_hypertension),
    "pregnancy" to stringResource(R.string.condition_pregnancy),
    "kidney_disease" to stringResource(R.string.condition_kidney_disease),
    "thyroid_disorder" to stringResource(R.string.condition_thyroid_disorder),
    "food_allergies" to stringResource(R.string.condition_food_allergies),
    "intolerances" to stringResource(R.string.condition_intolerances),
    "digestive_disorders" to stringResource(R.string.condition_digestive_disorders),
    "cancer" to stringResource(R.string.condition_cancer),
    "depression" to stringResource(R.string.condition_depression),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConditionsSelector(current: Set<String>, onSelect: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.profile_condition_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            conditionLabels().forEach { (key, label) ->
                FilterChip(
                    selected = key in current,
                    onClick  = { onSelect(if (key in current) current - key else current + key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Teal.copy(0.2f), selectedLabelColor = Teal,
                        labelColor = OnBackground.copy(0.7f),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllergenSelector(current: Set<String>, onSelect: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.profile_allergen_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            allergenLabels().forEach { (key, label) ->
                FilterChip(
                    selected = key in current,
                    onClick  = {
                        onSelect(if (key in current) current - key else current + key)
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = semanticAmber().copy(0.2f), selectedLabelColor = semanticAmber(),
                        labelColor = OnBackground.copy(0.7f),
                    ),
                )
            }
        }
    }
}
