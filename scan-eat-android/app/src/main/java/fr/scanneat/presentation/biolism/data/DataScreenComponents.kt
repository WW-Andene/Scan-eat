package fr.scanneat.presentation.biolism.data

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(defaultOpen) }
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f)) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { open = !open },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Box(Modifier.width(2.dp).height(16.dp).background(Gold, RoundedCornerShape(1.dp)))
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    badge?.invoke()
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OnBackground.copy(0.4f), modifier = Modifier.size(20.dp))
                }
                AnimatedVisibility(open) {
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
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
        Column(Modifier.padding(9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String, note: String, color: Color = OnBackground) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Label(text: String, color: Color = OnBackground.copy(0.4f)) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun TintedPanel(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(0.06f),
        border = BorderStroke(1.dp, color.copy(0.15f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
internal fun HormoneRow(name: String, h: HormoneReading, note: String) {
    val color = colorFromToken(h.colorToken)
    val barPct = (h.value / (h.refHigh * 1.3)).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(name, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), fontWeight = FontWeight.Medium)
                Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%.1f ${h.unit}".format(Locale.US, h.value), style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(3.dp), color = color.copy(0.15f),
                    border = BorderStroke(1.dp, color.copy(0.3f))) {
                    Text(h.label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
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
                color = Color.White.copy(alpha = 0.12f),
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
        Text(stringResource(R.string.biolism_common_ref_range, h.refLow, h.refHigh, h.unit), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.25f))
    }
}

@Composable
internal fun MacroTargetRow(label: String, grams: Double, unit: String, note: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.1f $unit".format(Locale.US, grams), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
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

internal fun formatDuration(ms: Long): String {
    val (h, m, sec) = hmsFromSeconds(ms / 1000)
    return if (h > 0) "%dh %02dm".format(Locale.US, h, m) else "%dm %02ds".format(Locale.US, m, sec)
}

internal fun isToday(iso: String): Boolean {
    return try { iso.startsWith(java.time.LocalDate.now().toString()) } catch (e: Exception) { false }
}
