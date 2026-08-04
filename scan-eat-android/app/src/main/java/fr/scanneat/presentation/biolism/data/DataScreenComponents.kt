package fr.scanneat.presentation.biolism.data

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.HormoneReading
import fr.scanneat.presentation.biolism.hmsFromSeconds
import fr.scanneat.presentation.ui.theme.*
import java.util.Locale

// ── Shared card / helper composables for the Biolism Data screen ──────────────
// Used across cards/*.kt — internal (not private) so other files in this
// module can call them, but not part of any cross-module public API.

@Composable
internal fun BioCard(
    title: String,
    defaultOpen: Boolean = true,
    // MetabolicHealthScoreCard is the Data tab's stated aggregate/entry-point
    // card (per its own doc comment) but rendered at the exact same visual
    // weight as its 15 siblings - no card on this screen ever used HERO-tier
    // emphasis the way Dashboard's CalorieBalanceCard does for its one focal
    // card. Mirrors ScanEatCard's own HeroGlassSpec (edgeAlpha 0.34, a
    // visible Gold border) rather than introducing a second set of numbers.
    emphasized: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(defaultOpen) }
    // TalkBack previously heard only "button" for this row - the ExpandLess/ExpandMore
    // icon swap was purely visual (contentDescription = null) with no semantics on the
    // Row itself, so neither the toggle affordance nor the current open/closed state
    // was announced. mergeDescendants keeps the title/badge text audible while adding
    // the expanded/collapsed state and a Button role, mirroring HydrationScreen's
    // goal-editor row (see HydrationScreen.kt ~line 171).
    val openStateDescription = stringResource(if (open) R.string.common_expanded else R.string.common_collapsed)
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = if (emphasized) 0.34f else 0.16f, glowTint = if (emphasized) Gold else Color.White)) {
        Surface(
            shape = RoundedCornerShape(CardRadius.CARD),
            // design-aesthetic-audit: same fix as ScanEatCard.kt - SurfaceVariant sits
            // only ~1-3 RGB units from Background in Light theme, so this fill was
            // imperceptible there, leaving only the shadow visible as a disconnected
            // rectangle instead of a filled card.
            color = SurfaceVariant.copy(alpha = if (isLightBackground()) 0.85f else 0.42f),
            border = if (emphasized) BorderStroke(1.dp, Gold.copy(alpha = 0.22f)) else null,
            // same fix as ScanEatCard.kt: force the fill to hard-clip to its own shape
            // instead of relying on Surface's implicit clip, which doesn't reliably
            // match the shadow's rounded outline on every rendering path. Shadow also
            // now tinted (Modifier.shadow) instead of Surface's untinted shadowElevation.
            modifier = Modifier.fillMaxWidth()
                .shadow(elevation = if (emphasized) 10.dp else 6.dp, shape = RoundedCornerShape(CardRadius.CARD), ambientColor = ShadowTint, spotColor = ShadowTint)
                .clip(RoundedCornerShape(CardRadius.CARD)),
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(Spacing.M)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { open = !open }
                        .semantics(mergeDescendants = true) {
                            stateDescription = openStateDescription
                            role = Role.Button
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Box(Modifier.width(2.dp).height(16.dp).background(Gold, RoundedCornerShape(1.dp)))
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    badge?.invoke()
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.Inline))
                }
                // Gated on rememberReducedMotion(), like every other prominent
                // animation in the app (see Motion.kt's own doc comment) -
                // previously left at Compose's default expand/fade regardless
                // of the system setting.
                val reduceMotion = rememberReducedMotion()
                AnimatedVisibility(
                    visible = open,
                    enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandVertically(),
                    exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkVertically(),
                ) {
                    Column(Modifier.padding(top = Spacing.M), content = content)
                }
            }
        }
    }
}

