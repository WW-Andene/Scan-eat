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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val balColor = if (isSurplus) semanticRed() else if (isDeficit) AccentCoral else semanticAmber()
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

    // Outer wrapper carries NO clip of its own — glassSheen()'s clip(shape) is
    // scoped to the inner Box below, so the streak badge (a sibling of that
    // inner Box, not a child inside it) can poke above the card's top edge via
    // its negative Y offset instead of being clipped off at the card boundary
    // it used to sit inside of.
    Box {
    Box(
        modifier = Modifier.fillMaxWidth().glassSheen(
            edgeAlpha = HeroGlassSpec.edgeAlpha,
            shape     = RoundedCornerShape(CardRadius.PROMINENT),
            glowTint  = balColor,
            glowAlpha = HeroGlassSpec.glowAlpha,
        ),
    ) {
        // Same directional shadow treatment as ScanEatCard (see its own doc
        // comment) - offset toward the bottom-left, drawn behind the Surface
        // rather than Modifier.shadow's symmetric elevation shadow below.
        Box(
            Modifier
                .matchParentSize()
                .offset(x = -7.dp, y = 9.dp)
                .blur(10.dp)
                .background(ShadowTint.copy(alpha = 0.4f), RoundedCornerShape(CardRadius.PROMINENT)),
        )
        // This is the Dashboard's one focal metric — the Part B6 atmosphere
        // fix: a soft radial light-pool in the balance color, at Haze-level
        // intensity (~10% alpha), rendered on top of the flat surface fill
        // rather than left flat. Reserved for this card alone, not every card.
        Surface(
            // User-reported "rectangle" bug: this Surface had no explicit .clip() and
            // an untinted shadowElevation — the same MIUI/Xiaomi flat-square-corner
            // bug ScanEatCard.kt's own comment documents and fixes. Matched here:
            // tinted Modifier.shadow + forced .clip() + shadowElevation = 0.dp.
            modifier = Modifier.fillMaxWidth()
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(CardRadius.PROMINENT), ambientColor = ShadowTint, spotColor = ShadowTint)
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                // Matches ScanEatCard's own border treatment: radial, anchored
                // top-right, fading away by the bottom-left instead of an evenly
                // lit uniform outline (see its own doc comment).
                .drawWithContent {
                    drawContent()
                    drawOutline(
                        outline = RoundedCornerShape(CardRadius.PROMINENT).createOutline(size, layoutDirection, this),
                        brush = Brush.radialGradient(
                            colors = listOf(balColor.copy(alpha = HeroGlassSpec.borderAlpha), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 1.15f,
                        ),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
            shape = RoundedCornerShape(CardRadius.PROMINENT),
            color = Color.Transparent,
            // design-aesthetic-audit §DH: this card already declares itself
            // HERO tier via HeroGlassSpec's edge/glow/border above (it's the
            // Dashboard's one focal metric per the doc comment below). 10dp matches
            // HeroGlassSpec's own elevation tier from ScanEatCard, so this card
            // actually reads as more prominent than an ordinary card, not equal to
            // or flatter than one — now drawn via the tinted Modifier.shadow above.
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    // .copy(alpha = ...), not the bare opaque color - every other
                    // card in the app goes through ScanEatCard's translucent
                    // default fill so the screen's own ambientGloom wash bleeds
                    // through it; this card hand-rolls its own Surface/Column
                    // instead of using ScanEatCard (so it can overlay the streak
                    // badge via BoxScope.align, a slot ScanEatCard's content
                    // lambda doesn't expose) and had fully opaque SurfaceVariant
                    // here as a result - the one card on Dashboard that never let
                    // any background show through it at all. Kept in sync with
                    // ScanEatCard's own default alpha (see its doc comment on the
                    // 0.24 -> 0.4 correction) rather than a separate literal here.
                    .background(SurfaceVariant.copy(alpha = 0.4f))
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
                    modifier = Modifier.fillMaxWidth().padding(end = if (streak > 0) 40.dp else 0.dp),
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

                val pct = (balance.kcalIn / balance.tdee).toFloat().coerceIn(0f, 1.2f)
                LinearProgressIndicator(
                    progress   = { pct.coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = if (isSurplus) semanticRed() else AccentCoral,
                    trackColor = SurfaceVariant.copy(alpha = 0.3f),
                )
                Text(
                    stringResource(R.string.dashboard_calorie_in_out, balance.kcalIn.roundToInt(), balance.tdee.roundToInt()),
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
            }
        }
    }

    if (streak > 0) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-10).dp)
                .size(46.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(50), ambientColor = ShadowTint, spotColor = ShadowTint),
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
