package fr.scanneat.presentation.activity.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.health.ActivityRiskType
import fr.scanneat.domain.engine.health.OvertrainingSeverity
import fr.scanneat.domain.engine.health.OvertrainingWarning

/**
 * User-requested: does the overtraining warning say *what kind* of risk it
 * is, or just "overtraining"? Previously the latter - this renders every
 * [OvertrainingWarning.riskTypes] the check flagged (always at least
 * [ActivityRiskType.OVERUSE_INJURY], plus e.g. cardiac/dehydration/
 * hypoglycemia when the specific person/session combination warrants them -
 * see checkDailyOvertraining's own doc comment) as one message.
 *
 * Two entry points sharing [riskLabelRes]/[headlineRes] below so
 * AddActivityDialog's live inline text (composable context) and
 * ActivityScreen's post-save snackbar (LaunchedEffect's suspend collector,
 * not composable) never drift out of sync.
 */
@Composable
internal fun overtrainingMessage(warning: OvertrainingWarning): String {
    val headline = stringResource(headlineRes(warning), warning.totalMinutes)
    val riskLabels = warning.riskTypes.map { stringResource(riskLabelRes(it)) }
    return "$headline — ${riskLabels.joinToString(", ")}"
}

/** Non-composable counterpart for use inside a suspend/LaunchedEffect collector. */
internal fun overtrainingMessage(warning: OvertrainingWarning, context: Context): String {
    val headline = context.getString(headlineRes(warning), warning.totalMinutes)
    val riskLabels = warning.riskTypes.map { context.getString(riskLabelRes(it)) }
    return "$headline — ${riskLabels.joinToString(", ")}"
}

private fun headlineRes(warning: OvertrainingWarning): Int =
    if (warning.severity == OvertrainingSeverity.HIGH) R.string.activity_overtraining_high
    else R.string.activity_overtraining_moderate

private fun riskLabelRes(risk: ActivityRiskType): Int = when (risk) {
    ActivityRiskType.OVERUSE_INJURY  -> R.string.activity_risk_overuse_injury
    ActivityRiskType.CARDIAC_STRAIN  -> R.string.activity_risk_cardiac
    ActivityRiskType.DEHYDRATION     -> R.string.activity_risk_dehydration
    ActivityRiskType.HYPOGLYCEMIA    -> R.string.activity_risk_hypoglycemia
    ActivityRiskType.MASKED_EXERTION -> R.string.activity_risk_masked_exertion
    ActivityRiskType.BLEEDING        -> R.string.activity_risk_bleeding
}
