package fr.scanneat.presentation.result.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
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
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 700, easing = ScoreRevealEasing),
        label         = "scoreRingProgress",
    )
    val completion = if (target > 0f) (animatedProgress / target).coerceIn(0f, 1f) else 1f
    return animatedProgress to completion
}

@Composable
internal fun ScoreRing(score: Int, grade: Grade) {
    val color = gradeColor(grade)
    val (animatedProgress, completion) = rememberScoreReveal(score / 100f)
    Box(modifier = Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.24f * completion), Color.Transparent)),
                    CircleShape,
                ),
        )
        CircularProgressIndicator(
            progress    = { animatedProgress },
            modifier    = Modifier.size(178.dp),
            color       = color,
            strokeWidth = 14.dp,
            trackColor  = SurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.label, style = HeroNumberStyle.copy(fontSize = 56.sp), color = color)
            Text(stringResource(R.string.result_score_out_of_100, score),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = OnBackground.copy(0.6f))
        }
    }
}

@Composable
internal fun DualScoreRing(
    classicScore: Int, classicGrade: Grade,
    personalScore: Int, personalGrade: Grade,
    veto: Boolean,
) {
    val vetoDescription = stringResource(R.string.result_veto_description)
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        val classicColor = gradeColor(classicGrade)
        val (classicAnimated, classicCompletion) = rememberScoreReveal(classicScore / 100f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.result_classic_score_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            Spacer(Modifier.height(4.dp))
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
                    Text(classicGrade.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = classicColor)
                    Text("$classicScore", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = OnBackground.copy(0.6f))
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = OnBackground.copy(0.3f), modifier = Modifier.size(20.dp))
        val personalColor = if (veto) FlagRed else gradeColor(personalGrade)
        val (personalAnimated, personalCompletion) = rememberScoreReveal(personalScore / 100f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.result_personal_score_label), style = MaterialTheme.typography.labelSmall,
                color = if (veto) FlagRed else AccentCoral)
            Spacer(Modifier.height(4.dp))
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
                    Text(if (veto) "✗" else personalGrade.label, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = personalColor)
                    Text("$personalScore", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = OnBackground.copy(0.6f))
                }
            }
        }
    }
}
