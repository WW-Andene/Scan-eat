package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber

// ============================================================================
// HEALTH CONDITION CAUTION — closes the same PersonalScoreEngine/hint-panel-
// vs-Biolism gap the food-scoring path already closed for pregnancy/cancer/
// depression: extended fasting and ketogenic states carry real, documented
// risk for some Profile.healthConditions, but Biolism previously showed the
// same guidance to every user regardless of profile.
//
// Sourced conservatively, one line per condition x mode combination that has
// genuinely established, non-controversial public guidance:
//  - Fasting + diabetes: ADA guidance — hypoglycemia risk, particularly on
//    insulin or sulfonylureas; medical supervision advised before extended fasts.
//  - Fasting + pregnancy: ACOG/ANSES guidance — prolonged fasting is
//    generally discouraged during pregnancy (maternal/fetal hypoglycemia and
//    ketosis risk).
//  - Ketosis + diabetes: ADA guidance — euglycemic diabetic ketoacidosis is a
//    documented risk, particularly relevant to type 1 diabetes.
//  - Ketosis + kidney_disease: National Kidney Foundation guidance — the
//    higher protein/fat load of ketogenic eating warrants caution in kidney
//    disease.
//  - Ketosis + pregnancy: ACOG guidance — ketogenic diets are not
//    recommended during pregnancy.
// Not attempting a condition x mode matrix beyond these — a wrong caution
// here is worse than a missing one, same principle as the rest of this
// codebase's health-condition dictionaries.
// ============================================================================

private val FASTING_CAUTIONS: Map<String, Pair<String, String>> = mapOf(
    "diabetes" to (
        "Jeûne prolongé et diabète : risque d'hypoglycémie, en particulier sous insuline ou sulfamides hypoglycémiants — avis médical recommandé avant un jeûne prolongé (recommandations ADA)." to
        "Extended fasting and diabetes: hypoglycemia risk, particularly on insulin or sulfonylureas — medical advice recommended before an extended fast (ADA guidance)."),
    "pregnancy" to (
        "Jeûne prolongé et grossesse : généralement déconseillé (risque d'hypoglycémie et de cétose maternelle/fœtale) — recommandations ACOG/ANSES." to
        "Extended fasting and pregnancy: generally discouraged (maternal/fetal hypoglycemia and ketosis risk) — ACOG/ANSES guidance."),
)

private val KETOSIS_CAUTIONS: Map<String, Pair<String, String>> = mapOf(
    "diabetes" to (
        "Cétose et diabète : risque d'acidocétose euglycémique documenté, en particulier en cas de diabète de type 1 — suivi médical recommandé (recommandations ADA)." to
        "Ketosis and diabetes: documented risk of euglycemic diabetic ketoacidosis, particularly relevant to type 1 diabetes — medical follow-up recommended (ADA guidance)."),
    "kidney_disease" to (
        "Cétose et maladie rénale : la charge protéique/lipidique plus élevée d'une alimentation cétogène nécessite une prudence particulière (recommandations National Kidney Foundation)." to
        "Ketosis and kidney disease: the higher protein/fat load of ketogenic eating warrants particular caution (National Kidney Foundation guidance)."),
    "pregnancy" to (
        "Cétose et grossesse : l'alimentation cétogène n'est pas recommandée pendant la grossesse (recommandations ACOG)." to
        "Ketosis and pregnancy: ketogenic eating is not recommended during pregnancy (ACOG guidance)."),
)

@Composable
private fun CautionBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = semanticAmber().copy(alpha = 0.12f),
        border = BorderStroke(1.dp, semanticAmber().copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = semanticAmber(), modifier = Modifier.padding(top = 2.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = semanticAmber())
        }
    }
}

/** Shown under FastingRow when active and the profile has a matched health condition. */
@Composable
internal fun FastingHealthCaution(healthConditions: Set<String>, lang: String) {
    val en = lang == "en"
    healthConditions.forEach { condition ->
        FASTING_CAUTIONS[condition]?.let { (fr, enText) ->
            CautionBanner(if (en) enText else fr)
        }
    }
}

/** Shown under KetosisToggleRow/AdaptedToggleRow when ketosis is on and the profile has a matched health condition. */
@Composable
internal fun KetosisHealthCaution(healthConditions: Set<String>, lang: String) {
    val en = lang == "en"
    healthConditions.forEach { condition ->
        KETOSIS_CAUTIONS[condition]?.let { (fr, enText) ->
            CautionBanner(if (en) enText else fr)
        }
    }
}
