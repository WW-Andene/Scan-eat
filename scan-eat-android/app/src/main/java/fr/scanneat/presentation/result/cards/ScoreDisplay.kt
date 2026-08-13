package fr.scanneat.presentation.result.cards

import compose.icons.tablericons.Minus
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.domain.model.Grade
import fr.scanneat.presentation.ui.theme.*

/** The score reveal's signature moment: arc animates in, glow intensifies as it
 *  completes. Gated behind reduced-motion (snaps instantly, full glow, no
 *  animation) per the audit's Chain 2 — never ships the motion without the gate.
 *
 *  [target] is already known on first composition (the score arrives with the
 *  rest of the result), so animateFloatAsState alone never animates — it seeds
 *  its Animatable AT the target value on first composition and only animates
 *  on later *changes* to the target. `started` starts false and flips true one
 *  frame later via LaunchedEffect, giving animateFloatAsState an actual 0 → target
 *  transition to play instead of rendering the final state immediately. */
@Composable
private fun rememberScoreReveal(target: Float): Pair<Float, Float> {
    val reducedMotion = rememberReducedMotion()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animatedProgress by animateFloatAsState(
        targetValue   = if (started) target else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = SCORE_REVEAL_DURATION_MS, easing = ScoreRevealEasing),
        label         = "scoreRingProgress",
    )
    val completion = if (target > 0f) (animatedProgress / target).coerceIn(0f, 1f) else 1f
    return animatedProgress to completion
}

/** Small +N / -N / = chip shown below the score ring when a prior scan exists. */
@Composable
internal fun ScoreDeltaChip(delta: Int) {
    val positive = delta > 0
    val neutral  = delta == 0
    val chipColor = when {
        neutral  -> OnBackground.copy(0.15f)
        positive -> semanticGreen().copy(0.18f)
        else     -> semanticRed().copy(0.18f)
    }
    val textColor = when {
        neutral  -> OnBackground.copy(0.5f)
        positive -> semanticGreen()
        else     -> semanticRed()
    }
    Surface(shape = RoundedCornerShape(50), color = chipColor) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.T2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.T2),
        ) {
            Icon(
                imageVector = when {
                    neutral  -> TablerIcons.Minus
                    positive -> TablerIcons.ChevronUp
                    else     -> TablerIcons.ChevronDown
                },
                contentDescription = null,
                tint     = textColor,
                modifier = Modifier.size(IconSize.Tiny),
            )
            Text(
                text  = if (neutral) "=" else "${if (positive) "+" else ""}$delta",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = textColor,
            )
        }
    }
}

@Composable
internal fun ScoreRing(score: Int, grade: Grade, scoreDelta: Int? = null) {
    val color = gradeColor(grade)
    val (animatedProgress, completion) = rememberScoreReveal(score / 100f)
    // The app's one signature ambient motion (docs/design-audit-art-direction-brief.md):
    // once the reveal completes, the glow keeps breathing gently rather than
    // freezing static — this ring is the "second skin" pulse's home.
    val breathingPulse = rememberBreathingPulse()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().height(232.dp), contentAlignment = Alignment.Center) {
            // User-reported regression: the OrganicBlobShape aura layer here read as a
            // rendering bug ("un cercle un peu déformé derrière"), not a deliberate
            // signature — reverted to a plain circular glow. OrganicBlobShape itself
            // stays available (docs/design-audit-art-direction-brief.md's "second skin"
            // shape) for a future, more clearly-intentional use; this ring keeps only
            // the breathing-pulse glow, no asymmetric aura.
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .background(
                        Brush.radialGradient(listOf(color.copy(alpha = 0.24f * completion * breathingPulse), Color.Transparent)),
                        CircleShape,
                    ),
            )
            // User-requested (Notebook theme): "les cercle et gauge doivent
            // être en trait de crayon de couleur" - drawCrayonRing (Glass.kt)
            // instead of Material's smooth CircularProgressIndicator arc for
            // this theme; every other theme is unaffected.
            if (LocalThemeName.current == "notebook") {
                Canvas(Modifier.size(178.dp)) {
                    drawCrayonRing(progress = animatedProgress, color = color, trackColor = SurfaceVariant, strokeWidthPx = 14.dp.toPx())
                }
            } else {
                CircularProgressIndicator(
                    progress    = { animatedProgress },
                    modifier    = Modifier.size(178.dp),
                    color       = color,
                    strokeWidth = 14.dp,
                    trackColor  = SurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Nutritionist/public-bench audit: unlike DualScoreRing (which labels
                // its classic score "Score classique"), this single-ring view showed
                // only the bare letter with nothing naming whose grade it is - right
                // next to ScoreBadgesRow's explicitly-labeled "NutriScore" badge above.
                // Two differently-scaled systems sharing a letter (this app's 7-tier
                // grade vs. Nutri-Score's 5-tier one) isn't itself a problem, the same
                // way two different exams both grading A-F don't need new letters just
                // because they measure different things - but only if each is actually
                // named. This one wasn't.
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), modifier = Modifier.notebookTextJitter())
                // User-reported: 56sp read as too large for a single-score display
                // (DualScoreRing's 26sp comparison view was unaffected/correctly sized).
                Text(grade.label, style = HeroNumberStyle.copy(fontSize = 44.sp), color = color, modifier = Modifier.notebookTextJitter())
                Text(stringResource(R.string.result_score_out_of_100, score),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = OnBackground.copy(0.6f), modifier = Modifier.notebookTextJitter())
            }
        }
        if (scoreDelta != null) {
            ScoreDeltaChip(scoreDelta)
        }
    }
}