// ── 2-column grid utility (see MetCellGrid below)
@Composable
internal fun MetCellGrid(items: List<Triple<String, String, String>>, accents: List<Color> = emptyList()) {
    items.chunked(2).forEachIndexed { row, pair ->
        // bottom padding now matches this Row's own horizontal spacedBy(Spacing.S) -
        // previously 6.dp against an 8.dp column gap, so a 2-column grid of cells
        // had a visibly tighter gap between rows than between columns.
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.S), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            pair.forEachIndexed { col, (label, value, sub) ->
                val accent = accents.getOrNull(row * 2 + col) ?: OnBackground
                MetCell(label, value, sub, accent, Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MetCell(label: String, value: String, sub: String, accent: Color = OnBackground, modifier: Modifier = Modifier.fillMaxWidth()) {
    Surface(shape = RoundedCornerShape(8.dp), color = OnBackground.copy(0.04f), modifier = modifier) {
        // Spacing.S (8dp), not the literal 9.dp this had - one dp off the actual
        // scale for no reason, next to every other Biolism cell/row padding here.
        Column(Modifier.padding(Spacing.S)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.SemiBold)
            // Bumped from 0.3f - a UI/UX audit flagged this as real informational
            // content (not decorative) rendered too faint against the dark surface.
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String, note: String, color: Color = OnBackground) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            // Bumped from 0.3f - a UI/UX audit flagged this as real informational
            // content (not decorative) rendered too faint against the dark surface.
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Label(text: String, color: Color = OnBackground.copy(0.4f)) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.padding(bottom = Spacing.S))
}

@Composable
internal fun TintedPanel(color: Color, content: @Composable ColumnScope.() -> Unit) {
    // Alpha steps were ad hoc (0.06f/0.15f) instead of the phi-derived Haze
    // (0.10) / Border (0.162) alpha family every named Gold/Teal/Violet
    // token elsewhere in this module uses - this is the shared container
    // ~9 cards route through, so it was silently reintroducing an
    // independent alpha scale across most of the Data tab regardless of
    // which accent color a card passed in.
    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = color.copy(GLOW_HAZE_ALPHA),
        border = BorderStroke(1.dp, color.copy(GLOW_BORDER_ALPHA)), modifier = Modifier.fillMaxWidth()) {
        // Inner spacing now matches this Column's own Spacing.S padding, instead of
        // a literal 6.dp that didn't agree with the container it sits inside.
        Column(Modifier.padding(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S), content = content)
    }
}

@Composable
internal fun HormoneRow(name: String, h: HormoneReading, note: String) {
    val color = colorFromToken(h.colorToken)
    val barPct = (h.value / (h.refHigh * 1.3)).coerceIn(0.0, 1.0).toFloat()
    // Spacing.XS, matching InfoRow's own vertical padding - this and InfoRow render
    // the same "label/value row in a list" shape in the same cards (e.g.
    // HormonesCard), and previously used a different literal padding (5.dp vs
    // InfoRow's Spacing.XS/4dp) for no reason, giving the two row types a visibly
    // different rhythm next to each other.
    Column(Modifier.padding(vertical = Spacing.XS)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(name, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), fontWeight = FontWeight.Medium)
                // Bumped from 0.3f - a UI/UX audit flagged this as real informational
                // content (not decorative) rendered too faint against the dark surface.
                Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // A formula-derived estimate (see HormoneEstimator), not a lab
                // measurement - one-decimal precision here (unlike the whole-number
                // reference range just below) implied a level of accuracy this
                // doesn't have. Whole numbers, matching the ref range's own format.
                Text("%.0f ${h.unit}".format(Locale.US, h.value), style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(3.dp), color = color.copy(0.15f),
                    border = BorderStroke(1.dp, color.copy(0.3f))) {
                    Text(h.label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        // design-aesthetic-audit §DC3: the reference band below was a hardcoded
        // Color.White at low alpha - readable on the dark/OLED themes it was
        // eyeballed against, but nearly invisible on the Light theme's near-white
        // background (0xFFF6F1EC). OnBackground already flips between near-white
        // and near-black per theme; captured here since DrawScope (inside Canvas
        // below) isn't itself a @Composable context and can't read it directly.
        val referenceBandColor = OnBackground
        // Fix 12: Canvas draws track, normal-range band, and value bar correctly
        Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            val w   = size.width
            val h3  = size.height
            val top = 1.5f    // half-height — used for RoundedCornerShape approximation via cornerRadius
            val scale = h.refHigh * 1.3
            val loFrac = (h.refLow  / scale).toFloat().coerceIn(0f, 1f)
            val hiFrac = (h.refHigh / scale).toFloat().coerceIn(0f, 1f)
            val valFrac = barPct.coerceIn(0f, 1f)
            // Track
            drawRoundRect(color.copy(alpha = 0.06f), cornerRadius = CornerRadius(top))
            // Normal reference band
            drawRect(
                color = referenceBandColor.copy(alpha = 0.12f),
                topLeft = Offset(w * loFrac, 0f),
                size    = Size(w * (hiFrac - loFrac), h3),
            )
            // Value bar
            if (valFrac > 0f) {
                drawRoundRect(
                    color       = color.copy(alpha = 0.75f),
                    size        = Size(w * valFrac, h3),
                    cornerRadius = CornerRadius(top),
                )
            }
        }
        // Bumped from 0.25f - a UI/UX audit flagged a clinically meaningful reference
        // range rendered at the lowest alpha found anywhere in the app.
        Text(stringResource(R.string.biolism_common_ref_range, h.refLow, h.refHigh, h.unit), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
    }
}

@Composable
internal fun MacroTargetRow(label: String, grams: Double, unit: String, note: String, color: Color, kcal: Double? = null) {
    // Spacing.XS - same reasoning as HormoneRow above: MacroTargetsCard renders
    // this next to InfoRow (Spacing.XS), and the two previously had a visibly
    // different vertical rhythm (6.dp vs 4.dp) despite being the same "label/
    // value row" shape.
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.1f $unit".format(Locale.US, grams), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
            if (kcal != null) {
                Text("%.0f kcal".format(Locale.US, kcal), style = MaterialTheme.typography.labelSmall, color = color.copy(0.6f))
            }
        }
    }
    HorizontalDivider(color = OnBackground.copy(0.06f))
}

