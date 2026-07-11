package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
internal fun ScoreRing(score: Int, grade: Grade) {
    val color = gradeColor(grade)
    Box(modifier = Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.24f), Color.Transparent)),
                    CircleShape,
                ),
        )
        CircularProgressIndicator(
            progress    = { score / 100f },
            modifier    = Modifier.size(178.dp),
            color       = color,
            strokeWidth = 14.dp,
            trackColor  = SurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.label, fontSize = 56.sp, fontWeight = FontWeight.Black, color = color)
            Text(stringResource(R.string.result_score_out_of_100, score), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.6f))
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.result_classic_score_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress    = { classicScore / 100f },
                    modifier    = Modifier.fillMaxSize(),
                    color       = gradeColor(classicGrade),
                    strokeWidth = 8.dp,
                    trackColor  = SurfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(classicGrade.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = gradeColor(classicGrade))
                    Text("$classicScore", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                }
            }
        }
        Icon(Icons.Default.ArrowForward, null, tint = OnBackground.copy(0.3f), modifier = Modifier.size(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.result_personal_score_label), style = MaterialTheme.typography.labelSmall,
                color = if (veto) FlagRed else AccentGreen)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress    = { personalScore / 100f },
                    modifier    = Modifier.fillMaxSize(),
                    color       = if (veto) FlagRed else gradeColor(personalGrade),
                    strokeWidth = 8.dp,
                    trackColor  = SurfaceVariant,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (veto) Modifier.clearAndSetSemantics { contentDescription = vetoDescription } else Modifier,
                ) {
                    Text(if (veto) "✗" else personalGrade.label, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (veto) FlagRed else gradeColor(personalGrade))
                    Text("$personalScore", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                }
            }
        }
    }
}
