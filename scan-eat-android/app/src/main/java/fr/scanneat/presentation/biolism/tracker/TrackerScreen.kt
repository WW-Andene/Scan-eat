package fr.scanneat.presentation.biolism.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TrackerScreen(viewModel: TrackerViewModel = hiltViewModel()) {
    val profile   = viewModel.profile.collectAsStateWithLifecycle()
    val timer     = viewModel.timerState.collectAsStateWithLifecycle()
    val elapsedMs = viewModel.elapsedMs.collectAsStateWithLifecycle()
    val ketoMs    = viewModel.ketoElapsedMs.collectAsStateWithLifecycle()
    val precision = viewModel.heroPrecision.collectAsStateWithLifecycle()
    val saved     = viewModel.saved.collectAsStateWithLifecycle()

    val s    = timer.value
    val p    = profile.value
    val live = viewModel.liveMetabolic.collectAsStateWithLifecycle()
    val lm   = live.value

    val elapsedSec   = elapsedMs.value / 1000.0
    val ketoHours    = ketoMs.value / 3_600_000.0
    val fastingHours = s.fastingHours

    // All metabolic values come from the ViewModel StateFlow (Fix 8)
    val kcalTotal    = lm.kcalTotal
    val fatPct       = (lm.fatFrac  * 100).roundToInt()
    val protPct      = (lm.protFrac * 100).roundToInt()
    val carbPct      = 100 - fatPct - protPct
    val fatLostKg    = lm.fatLostKg
    val glycoLostKg  = lm.glycoLostKg
    val liveWeight   = lm.liveWeightKg
    val phase        = lm.phase
    val phaseColor   = when (phase?.colorToken) {
        "Gold"   -> Gold;    "Teal"  -> Teal;    "Violet" -> Violet
        "Warm"   -> Warm;    "Severe"-> Severe;  else     -> Teal
    }

    val bgColor = MaterialTheme.colorScheme.background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        if (!p.isValid) {
            EmptyProfilePrompt()
        } else {
            // ── Ketosis toggle ─────────────────────────────────────────────────
            KetosisToggleRow(
                active        = s.ketosisOn,
                ketoAdapted   = s.ketoAdapted,
                fatPct        = fatPct,
                npRq          = lm.npRq,
                ketoHours     = ketoHours,
                onToggle      = viewModel::toggleKetosis,
                onAddHours    = viewModel::addKetoHours,
            )

            if (s.ketosisOn) {
                AdaptedToggleRow(active = s.ketoAdapted, ketoHours = ketoHours, onToggle = viewModel::toggleKetoAdapted)
            }

            // ── Fasting row ────────────────────────────────────────────────────
            FastingRow(
                active        = s.fastingActive,
                fastingHours  = fastingHours,
                onToggle      = viewModel::toggleFastingActive,
                onLogMeal     = viewModel::logMealNow,
                onAddHours    = viewModel::addFastingHours,
            )

            // ── Phase strip ────────────────────────────────────────────────────
            if (s.ketosisOn && phase != null) {
                PhaseStrip(phase = phase, ketoHours = ketoHours, color = phaseColor)
            }

            // ── Hero kcal display ──────────────────────────────────────────────
            HeroCard(
                kcalTotal    = kcalTotal,
                precision    = precision.value,
                running      = s.running,
                ketosisOn    = s.ketosisOn,
                elapsedSec   = elapsedSec,
                fatPct       = fatPct,
                carbPct      = carbPct,
                protPct      = protPct,
                fatFrac      = lm.fatFrac,
                carbFrac     = lm.carbFrac,
                protFrac     = lm.protFrac,
                npRq         = lm.npRq,
                onPrecision  = viewModel::togglePrecision,
            )

            // ── Live weight ────────────────────────────────────────────────────
            if (elapsedSec > 0 && p.weightKg > 0) {
                LiveWeightCard(
                    liveWeight   = liveWeight,
                    baseWeight   = p.weightKg,
                    fatLostKg    = fatLostKg,
                    glycoLostKg  = glycoLostKg,
                    ketosisOn    = s.ketosisOn,
                )
            }

            // ── Session controls ───────────────────────────────────────────────
            SessionControls(
                running  = s.running,
                elapsed  = elapsedSec,
                saved    = saved.value,
                onStartPause = viewModel::startOrPause,
                onSave   = viewModel::saveSession,
                onReset  = viewModel::reset,
            )
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun EmptyProfilePrompt() {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⚗️", fontSize = 48.sp)
            Text("Complète ton profil Biolism", style = MaterialTheme.typography.titleSmall,
                color = OnBackground, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Âge, sexe, taille et poids sont nécessaires pour calculer ton BMR et les flux de substrats.",
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun KetosisToggleRow(
    active: Boolean, ketoAdapted: Boolean, fatPct: Int, npRq: Double,
    ketoHours: Double, onToggle: () -> Unit, onAddHours: (Double) -> Unit,
) {
    val borderColor = if (active) TealBorder else TealTrace
    val bgColor     = if (active) TealHaze   else TealTrace

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = active, onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = Teal, uncheckedColor = Teal.copy(0.4f)))
                    Column {
                        Text("Cétose", style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Bold)
                        Text("< 50g glucides · graisse = carburant principal · RQ ≈ 0.70",
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    }
                }
                Surface(shape = RoundedCornerShape(4.dp), color = TealHaze,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TealGlow)) {
                    Text(if (active) "oxi. ${fatPct}% graisses · npRQ ${String.format("%.3f", npRq)}"
                         else "npRQ ${String.format("%.3f", npRq)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                }
            }
            if (active) {
                // +/- stepper row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Temps cétose :", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    listOf("6h" to 6.0, "12h" to 12.0, "24h" to 24.0, "1s" to 168.0, "1m" to 720.0).forEach { (label, h) ->
                        StepperChip(label = label, color = Teal, onMinus = { onAddHours(-h) }, onPlus = { onAddHours(h) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptedToggleRow(active: Boolean, ketoHours: Double, onToggle: () -> Unit) {
    val threeWeeks = ketoHours >= 504.0
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = if (active) GoldHaze else GoldTrace,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) GoldBorder else GoldTrace),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(checked = active, onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Gold.copy(0.4f)))
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Kéto-adapté", style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Bold)
                        if (threeWeeks) {
                            Surface(shape = RoundedCornerShape(4.dp), color = GoldHaze, border = androidx.compose.foundation.BorderStroke(1.dp, GoldGlow)) {
                                Text("AUTO ✓", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("3+ semaines requis", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                        }
                    }
                    Text("Cerveau ~70% cétones · RQ plancher 0.715", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                }
            }
            Surface(shape = RoundedCornerShape(4.dp), color = GoldHaze, border = androidx.compose.foundation.BorderStroke(1.dp, GoldGlow)) {
                Text(if (active) "RQ→0.715" else "RQ→0.720", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FastingRow(
    active: Boolean, fastingHours: Double, onToggle: () -> Unit, onLogMeal: () -> Unit, onAddHours: (Double) -> Unit,
) {
    val fastFmt = formatFastingTime(fastingHours)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (active && fastingHours > 0) VioletHaze else VioletTrace,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active && fastingHours > 0) VioletBorder else VioletTrace),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = active, onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = Violet, uncheckedColor = Violet.copy(0.4f)))
                    Column {
                        Text("Jeûne", style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Bold)
                        Text(if (active && fastFmt != null) "$fastFmt · état métabolique ancré"
                             else if (active) "Logger un repas ou ajuster le temps ci-dessous"
                             else "Désactivé — activer pour suivre le jeûne",
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    }
                }
                if (active) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (fastFmt != null) {
                            Surface(shape = RoundedCornerShape(4.dp), color = VioletHaze, border = androidx.compose.foundation.BorderStroke(1.dp, VioletGlow)) {
                                Text(fastFmt, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Violet, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onLogMeal() },
                            shape = RoundedCornerShape(4.dp),
                            color = VioletHaze,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Violet.copy(0.4f)),
                        ) {
                            Text("J'ai mangé", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = Violet, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Temps de jeûne :", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    listOf("6h" to 6.0, "12h" to 12.0, "24h" to 24.0, "1s" to 168.0, "1m" to 720.0).forEach { (label, h) ->
                        StepperChip(label = label, color = Violet, onMinus = { onAddHours(-h) }, onPlus = { onAddHours(h) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperChip(label: String, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(0.08f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("−", modifier = Modifier.clickable { onMinus() }.padding(horizontal = 6.dp, vertical = 3.dp),
            color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(label, modifier = Modifier.padding(horizontal = 4.dp),
            color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        Text("+", modifier = Modifier.clickable { onPlus() }.padding(horizontal = 6.dp, vertical = 3.dp),
            color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun PhaseStrip(phase: KetoPhaseInfo, ketoHours: Double, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(0.08f)),
    ) {
        // Progress underline
        Box(
            modifier = Modifier
                .fillMaxWidth(phase.progressPct.toFloat() / 100f)
                .height(2.dp)
                .align(Alignment.BottomStart)
                .background(color.copy(0.7f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                Text(phase.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                Text(phase.description, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(String.format("%.1f h", ketoHours), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                Text("${phase.progressPct.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
            }
        }
    }
}

@Composable
private fun HeroCard(
    kcalTotal: Double, precision: Boolean, running: Boolean, ketosisOn: Boolean,
    elapsedSec: Double, fatPct: Int, carbPct: Int, protPct: Int,
    fatFrac: Double, carbFrac: Double, protFrac: Double, npRq: Double,
    onPrecision: () -> Unit,
) {
    val heroColor = if (ketosisOn) Teal else Gold
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ketosisOn) Box(Modifier.size(6.dp).clip(CircleShape).background(Teal))
                if (running)   Box(Modifier.size(6.dp).clip(CircleShape).background(Gold))
                Surface(shape = RoundedCornerShape(4.dp), color = if (running) GoldHaze else VioletHaze,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (running) GoldGlow else VioletGlow)) {
                    Text(if (running) "EN COURS" else "PAUSE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = if (running) Gold else Violet, fontWeight = FontWeight.Bold)
                }
            }

            Text(if (ketosisOn) "Calories brûlées (cétose)" else "Calories brûlées",
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
                letterSpacing = 1.sp, fontWeight = FontWeight.Bold)

            Text(
                if (precision) String.format("%.4f", kcalTotal) else String.format("%.1f", kcalTotal),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W500, fontSize = 42.sp),
                color = heroColor,
            )
            Text("kcal", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            if (ketosisOn) {
                Text("oxydation : ${fatPct}% graisses · ${carbPct}% glucides · ${protPct}% protéines · npRQ ${String.format("%.3f", npRq)}",
                    style = MaterialTheme.typography.labelSmall, color = Teal.copy(0.8f))
            }

            TextButton(onClick = onPrecision) {
                Text(if (precision) "← 1 chiffre" else "4 chiffres →",
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
            }

            // Elapsed
            Text(formatElapsed(elapsedSec), style = MaterialTheme.typography.labelMedium,
                color = OnBackground.copy(0.4f), fontWeight = FontWeight.Medium)

            // Substrate bar
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                    Box(Modifier.weight(fatFrac.coerceAtLeast(0.01).toFloat()).fillMaxHeight().background(if (ketosisOn) Teal else Warm))
                    Box(Modifier.weight(carbFrac.coerceAtLeast(0.01).toFloat()).fillMaxHeight().background(Gold.copy(0.6f)))
                    Box(Modifier.weight(protFrac.coerceAtLeast(0.01).toFloat()).fillMaxHeight().background(Violet.copy(0.7f)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubstrateLegendItem(if (ketosisOn) Teal else Warm, "Graisses $fatPct%")
                    SubstrateLegendItem(Gold.copy(0.9f), "Glucides $carbPct%")
                    SubstrateLegendItem(Violet, "Protéines $protPct%")
                }
            }
        }
    }
}

@Composable
private fun SubstrateLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
    }
}

@Composable
private fun LiveWeightCard(liveWeight: Double, baseWeight: Double, fatLostKg: Double, glycoLostKg: Double, ketosisOn: Boolean) {
    val color = if (ketosisOn) Teal else Gold
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(0.04f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Poids en direct", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(if (ketosisOn) "9 300 kcal/kg (triglycérides)" else "Wishnofsky · 7 700 kcal/kg",
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(String.format("%.4f", liveWeight), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W500), color = color)
                Text("kg", style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.5f))
                val deltaG = (liveWeight - baseWeight) * 1000.0
                Text("Δ ${String.format("%.4f", deltaG)} g", style = MaterialTheme.typography.labelSmall, color = color.copy(0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Base: ${String.format("%.3f", baseWeight)} kg", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                Text("−", color = OnBackground.copy(0.3f))
                Text("${String.format("%.4f", fatLostKg * 1000)} g graisses", style = MaterialTheme.typography.labelSmall, color = color.copy(0.8f))
                if (ketosisOn && glycoLostKg > 0) {
                    Text("−", color = OnBackground.copy(0.3f))
                    Text("${String.format("%.2f", glycoLostKg * 1000)} g glycogène+H₂O", style = MaterialTheme.typography.labelSmall, color = Gold.copy(0.8f))
                }
            }
        }
    }
}

@Composable
private fun SessionControls(
    running: Boolean, elapsed: Double, saved: Boolean,
    onStartPause: () -> Unit, onSave: () -> Unit, onReset: () -> Unit,
) {
    val hasElapsed = elapsed > 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStartPause,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black)
            Spacer(Modifier.width(4.dp))
            Text(if (running) "Pause" else if (hasElapsed) "Reprendre" else "Démarrer",
                color = Color.Black, fontWeight = FontWeight.Bold)
        }
        if (hasElapsed) {
            if (!running) {
                if (saved) {
                    OutlinedButton(onClick = {}, enabled = false, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Check, null, tint = Teal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sauvegardé", color = Teal)
                    }
                } else {
                    OutlinedButton(onClick = onSave, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sauver")
                    }
                }
            }
            OutlinedButton(onClick = onReset, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Utility formatters ─────────────────────────────────────────────────────────
private fun formatElapsed(sec: Double): String {
    val total = sec.toLong()
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "${h}h ${m.toString().padStart(2,'0')}m ${s.toString().padStart(2,'0')}s"
    else if (m > 0) "${m}m ${s.toString().padStart(2,'0')}s"
    else "${s}s"
}

private fun formatFastingTime(fh: Double): String? {
    if (fh <= 0) return null
    return when {
        fh >= 720  -> "${"%.1f".format(fh / 720)}mo"
        fh >= 168  -> "${(fh / 168).toInt()}s ${((fh % 168) / 24).toInt()}j"
        fh >= 48   -> "${(fh / 24).toInt()}j ${(fh % 24).toInt()}h"
        fh >= 1    -> "${fh.toInt()}h ${((fh % 1) * 60).toInt()}m"
        else       -> "${(fh * 60).toInt()}m"
    }
}