@Composable
internal fun DualScoreRing(
    classicScore: Int, classicGrade: Grade,
    personalScore: Int, personalGrade: Grade,
    veto: Boolean,
    scoreDelta: Int? = null,
) {
    val vetoDescription = stringResource(R.string.result_veto_description)
    val vetoShortLabel = stringResource(R.string.result_veto_short_label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = Spacing.S),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        val classicColor = gradeColor(classicGrade)
        val (classicAnimated, classicCompletion) = rememberScoreReveal(classicScore / 100f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.result_classic_score_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            Spacer(Modifier.height(Spacing.XS))
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .background(
                            Brush.radialGradient(listOf(classicColor.copy(alpha = 0.20f * classicCompletion), Color.Transparent)),
                            CircleShape,
                        ),
                )
                CircularProgressIndicator(
                    progress    = { classicAnimated },
                    modifier    = Modifier.fillMaxSize(),
                    color       = classicColor,
                    strokeWidth = 8.dp,
                    trackColor  = SurfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(classicGrade.label, style = HeroNumberStyle.copy(fontSize = 26.sp), color = classicColor)
                    Text(stringResource(R.string.result_score_out_of_100, classicScore), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = OnBackground.copy(0.6f))
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.Inline))
        val personalColor = if (veto) semanticRed() else gradeColor(personalGrade)
        val (personalAnimated, personalCompletion) = rememberScoreReveal(personalScore / 100f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.result_personal_score_label), style = MaterialTheme.typography.labelSmall,
                color = if (veto) semanticRed() else AccentCoral)
            Spacer(Modifier.height(Spacing.XS))
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .background(
                            Brush.radialGradient(listOf(personalColor.copy(alpha = 0.20f * personalCompletion), Color.Transparent)),
                            CircleShape,
                        ),
                )
                CircularProgressIndicator(
                    progress    = { personalAnimated },
                    modifier    = Modifier.fillMaxSize(),
                    color       = personalColor,
                    strokeWidth = 8.dp,
                    trackColor  = SurfaceVariant,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (veto) Modifier.clearAndSetSemantics { contentDescription = vetoDescription } else Modifier,
                ) {
                    Text(if (veto) "✗" else personalGrade.label,
                        style = HeroNumberStyle.copy(fontSize = 26.sp),
                        color = personalColor)
                    // A numeric "0/100" under the veto ✗ read as just a bad score rather than
                    // "unsafe regardless of score" - the whole point of the veto distinction. A
                    // short, sharp label keeps the alert legible without diluting it into a
                    // number that doesn't mean what it looks like it means here.
                    Text(
                        if (veto) vetoShortLabel else stringResource(R.string.result_score_out_of_100, personalScore),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (veto) semanticRed().copy(alpha = 0.8f) else OnBackground.copy(0.6f),
                    )
                }
            }
        }
    } // end Row
    if (scoreDelta != null) {
        Spacer(Modifier.height(Spacing.XS))
        ScoreDeltaChip(scoreDelta)
    }
    } // end Column
}
