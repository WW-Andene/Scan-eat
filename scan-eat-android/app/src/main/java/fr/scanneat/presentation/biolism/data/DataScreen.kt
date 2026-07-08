package fr.scanneat.presentation.biolism.data

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.data.repository.biolism.BiolismRepository.MealEntry
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.ui.theme.BiolismTokens
import kotlin.math.*

@Composable
fun DataScreen(viewModel: DataViewModel = hiltViewModel(), theme: String = "oled") {
    val profile  = viewModel.profile.collectAsStateWithLifecycle()
    val timer    = viewModel.timer.collectAsStateWithLifecycle()
    val m        = viewModel.metabolics.collectAsStateWithLifecycle()
    val hormones = viewModel.hormones.collectAsStateWithLifecycle()
    val meals    = viewModel.meals.collectAsStateWithLifecycle()
    val sessions = viewModel.sessions.collectAsStateWithLifecycle()
    val manualHR = viewModel.manualHR.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle()  // force recomposition every second

    val met = m.value
    val s   = timer.value

    if (met == null) {
        val bgColor = BiolismTokens.background(theme)
        Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚗️", fontSize = 48.sp)
                Text("Complète ton profil Biolism", style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text("Onglet Profil", style = MaterialTheme.typography.bodySmall, color = Gold)
            }
        }
        return
    }

    LazyColumn(
        val bgColor2 = BiolismTokens.background(theme)
        modifier = Modifier.fillMaxSize().background(bgColor2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Body Composition ──────────────────────────────────────────────────
        item { BioCard("Composition corporelle", defaultOpen = true, badge = { BmiChip(met) }) {
            MetCellGrid(listOf(
                Triple("IMC", "%.1f".format(met.bmi), "kg/m² · WHO 2000"),
                Triple("Graisse corporelle", "%.1f%%".format(met.bfPct), "Deurenberg 1991"),
                Triple("Masse maigre", "%.1f kg".format(met.ffm), "sans graisse"),
                Triple("Masse grasse", "%.1f kg".format(met.fm), "graisse adipocytaire"),
            ))
            // Navy tape (when available)
            met.navyBfPct?.let { navy ->
                Spacer(Modifier.height(8.dp))
                TintedPanel(Teal) {
                    Label("Méthode Navy Tape — Hodgdon & Beckett 1984", Teal)
                    MetCellGrid(listOf(
                        Triple("Navy BF%", "%.1f%%".format(navy), "mesuré au ruban"),
                        Triple("Navy masse maigre", "%.1f kg".format(met.navyFfm ?: 0.0), "masse maigre"),
                        Triple("Navy masse grasse", "%.1f kg".format(met.navyFm ?: 0.0), "masse grasse"),
                        Triple("Δ vs Deurenberg", "%+.1f%%".format(navy - met.bfPct), ""),
                    ))
                }
            }
            // IBW
            Spacer(Modifier.height(8.dp))
            Label("Poids idéal", OnBackground.copy(0.4f))
            MetCellGrid(listOf(
                Triple("Devine", "%.1f kg".format(met.ibwDevine), "1974"),
                Triple("Robinson", "%.1f kg".format(met.ibwRobinson), "1983"),
                Triple("Miller", "%.1f kg".format(met.ibwMiller), "1983"),
                Triple("Moyenne", "%.1f kg".format(met.ibwMean), ""),
            ))
            // Visceral
            Spacer(Modifier.height(8.dp))
            TintedPanel(Gold) {
                Label("Indicateurs graisse viscérale", Gold)
                met.whtr?.let { v ->
                    InfoRow("Tour taille/Taille (WHtR)", "%.3f".format(v),
                        if (v < 0.40) "Mince" else if (v < 0.50) "Sain" else if (v < 0.60) "Risque central" else "Risque élevé",
                        if (v < 0.50) Teal else if (v < 0.60) Gold else Danger)
                } ?: Text("Ajouter Tour de taille dans Profil pour déverrouiller WHtR",
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                met.whr?.let { v ->
                    val thresh = if (profile.value.sex == BiolismSex.MALE) 0.90 else 0.85
                    InfoRow("Tour taille/Hanches (WHR)", "%.3f".format(v),
                        if (v < thresh) "Faible risque" else "Risque élevé",
                        if (v < thresh) Teal else Danger)
                }
                met.bai?.let { v ->
                    InfoRow("Indice Adiposité Corporelle", "%.1f%%".format(v), "Bergman 2011", Gold)
                }
            }
        }}

        // ── Daily Energy ──────────────────────────────────────────────────────
        item { BioCard("Énergie quotidienne (TDEE)",
            badge = { if (s.ketosisOn) TealBadge("KÉTO −${((1.0 - met.ketoSupprFactor)*100).toInt()}%") }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Dépense Totale Journalière", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), letterSpacing = 1.sp)
                Text("%.1f".format(met.tdeeDay), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.W500), color = Gold)
                Text("kcal/jour · BMR × %.3f".format(met.tdeeDay / met.bmrDay.coerceAtLeast(1.0)), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            MetCellGrid(listOf(
                Triple("BMR moyen", "%.1f".format(met.bmrDay), "kcal/j (consensus)"),
                Triple("Mifflin-St Jeor", "%.1f".format(met.bmrMsj), "kcal/j"),
                Triple("Katch-McArdle", "%.1f".format(met.bmrKm), "kcal/j · masse maigre"),
            ) + if (s.ketosisOn) listOf(Triple("BMR supprimé", "%.1f".format(met.bmrDay * met.ketoSupprFactor), "kcal/j · T3 adj.")) else emptyList())
        }}

        // ── Burn Rate ─────────────────────────────────────────────────────────
        item { BioCard("Taux de combustion", badge = { TealBadge("RQ %.3f".format(met.sub.rq)) }) {
            MetCellGrid(listOf(
                Triple("Par seconde", "%.6f".format(met.kcalSec), "kcal/s"),
                Triple("Par minute", "%.4f".format(met.kcalSec * 60), "kcal/min"),
                Triple("Par heure", "%.2f".format(met.kcalSec * 3600), "kcal/h"),
                Triple("Par jour", "%.1f".format(met.bmrDay), "kcal/j"),
            ))
            Spacer(Modifier.height(6.dp))
            InfoRow("Sortie thermodynamique", "%.4f W".format(met.watts), "", Gold)
            InfoRow("VO₂ consommé", "%.4f L/min".format(met.vo2PerMin), "", Violet)
            InfoRow("CO₂ produit", "%.4f L/min".format(met.vco2PerMin), "", TextSecondary)
            InfoRow("Équiv. oxycalorique", "%.4f kcal/L O₂".format(met.sub.oxycaloric), "", if (s.ketosisOn) Teal else TextSecondary)
        }}

        // ── Substrate Flux ────────────────────────────────────────────────────
        item { BioCard("Flux de substrats", badge = { if (s.ketosisOn) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(6.dp).padding(top = 5.dp)); TealBadge("LIVE") } } }) {
            Label("Taux d'oxydation", OnBackground.copy(0.4f))
            MetCellGrid(
                listOf(
                    Triple("Oxyd. graisses", "%.4f g/min".format(met.fatOxGPerMin), "9,0 kcal/g"),
                    Triple("Oxyd. glucides", "%.4f g/min".format(met.carbOxGPerMin), "4,0 kcal/g"),
                    Triple("Oxyd. protéines", "%.4f g/min".format(met.protOxGPerMin), "4,1 kcal/g"),
                    Triple("Flux AGL lipolyse", "%.4f g/min".format(met.ffaFluxGPerMin), "adipose→plasma"),
                ),
                accents = listOf(Warm, OnBackground, Violet, Warm)
            )
            Spacer(Modifier.height(6.dp))
            InfoRow("Acétyl-CoA (total)", "%.3f mmol/min".format(met.acCoaTotalMmolMin), "", Gold)
            InfoRow("  ↳ β-oxydation", "%.3f mmol/min".format(met.acCoaFatMmolMin), "", Warm)
            InfoRow("  ↳ glycolyse (PDH)", "%.3f mmol/min".format(met.acCoaCarbMmolMin), "", TextSecondary)
            InfoRow("  ↳ protéines (AA)", "%.3f mmol/min".format(met.acCoaProtMmolMin), "", Violet)
            Spacer(Modifier.height(4.dp))
            InfoRow("β-Hydroxybutyrate", if (met.bhbMmolPerMin > 0) "%.4f mmol/min".format(met.bhbMmolPerMin) else "— (pas de cétose)", "", Teal)
            InfoRow("Gluconéogenèse", "%.3f g glucose/h".format(met.gngGPerHr), "", Gold)
        }}

        // ── Organ Heat ────────────────────────────────────────────────────────
        item { BioCard("Chaleur organique", badge = { VioletBadge("ELIA 1992") }) {
            val maxPct = met.organs.maxOfOrNull { it.pct } ?: 1.0
            val eliaBase = mapOf("Liver" to 26.0, "Skeletal Muscle" to 22.0, "Brain" to 18.0, "Residual" to 16.0, "Kidneys" to 9.0, "Heart" to 9.0)
            met.organs.forEach { organ ->
                val kcalDay = met.bmrDay * met.ketoSupprFactor * organ.pct / 100.0
                val delta   = organ.pct - (eliaBase[organ.name] ?: organ.pct)
                val barColor = colorFromToken(organ.colorToken)
                Column(Modifier.padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(organ.name, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("%.1f%%".format(organ.pct), style = MaterialTheme.typography.labelSmall, color = barColor, fontWeight = FontWeight.Bold)
                            if (s.ketosisOn && delta != 0.0) Text("%+.1f%%".format(delta), style = MaterialTheme.typography.labelSmall, color = if (delta > 0) Teal else Violet)
                            Text("· %.1f kcal/j".format(kcalDay), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (organ.pct / maxPct).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = barColor,
                        trackColor = OnBackground.copy(0.05f),
                    )
                }
            }
        }}

        // ── Thermoregulation ──────────────────────────────────────────────────
        item { BioCard("Thermorégulation & chaleur", defaultOpen = false, badge = { GoldBadge("FANGER 1970") }) {
            Label("Dissipation thermique — %.3f W total".format(met.watts), OnBackground.copy(0.4f))
            MetCellGrid(
                listOf(
                    Triple("Rayonnement", "%.4f W".format(met.heatRadW), "~45% · IR longwave"),
                    Triple("Convection", "%.4f W".format(met.heatConvW), "~30% · naturelle"),
                    Triple("Évaporation", "%.4f W".format(met.heatEvapW), "~23% · peau + resp."),
                    Triple("Conduction", "%.4f W".format(met.heatCondW), "~2% · contact direct"),
                ),
                accents = listOf(Warm, Teal, Violet, TextMuted)
            )
            Spacer(Modifier.height(8.dp))
            Label("Pertes hydriques insensibles", OnBackground.copy(0.4f))
            MetCellGrid(
                listOf(
                    Triple("Resp. (RWL)", "%.2f mL/h".format(met.rwlMlPerHr), "humidité expirée"),
                    Triple("Transépidermique", "%.2f mL/h".format(met.tewlMlPerHr), "Pinnagoda 1990"),
                    Triple("Total (IWL)", "%.2f mL/h".format(met.iwlMlPerHr), "%.0f mL/j".format(met.iwlMlPerHr * 24)),
                    Triple("Eau métab. produite", "%.2f mL/h".format(met.metWaterMlPerHr), "Hill 2004"),
                ),
                accents = listOf(Teal, Violet, Gold, Teal)
            )
            InfoRow("Bilan hydrique net", "%+.2f mL/h".format(met.netHydroBalMlPerHr),
                "(métab. − insensibles, hors urine et sueur)",
                if (met.netHydroBalMlPerHr >= 0) Teal else Warm)
        }}

        // ── Physiological metrics ──────────────────────────────────────────────
        item { BioCard("Métriques physiologiques", defaultOpen = false, badge = { VioletBadge("LIVE") }) {
            MetCellGrid(listOf(
                Triple("V̇E (ventilation)", "%.2f L/min".format(met.vePerMin), "VO₂ / (FiO₂ − FeO₂)"),
                Triple("FC estimée (repos)", "%.1f bpm".format(met.hrEstimated), "Fick · VS 70mL"),
                Triple("Production ATP", "%.2f mmol/min".format(met.atpMmolPerMin), "Berg 2015"),
                Triple("Eau métabolique", "%.3f g/min".format(met.metWaterPerMin), "Hill 2004"),
            ))
            InfoRow("Excrétion azote (N₂)", "%.4f mg/min · %.2f g/j".format(
                met.nExcrGPerDay / 1440.0 * 1000, met.nExcrGPerDay), "", Violet)

            // Manual HR cross-check
            Spacer(Modifier.height(8.dp))
            var hrText by remember { mutableStateOf(manualHR.value?.toString() ?: "") }
            TintedPanel(Violet) {
                Label("Vérification FC (Fick)", Violet)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hrText, onValueChange = { hrText = it; it.toIntOrNull()?.let { bpm -> viewModel.saveManualHR(bpm) } },
                        label = { Text("FC repos (bpm)") }, singleLine = true,
                        modifier = Modifier.width(140.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Violet, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                    )
                    manualHR.value?.let { mhr ->
                        val diff = mhr - met.hrEstimated
                        Column {
                            Text("%+.1f bpm (%.0f%%)".format(diff, abs(diff) / met.hrEstimated * 100),
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                                color = if (abs(diff) <= 10) Teal else if (abs(diff) <= 20) Gold else Danger)
                            Text("VS impliqué : %.1f mL".format(met.vo2PerMin * 1000 / (mhr * 0.05)),
                                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                        }
                    }
                }
            }
        }}

        // ── Hormones ──────────────────────────────────────────────────────────
        item {
            val h = hormones.value
            if (h != null) {
                BioCard("Hormones", defaultOpen = false, badge = { GoldBadge("ESTIMATION") }) {
                    Text("⚠ Estimations basées sur âge, sexe, composition corporelle et contexte métabolique. Pas un substitut à une analyse sanguine.",
                        style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
                        modifier = Modifier.background(OnBackground.copy(0.03f), RoundedCornerShape(6.dp)).padding(8.dp))
                    Spacer(Modifier.height(8.dp))

                    Label("Hormones sexuelles", Gold)
                    listOf(
                        Triple("Testostérone", h.testosterone, "Harman 2001"),
                        Triple("Estradiol (E2)", h.estradiol, "Santoro 2008"),
                        h.progesterone?.let { Triple("Progestérone", it, "Speroff 2011") },
                        Triple("DHEA-S", h.dheaS, "Orentreich 1984"),
                    ).filterNotNull().forEach { (name, reading, note) ->
                        HormoneRow(name, reading, note)
                    }
                    Spacer(Modifier.height(8.dp))
                    Label("Hormones métaboliques", Teal)
                    listOf(
                        Triple("Insuline", h.insulin, "Phinney 2012"),
                        Triple("Glucagon", h.glucagon, "Unger 1978"),
                        Triple("Cortisol", h.cortisol, "Bjorntorp 2000"),
                        Triple("T3 libre", h.fT3, "Phinney 1983"),
                    ).forEach { (name, reading, note) -> HormoneRow(name, reading, note) }
                    Spacer(Modifier.height(8.dp))
                    Label("Appétit & croissance", Violet)
                    listOf(
                        Triple("Leptine", h.leptin, "Considine 1996"),
                        Triple("Ghréline", h.ghrelin, "Tschop 2000"),
                        Triple("GH (moyen/j)", h.gh, "Hartman 1992"),
                        Triple("IGF-1", h.igf1, "Giustina 2008"),
                    ).forEach { (name, reading, note) -> HormoneRow(name, reading, note) }
                }
            }
        }

        // ── Macro Targets ─────────────────────────────────────────────────────
        item { BioCard("Objectifs macros & nutrition", defaultOpen = false, badge = { GoldBadge("MINIMUMS JOURNALIERS") }) {
            MacroTargetRow("Protéines", met.macroProtMinG, "g", "%.1f g/kg MMC × %.1f kg".format(met.protGPerKgFfm, met.ffm), Violet)
            MacroTargetRow("Glucides", met.macroCarbMinG, "g", if (met.macroCarbMinG < 50) "Cétose >24h · Cahill 2006" else "Besoin cérébral · IOM 2005", Teal)
            MacroTargetRow("Lipides", met.macroFatMinG, "g", "Résiduel TDEE · min. AGE = %.0f g".format(met.essentialFatMinG), Warm)

            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = GoldHaze, border = androidx.compose.foundation.BorderStroke(1.dp, GoldBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Total minimum", style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        Text("prot + glucides + lipides", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.0f kcal".format(met.macroFloorKcal), style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
                        val delta = met.macroFloorKcal - met.tdeeDay
                        Text("TDEE %.0f · %+.0f kcal".format(met.tdeeDay, delta), style = MaterialTheme.typography.labelSmall,
                            color = if (abs(delta) < 5) Teal else OnBackground.copy(0.5f))
                    }
                }
            }
        }}

        // ── Caloric Balance ───────────────────────────────────────────────────
        item { CaloricBalanceCard(meals = meals.value, met = met, sessions = sessions.value, viewModel = viewModel) }

        // ── Session History ────────────────────────────────────────────────────
        if (sessions.value.isNotEmpty()) {
            item {
                BioCard("Historique des sessions", defaultOpen = false, badge = { TealBadge("${sessions.value.size}") }) {
                    sessions.value.forEach { sess ->
                        val date = sess.timestamp.take(10)
                        val hh   = (sess.elapsedSec / 3600).toInt()
                        val mm   = ((sess.elapsedSec % 3600) / 60).toInt()
                        val dur  = if (hh > 0) "${hh}h ${mm.toString().padStart(2,'0')}m" else "${mm}m ${(sess.elapsedSec % 60).toInt()}s"
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(date, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                                    if (sess.ketosis) {
                                        Surface(shape = RoundedCornerShape(3.dp), color = TealHaze, border = androidx.compose.foundation.BorderStroke(1.dp, TealBorder)) {
                                            Text("KÉTO", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text("${sess.activityLabel} · $dur · ${sess.kcalBurned.toInt()} kcal",
                                    style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("%.4f kg".format(sess.fatLostKg), style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.SemiBold)
                                Text("graisse perdue", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                            }
                        }
                        HorizontalDivider(color = OnBackground.copy(0.06f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Caloric Balance sub-card ────────────────────────────────────────────────────
@Composable
private fun CaloricBalanceCard(
    meals: List<MealEntry>, met: MetabolicResult,
    sessions: List<fr.scanneat.domain.engine.biolism.BiolismSession>, viewModel: DataViewModel,
) {
    val slots = listOf("breakfast" to "🌅 Petit-déjeuner", "lunch" to "🥗 Déjeuner", "dinner" to "🍽️ Dîner", "snack" to "🍎 Collations")
    var editingSlot by remember { mutableStateOf<String?>(null) }
    var editKcal by remember { mutableStateOf("") }
    var editProt by remember { mutableStateOf("") }
    var editCarb by remember { mutableStateOf("") }
    var editFat  by remember { mutableStateOf("") }

    val totalIn   = meals.sumOf { it.kcal }
    val totalProt = meals.sumOf { it.proteinG }
    val totalCarb = meals.sumOf { it.carbsG }
    val totalFat  = meals.sumOf { it.fatG }

    val todaySessKcal = sessions.filter { isToday(it.timestamp) }.sumOf { it.kcalBurned }
    val totalOut  = met.tdeeDay + todaySessKcal
    val netBal    = if (totalIn > 0) totalIn - totalOut else null

    BioCard("Bilan calorique — Aujourd'hui", defaultOpen = true, badge = { TealBadge(if (met.sub.fatFrac > 0.5) "KÉTO" else "MIXTE") }) {
        slots.forEach { (slotId, label) ->
            val entry = meals.firstOrNull { it.slotId == slotId }
            val isEditing = editingSlot == slotId

            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (isEditing) { editingSlot = null } else {
                        editingSlot = slotId
                        editKcal = entry?.kcal?.toInt()?.toString() ?: ""
                        editProt = entry?.proteinG?.toInt()?.toString() ?: ""
                        editCarb = entry?.carbsG?.toInt()?.toString() ?: ""
                        editFat  = entry?.fatG?.toInt()?.toString() ?: ""
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = if (entry != null) TealTrace else OnBackground.copy(0.02f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (entry != null) TealBorder else OnBackground.copy(0.08f)),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = if (entry != null) OnBackground else OnBackground.copy(0.6f), fontWeight = FontWeight.Medium)
                        if (entry != null) {
                            Text("${entry.kcal.toInt()} kcal · P ${entry.proteinG.toInt()}g · G ${entry.carbsG.toInt()}g · L ${entry.fatG.toInt()}g",
                                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                        } else {
                            Text("Appuyer pour saisir", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entry != null) {
                            IconButton(onClick = { viewModel.clearMeal(slotId) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = OnBackground.copy(0.3f), modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(if (isEditing) "▲" else "▼", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    }
                }
            }
            AnimatedVisibility(isEditing) {
                Column(
                    modifier = Modifier.background(TealTrace, RoundedCornerShape(0.dp, 0.dp, 8.dp, 8.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("kcal" to editKcal to { v: String -> editKcal = v },
                               "P g"  to editProt to { v: String -> editProt = v },
                               "G g"  to editCarb to { v: String -> editCarb = v },
                               "L g"  to editFat  to { v: String -> editFat  = v },
                        ).forEach { (labelVal, setter) ->
                            val (lv, set) = labelVal
                            val (l, v) = lv
                            OutlinedTextField(value = v, onValueChange = set, label = { Text(l, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = OnBackground.copy(0.15f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
                        }
                    }
                    Button(onClick = {
                        viewModel.saveMeal(MealEntry(
                            slotId = slotId, kcal = editKcal.toDoubleOrNull() ?: 0.0,
                            proteinG = editProt.toDoubleOrNull() ?: 0.0,
                            carbsG   = editCarb.toDoubleOrNull() ?: 0.0,
                            fatG     = editFat.toDoubleOrNull()  ?: 0.0,
                        )); editingSlot = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Enregistrer", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (totalIn > 0) {
            HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = 8.dp))
            val balColor = if (netBal == null) TextMuted else if (netBal > 200) Danger else if (netBal < -50) Teal else Gold
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Bilan net", style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    Text("${totalIn.toInt()} entrées − ${totalOut.toInt()} sorties", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (netBal != null) "%+.0f kcal".format(netBal) else "—",
                        style = MaterialTheme.typography.titleLarge, color = balColor, fontWeight = FontWeight.Bold)
                    Text(if (netBal == null) "SAISIR REPAS" else if (netBal > 200) "EXCÉDENT" else if (netBal < -50) "DÉFICIT" else "ÉQUILIBRÉ",
                        style = MaterialTheme.typography.labelSmall, color = balColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Shared card / helper composables ──────────────────────────────────────────

@Composable
private fun BioCard(
    title: String,
    defaultOpen: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(defaultOpen) }
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.width(2.dp).height(16.dp).background(Gold, RoundedCornerShape(1.dp)))
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                badge?.invoke()
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = OnBackground.copy(0.4f), modifier = Modifier.size(20.dp))
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(top = 12.dp), content = content)
            }
        }
    }
}

// ── 2-column grid utility (see MetCellGrid below)
@Composable
private fun MetCellGrid(items: List<Triple<String, String, String>>, accents: List<Color> = emptyList()) {
    items.chunked(2).forEachIndexed { row, pair ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEachIndexed { col, (label, value, sub) ->
                val accent = accents.getOrNull(row * 2 + col) ?: OnBackground
                MetCell(label, value, sub, accent, Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetCell(label: String, value: String, sub: String, accent: Color = OnBackground, modifier: Modifier = Modifier.fillMaxWidth()) {
    Surface(shape = RoundedCornerShape(8.dp), color = OnBackground.copy(0.04f), modifier = modifier) {
        Column(Modifier.padding(9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
        }
    }
}



@Composable
private fun InfoRow(label: String, value: String, note: String, color: Color = OnBackground) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
        }
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Label(text: String, color: Color = OnBackground.copy(0.4f)) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun TintedPanel(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.15f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun HormoneRow(name: String, h: HormoneReading, note: String) {
    val color = colorFromToken(h.colorToken)
    val barPct = (h.value / (h.refHigh * 1.3)).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(name, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), fontWeight = FontWeight.Medium)
                Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%.1f ${h.unit}".format(h.value), style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(3.dp), color = color.copy(0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.3f))) {
                    Text(h.label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        // Fix 12: Canvas draws track, normal-range band, and value bar correctly
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            val w   = size.width
            val h3  = size.height
            val top = 1.5f    // half-height — used for RoundedCornerShape approximation via cornerRadius
            val scale = h.refHigh * 1.3
            val loFrac = (h.refLow  / scale).toFloat().coerceIn(0f, 1f)
            val hiFrac = (h.refHigh / scale).toFloat().coerceIn(0f, 1f)
            val valFrac = barPct.coerceIn(0f, 1f)
            // Track
            drawRoundRect(color.copy(alpha = 0.06f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(top))
            // Normal reference band
            drawRect(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                topLeft = androidx.compose.ui.geometry.Offset(w * loFrac, 0f),
                size    = androidx.compose.ui.geometry.Size(w * (hiFrac - loFrac), h3),
            )
            // Value bar
            if (valFrac > 0f) {
                drawRoundRect(
                    color       = color.copy(alpha = 0.75f),
                    size        = androidx.compose.ui.geometry.Size(w * valFrac, h3),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(top),
                )
            }
        }
        Text("réf: %.0f–%.0f %s".format(h.refLow, h.refHigh, h.unit), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.25f))
    }
}

@Composable
private fun MacroTargetRow(label: String, grams: Double, unit: String, note: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.1f $unit".format(grams), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
    HorizontalDivider(color = OnBackground.copy(0.06f))
}

@Composable
private fun BmiChip(m: MetabolicResult) {
    val color = when (m.bmiClass) {
        BiolismBmiCategory.NORMAL      -> Teal
        BiolismBmiCategory.UNDERWEIGHT -> Violet
        BiolismBmiCategory.OVERWEIGHT  -> Gold
        BiolismBmiCategory.OBESE       -> Danger
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.15f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.3f))) {
        Text(m.bmiClass.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun TealBadge(text: String) = Badge(text, Teal)
@Composable private fun GoldBadge(text: String) = Badge(text, Gold)
@Composable private fun VioletBadge(text: String) = Badge(text, Violet)

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.12f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.25f))) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

private fun colorFromToken(token: String): Color = when (token) {
    "Gold"        -> Gold
    "Teal"        -> Teal
    "Violet"      -> Violet
    "Warm"        -> Warm
    "Danger"      -> Danger
    "Severe"      -> Severe
    "IconInactive"-> IconInactive
    else          -> TextSecondary
}

private fun isToday(iso: String): Boolean {
    return try { iso.startsWith(java.time.LocalDate.now().toString()) } catch (e: Exception) { false }
}