@Composable
internal fun TealBadge(text: String) = Badge(text, Teal)
@Composable
internal fun GoldBadge(text: String) = Badge(text, Gold)
@Composable
internal fun VioletBadge(text: String) = Badge(text, Violet)

@Composable
internal fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.12f), border = BorderStroke(1.dp, color.copy(0.25f))) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// Danger/Severe genuinely mean "bad" (organ strain, prolonged-ketosis risk, etc.) —
// routed through the colorblind-safe semantic palette instead of the fixed red-family
// literals, which previously stayed identical across every colorblind mode despite
// being exactly the kind of red/orange hue those modes exist to disambiguate.
@Composable
internal fun colorFromToken(token: String): Color = when (token) {
    "Gold"        -> Gold
    "Teal"        -> Teal
    "Violet"      -> Violet
    "Warm"        -> Warm
    "Danger"      -> semanticRed()
    "Severe"      -> semanticRed()
    "IconInactive"-> IconInactive
    else          -> TextSecondary
}

// OrganPct.name (computeOrganPcts) is a stable English key also used to look
// up eliaBase deltas in OrganHeatCard - it can't be localized at the source
// without breaking that lookup, so the raw literal was rendered directly with
// no stringResource() at all, unlike every other label in this card. Same
// "token -> localized label" indirection as colorFromToken above.
@Composable
internal fun organLabel(name: String): String = when (name) {
    "Liver"           -> stringResource(R.string.biolism_organ_liver)
    "Skeletal Muscle" -> stringResource(R.string.biolism_organ_muscle)
    "Brain"           -> stringResource(R.string.biolism_organ_brain)
    "Residual"        -> stringResource(R.string.biolism_organ_residual)
    "Kidneys"         -> stringResource(R.string.biolism_organ_kidneys)
    "Heart"           -> stringResource(R.string.biolism_organ_heart)
    else              -> name
}

internal fun formatDuration(ms: Long): String {
    val (h, m, sec) = hmsFromSeconds(ms / 1000)
    return if (h > 0) "%dh %02dm".format(Locale.US, h, m) else "%dm %02ds".format(Locale.US, m, sec)
}

internal fun isToday(iso: String): Boolean {
    return try { iso.startsWith(java.time.LocalDate.now().toString()) } catch (e: Exception) { false }
}
