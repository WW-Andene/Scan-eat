package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.CalorieBalance
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun CalorieBalanceCard(balance: CalorieBalance, streak: Int, longestStreak: Int = 0) {
    val isSurplus = balance.net > 200
    val isDeficit = balance.net < -50
    // User-reported (§E8 audit, emotional safety): a calorie surplus is often
    // a perfectly normal day, not a safety flag - alarm-red read as
    // judgmental. Amber (the same tone already used for the "balanced"
    // state) keeps this informational instead of alarming.
    val balColor = if (isSurplus) semanticAmber() else if (isDeficit) AccentCoral else semanticAmber()
    val statusRes = if (isSurplus) R.string.dashboard_calorie_surplus
        else if (isDeficit) R.string.dashboard_calorie_deficit
        else R.string.dashboard_calorie_balanced
    val sourceRes = if (balance.tdeeFromBiolism) R.string.dashboard_calorie_source_biolism else R.string.dashboard_calorie_source_profile

    // Dashboard's single HERO-tier card (see CardEmphasis's own doc comment) —
    // stronger glow/edge than a plain glassSheen(), echoed in the balance's
    // own color rather than a fixed hue, plus a one-time reveal on the number
    // itself (started-flip idiom, same as ScoreDisplay's score ring).
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val entrance = rememberHeroEntrance(visible = started)

    // Outer wrapper carries NO clip of its own — the inner Box below owns the
    // one real clip, so the streak badge (a sibling of that inner Box, not a
    // child inside it) can poke above the card's top edge via its negative Y
    // offset instead of being clipped off at the card boundary it used to sit
    // inside of.
    Box {
    // User-reported: this used to be an outer Box(glassSheen's own clip) wrapping
    // an inner Surface (its own separate shadow/clip) - two independently-clipped
    // objects, the exact construction already fixed on ScanEatCard/FloatingTopBar/
    // MainShell's nav/DiaryHeader (see their own doc comments). Collapsed into one
    // Box carrying shadow, clip, and the glassSheen hairline in a single chain.
    Box(
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(CardRadius.PROMINENT))
            .clip(RoundedCornerShape(CardRadius.PROMINENT))
            .glassSheen(
                edgeAlpha = HeroGlassSpec.edgeAlpha,
                shape     = RoundedCornerShape(CardRadius.PROMINENT),
                glowTint  = balColor,
                glowAlpha = HeroGlassSpec.glowAlpha,
            ),
    ) {
        // Same directional shadow treatment as ScanEatCard (see its own doc
        // comment) - offset toward the bottom-left, drawn behind the rest of
        // this Box's content rather than Modifier.shadow's symmetric elevation
        // shadow above.
        Box(
            Modifier
                .matchParentSize()
                .offset(x = -8.dp, y = 8.dp)
                .blur(12.dp)
                .background(ShadowTint.copy(alpha = 0.4f), RoundedCornerShape(CardRadius.PROMINENT)),
        )
        // This is the Dashboard's one focal metric — the Part B6 atmosphere
        // fix: a soft radial light-pool in the balance color, at Haze-level
        // intensity (~10% alpha), rendered on top of the flat surface fill
        // rather than left flat. Reserved for this card alone, not every card.
        run {
            // Wrapping Box (not fillMaxSize/matchParentSize on its own) so it
            // sizes to its content like Surface previously did directly, while
            // giving the nested blurred-fill Box below a BoxScope to resolve
            // matchParentSize() against.
            Box {
                // User-reported: a visibly separate, lighter rounded rectangle
                // floating inside this card - same root cause as ScanEatCard.kt's
                // own fix (see its doc comment): blur(3.dp) below had nothing
                // behind it to actually blur, so it faded the opaque fill inward
                // from its own clipped edge, shrinking it to a smaller box sitting
                // inside the card's real boundary. Dropped, same fix.
                Box(
                    // User-requested: one standard glass config app-wide - see
                    // StandardCardAlpha's own doc comment (ScanEatCard.kt).
                    Modifier.matchParentSize().clip(RoundedCornerShape(CardRadius.PROMINENT))
                        .background(SurfaceVariant.copy(alpha = StandardCardAlpha)),
                )
                Column(
                    modifier = Modifier
                        // Explicit center/radius, matching every other gradient in the
                        // theme (glassSheen's own glow, ambientGloom) - left implicit
                        // here (plain Brush.radialGradient(colors) with no center/
                        // radius), the two-stop gradient's falloff resolves from
                        // whatever bounds Compose measures this Column at, and on a
                        // near-black OLED surface an already-low-alpha (14%) two-stop
                        // fade banded into a single off-position bright spot instead
                        // of a smooth wash. drawWithCache below pins the center to the
                        // card's true middle and adds a third color stop to soften
                        // the falloff curve, both of which cut the banding.
                        .drawWithCache {
                            val brush = Brush.radialGradient(
                                colors = listOf(balColor.copy(alpha = 0.14f), balColor.copy(alpha = 0.05f), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                                radius = size.maxDimension * 0.6f,
                            )
                            onDrawBehind { drawRect(brush) }
                        }
                        .padding(Spacing.XL),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SM),
                ) {
                    Row(
                    modifier = Modifier.fillMaxWidth().padding(end = if (streak > 0) Spacing.XXL + Spacing.SM else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.dashboard_calorie_balance_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(sourceRes), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                }

                Text(
                    (if (balance.net >= 0) "+" else "") + "${balance.net.roundToInt()} kcal",
                    style = HeroNumberStyle.copy(fontSize = 32.sp), color = balColor,
                    modifier = Modifier.heroEntrance(entrance),
                )
                Text(stringResource(statusRes), style = MaterialTheme.typography.labelSmall, color = balColor, fontWeight = FontWeight.SemiBold)

                // Budget for both the bar and the "in/out" text below must match what
                // balance.net (driving isSurplus/isDeficit/balColor above) actually used -
                // net already includes extraExerciseKcal, so a plain balance.tdee
                // denominator here could show the bar over 100% while the headline
                // status/color says "deficit", the exact inconsistency this avoids.
                val effectiveTdee = balance.tdee + balance.extraExerciseKcal
                val pct = (balance.kcalIn / effectiveTdee).toFloat().coerceIn(0f, 1.2f)
                LinearProgressIndicator(
                    progress   = { pct.coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = if (isSurplus) semanticAmber() else AccentCoral,
                    trackColor = SurfaceVariant.copy(alpha = 0.3f),
                )
                Text(
                    stringResource(R.string.dashboard_calorie_in_out, balance.kcalIn.roundToInt(), effectiveTdee.roundToInt()),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
                )
                // longestLogStreak() (the all-time record) was computed but never shown
                // anywhere - only shown once it's actually a real record to beat, i.e.
                // strictly longer than today's current streak.
                if (longestStreak > streak) {
                    Text(
                        pluralStringResource(R.plurals.dashboard_streak_record, longestStreak, longestStreak),
                        style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f),
                    )
                }
                // Activité previously had zero visible connection to this card - a
                // logged workout changed nothing here despite ActivityRepository
                // already tracking its estimated kcal burn.
                if (balance.exerciseKcal > 0) {
                    Text(
                        stringResource(R.string.dashboard_calorie_exercise, balance.exerciseKcal),
                        style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f),
                    )
                }
                // User-requested: is logged activity actually connected to the calorie
                // budget, or just shown as an info line? Now the former (see
                // extraExerciseKcal's own doc comment) - surfaced explicitly so the
                // budget increase this drives isn't a silent, unexplained number change.
                if (balance.extraExerciseKcal > 0) {
                    Text(
                        stringResource(R.string.dashboard_calorie_extra_budget, balance.extraExerciseKcal),
                        style = MaterialTheme.typography.labelSmall, color = semanticAmber(),
                    )
                }
                }
            }
        }
    }

    if (streak > 0) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-10).dp)
                .size(48.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(50)),
            shape = RoundedCornerShape(50),
            color = AccentCoral,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$streak", style = HeroNumberStyle.copy(fontSize = 14.sp), color = Color.Black)
                    Text(
                        pluralStringResource(R.plurals.dashboard_streak_unit, streak),
                        style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(0.7f), fontSize = 9.sp, lineHeight = 10.sp,
                    )
                }
            }
        }
    }
    }
}
