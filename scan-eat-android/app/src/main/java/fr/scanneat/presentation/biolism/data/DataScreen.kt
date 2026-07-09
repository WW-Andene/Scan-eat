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
import kotlin.math.*

@Composable
fun DataScreen(viewModel: DataViewModel = hiltViewModel()) {
    val profile  = viewModel.profile.collectAsStateWithLifecycle()
    val timer    = viewModel.timer.collectAsStateWithLifecycle()
    val m        = viewModel.metabolics.collectAsStateWithLifecycle()
    val hormones = viewModel.hormones.collectAsStateWithLifecycle()
    val meals    = viewModel.meals.collectAsStateWithLifecycle()
    val sessions = viewModel.sessions.collectAsStateWithLifecycle()
    val manualHR = viewModel.manualHR.collectAsStateWithLifecycle()
    val cum      = viewModel.sessionCumulative.collectAsStateWithLifecycle()
    val hist7d   = viewModel.history7d.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle()  // force recomposition every second

    val met = m.value
    val s   = timer.value

    if (met == null) {
        val bgColor = MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.MonitorHeart, null, tint = Gold, modifier = Modifier.size(48.dp))
                Text("Complète ton profil Biolism", style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text("Onglet Profil", style = MaterialTheme.typography.bodySmall, color = Gold)
            }
        }
        return
    }

    val bgColor2 = MaterialTheme.colorScheme.background
    LazyColumn(
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
            val ibwDelta = profile.value.weightKg - met.ibwMean
            MetCellGrid(listOf(
                Triple("Devine", "%.1f kg".format(met.ibwDevine), "1974"),
                Triple("Robinson", "%.1f kg".format(met.ibwRobinson), "1983"),
                Triple("Miller", "%.1f kg".format(met.ibwMiller), "1983"),
                Triple("Moyenne", "%.1f kg".format(met.ibwMean), "%s%.1f kg vs actuel".format(if (ibwDelta > 0) "+" else "", ibwDelta)),
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
            InfoRow("Niveau d'activité", profile.value.activityMeta.label, profile.value.activityMeta.note, Gold)
            MetCellGrid(listOf(
                Triple("BMR moyen", "%.1f".format(met.bmrDay), "kcal/j (consensus)"),
                Triple("Mifflin-St Jeor", "%.1f".format(met.bmrMsj), "kcal/j"),
                Triple("Katch-McArdle", "%.1f".format(met.bmrKm), "kcal/j · masse maigre"),
            ) + if (s.ketosisOn) listOf(Triple("BMR supprimé", "%.1f".format(met.bmrDay * met.ketoSupprFactor), "kcal/j · T3 adj.")) else emptyList())
            Spacer(Modifier.height(6.dp))
            InfoRow("Déficit (−500 kcal)", "%.1f kcal/j".format(met.tdeeDay - 500), "~0,45 kg/semaine perdu", Teal)
            InfoRow("Surplus (+300 kcal)", "%.1f kcal/j".format(met.tdeeDay + 300), "objectif prise de masse maigre", Violet)
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
            InfoRow("Répartition substrats", "G %d%% · Gluc %d%% · P %d%%".format(
                (met.sub.fatFrac * 100).toInt(), (met.sub.carbFrac * 100).toInt(), (met.sub.protFrac * 100).toInt()),
                "fraction de l'énergie totale", TextSecondary)
            InfoRow("Sortie thermodynamique", "%.4f W".format(met.watts), "", Gold)
            InfoRow("VO₂ consommé", "%.4f L/min".format(met.vo2PerMin), "", Violet)
            InfoRow("CO₂ produit", "%.4f L/min".format(met.vco2PerMin), "", TextSecondary)
            InfoRow("Équiv. oxycalorique", "%.4f kcal/L O₂".format(met.sub.oxycaloric), "", if (s.ketosisOn) Teal else TextSecondary)
            cum.value?.let { c ->
                Spacer(Modifier.height(8.dp))
                TintedPanel(if (s.ketosisOn) Teal else Gold) {
                    Label("Cumul de session", if (s.ketosisOn) Teal else Gold)
                    InfoRow("kcal brûlées", "%.4f kcal".format(c.kcalTotal), "", if (s.ketosisOn) Teal else Gold)
                    InfoRow("O₂ consommé", "%.4f L".format(c.o2LitersTotal), "", Violet)
                    InfoRow("CO₂ produit", "%.4f L".format(c.co2LitersTotal), "", TextSecondary)
                    InfoRow("Graisse oxydée", "%.2f mg".format(c.fatOxidisedMg), "", if (s.ketosisOn) Teal else Warm)
                    if (s.ketosisOn) InfoRow("Glycogène déplété", "%.2f g".format(c.glycogenDepletedG), "+ %.2f g H₂O".format(c.glycogenWaterG), Gold)
                    InfoRow("Protéines catabolisées", "%.2f mg".format(c.proteinCatabolisedMg), "%.2f mg N₂".format(c.n2ExcretedMg), Violet)
                }
            }
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
            InfoRow("β-Hydroxybutyrate", if (met.bhbMmolPerMin > 0) "%.4f mmol/min".format(met.bhbMmolPerMin) else "— (pas de cétose)",
                if (met.ketoActivation > 0) "activation CPT-I %.0f%% · McGarry 1980".format(met.ketoActivation * 100) else "", Teal)
            InfoRow("Gluconéogenèse", "%.3f g glucose/h".format(met.gngGPerHr),
                "%.0f%% du catabolisme protéique → GNG · Cahill 1966".format(met.gngProtFrac * 100), Gold)
        }}

        // ── Ketosis Process ─────────────────────────────────────────────────────
        if (s.ketosisOn) {
            item {
                val phase = BiolismEngine.ketoPhaseInfo(s.ketoHours, s.ketoAdapted)
                val phaseColor = colorFromToken(phase.colorToken)
                BioCard("Processus de cétose", badge = { Badge(phase.label.uppercase(), phaseColor) }) {
                    Text(formatDuration(s.ketoElapsedMs), style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium), color = phaseColor)
                    Text("en cétose", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    Spacer(Modifier.height(6.dp))
                    Text(phase.description, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (phase.progressPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = phaseColor, trackColor = OnBackground.copy(0.06f),
                    )
                    Spacer(Modifier.height(10.dp))
                    MetCellGrid(
                        listOf(
                            Triple("Temps en cétose", formatDuration(s.ketoElapsedMs), ""),
                            if (s.fastingHours > 0) Triple("Jeûne", "%.1f h".format(s.fastingHours), "")
                            else Triple("Taux GNG", "%.2f g/h".format(met.gngGPerHr), ""),
                            Triple("Cétones est.", phase.estimatedKetoneMmol, ""),
                            Triple("RQ en direct", "%.3f".format(met.sub.rq), ""),
                        ),
                        accents = listOf(phaseColor, Gold, TextSecondary, phaseColor),
                    )
                    if (s.ketoHours >= 1440) {
                        Spacer(Modifier.height(8.dp))
                        Text("Attention : jeûne prolongé : réserves de graisse en voie d'épuisement, le catabolisme protéique augmente à nouveau.",
                            style = MaterialTheme.typography.labelSmall, color = Severe, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

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
            InfoRow("  ↳ transépidermique", "%.4f W".format(met.heatEvapSkinW), "~15% · diffusion cutanée", Violet)
            InfoRow("  ↳ respiratoire", "%.4f W".format(met.heatEvapRespW), "~8% · évaporation respiratoire", Violet)
            Spacer(Modifier.height(8.dp))
            Label("Pertes hydriques insensibles", OnBackground.copy(0.4f))
            MetCellGrid(
                listOf(
                    Triple("Resp. (RWL)", "%.2f mL/h".format(met.rwlMlPerHr), "%.0f mL/j".format(met.rwlMlPerHr * 24)),
                    Triple("Transépidermique", "%.2f mL/h".format(met.tewlMlPerHr), "%.0f mL/j".format(met.tewlMlPerHr * 24)),
                    Triple("Total (IWL)", "%.2f mL/h".format(met.iwlMlPerHr), "%.0f mL/j".format(met.iwlMlPerHr * 24)),
                    Triple("Eau métab. produite", "%.2f mL/h".format(met.metWaterMlPerHr), "Hill 2004"),
                ),
                accents = listOf(Teal, Violet, Gold, Teal)
            )
            Spacer(Modifier.height(8.dp))
            TintedPanel(Teal) {
                Label("Bilan hydrique — turnover", Teal)
                InfoRow("Eau métabolique produite", "%.2f mL/h · %.0f mL/j".format(met.metWaterMlPerHr, met.metWaterMlPerHr * 24), "", Teal)
                InfoRow("Pertes insensibles", "%.2f mL/h · %.0f mL/j".format(met.iwlMlPerHr, met.iwlMlPerHr * 24), "hors urine et sueur", Warm)
                InfoRow("Bilan net", "%+.2f mL/h · %+.0f mL/j".format(met.netHydroBalMlPerHr, met.netHydroBalMlPerHr * 24),
                    "métab. − insensibles", if (met.netHydroBalMlPerHr >= 0) Teal else Warm)
            }
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
            val estGluc = BiolismEngine.computeBloodGlucoseMmol(
                weightKg = profile.value.weightKg, kcalSec = met.kcalSec, carbFrac = met.sub.carbFrac,
                ketoHours = s.ketoHours, fastingHours = s.fastingHours, ketosis = s.ketosisOn,
                elapsedSec = s.elapsedMs / 1000.0,
            )
            InfoRow("Glycémie estimée", "%.2f mmol/L".format(estGluc), "modèle cinétique simplifié · Guyton & Hall 2016",
                if (estGluc < 3.0) Danger else if (estGluc < 3.9) Warm else Teal)
            cum.value?.let { c ->
                Spacer(Modifier.height(8.dp))
                TintedPanel(Violet) {
                    Label("Cumul de session", Violet)
                    InfoRow("Eau métabolique totale", "%.3f g".format(c.metWaterTotalG), "", Teal)
                    InfoRow("ATP total", if (c.atpTotalMmol >= 1000) "%.2f mol".format(c.atpTotalMmol / 1000) else "%.2f mmol".format(c.atpTotalMmol), "", Gold)
                    InfoRow("N₂ excrété total", if (c.n2ExcretedMg >= 1000) "%.4f g".format(c.n2ExcretedMg / 1000) else "%.3f mg".format(c.n2ExcretedMg), "", Violet)
                }
            }

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
                    Text("Estimations basées sur âge, sexe, composition corporelle et contexte métabolique. Pas un substitut à une analyse sanguine.",
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

                    val kh = s.ketoHours; val fh = s.fastingHours
                    val modifiers = buildList {
                        if (kh > 24) add(Triple("Cétose (${kh.toInt()}h) →", "T↑ · Insuline↓ · Glucagon↑ · T3↓ · Leptine↓", Teal))
                        if (fh > 12) add(Triple("Jeûne (${fh.toInt()}h) →", "GH↑ · Glucagon↑ · Cortisol↑ · Ghréline↑", Violet))
                        if (s.ketoAdapted) add(Triple("Céto-adapté →", "Ghréline↓↓ · Leptine adaptée · T3 nadir", Gold))
                        if (met.bfPct > 25) add(Triple("Masse grasse élevée (${met.bfPct.toInt()}%) →",
                            "${if (profile.value.sex == BiolismSex.MALE) "T↓ · E2↑" else "E2↑"} · Résistance insuline ↑", Warm))
                    }
                    if (modifiers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        TintedPanel(OnBackground) {
                            Label("Modificateurs actifs", OnBackground.copy(0.4f))
                            modifiers.forEach { (label, tags, color) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                                    Text(tags, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Macro Targets ─────────────────────────────────────────────────────
        item { BioCard("Objectifs macros & nutrition", defaultOpen = false, badge = { GoldBadge("MINIMUMS JOURNALIERS") }) {
            InfoRow("TDEE", "%.0f kcal · BMR %.0f × %.3f · MMC %.1f kg".format(met.tdeeDay, met.bmrDay, profile.value.activityMeta.mult, met.ffm), "", Gold)
            Spacer(Modifier.height(4.dp))
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
            Spacer(Modifier.height(10.dp))
            Label("Objectifs complémentaires", OnBackground.copy(0.4f))
            InfoRow("Eau", "≥ %.1f L".format(met.waterNeedL),
                "EFSA 2010 · ${if (profile.value.sex == BiolismSex.MALE) "2,5" else "2,0"} L${if (profile.value.activityMeta.mult >= 1.55) " + 0,5 L activité" else ""}", Teal)
            InfoRow("Fibres alimentaires", "≥ 25 g", "EFSA 2017 · apport suffisant pour transit & microbiote", Gold)
            InfoRow("Sodium", "1500–2300 mg", "WHO 2012 · cible AHA 1500 mg · limite NHANES 2300 mg", Warm)
            InfoRow("Potassium", "≥ ${if (profile.value.sex == BiolismSex.MALE) "3400" else "2600"} mg", "NASEM DRI 2019 · contre l'excrétion sodique", Violet)
        }}

        // ── All-Time Summary ──────────────────────────────────────────────────
        if (sessions.value.isNotEmpty()) {
            item {
                val allSessions = sessions.value
                val totalKcal = allSessions.sumOf { it.kcalBurned }
                val totalSec  = allSessions.sumOf { it.elapsedSec }
                val avgKcal   = totalKcal / allSessions.size
                val avgSec    = totalSec / allSessions.size
                val spark     = allSessions.takeLast(7)
                val sparkMax  = (spark.maxOfOrNull { it.kcalBurned } ?: 0.001).coerceAtLeast(0.001)
                BioCard("Résumé global", badge = { TealBadge("${allSessions.size} SESSION${if (allSessions.size > 1) "S" else ""}") }) {
                    MetCellGrid(listOf(
                        Triple("Total brûlé", if (totalKcal >= 1000) "%.2fk".format(totalKcal / 1000) else "%.1f".format(totalKcal), "kcal"),
                        Triple("Temps total", formatDuration((totalSec * 1000).toLong()), ""),
                        Triple("Moyenne/session", "%.2f".format(avgKcal), "kcal"),
                        Triple("Durée moyenne", formatDuration((avgSec * 1000).toLong()), ""),
                    ))
                    if (spark.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Label("${spark.size} dernières sessions — kcal brûlées", OnBackground.copy(0.4f))
                        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                            spark.forEachIndexed { i, sess ->
                                val isLast = i == spark.size - 1
                                val h = (sess.kcalBurned / sparkMax * 48.0).coerceAtLeast(6.0).toInt()
                                val alpha = 0.35f + 0.65f * (i / (spark.size - 1).coerceAtLeast(1).toFloat())
                                Box(Modifier.weight(1f).height(h.dp).background(if (isLast) Gold else Gold.copy(alpha = alpha), RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }
            item {
                BioCard("Objectifs quotidiens") {
                    InfoRow("BMR (au repos)", "%.1f kcal/j".format(met.bmrDay), "besoin métabolique de base", TextSecondary)
                    InfoRow("TDEE (actif)", "%.1f kcal/j".format(met.tdeeDay), profile.value.activityMeta.label, Gold)
                    InfoRow("Sessions aujourd'hui", "${sessions.value.count { isToday(it.timestamp) }}", "enregistrées aujourd'hui", Teal)
                }
            }
        }

        // ── Equations & sources ────────────────────────────────────────────────
        item { EquationsCard(met = met, profile = profile.value) }

        // ── Caloric Balance ───────────────────────────────────────────────────
        item { CaloricBalanceCard(meals = meals.value, met = met, sessions = sessions.value, history7d = hist7d.value, viewModel = viewModel) }

        // ── Session Analytics ─────────────────────────────────────────────────
        if (sessions.value.isNotEmpty()) {
            item { SessionAnalyticsCard(sessions = sessions.value, currentWeightKg = profile.value.weightKg) }
        }

        // ── Session History ────────────────────────────────────────────────────
        if (sessions.value.isNotEmpty()) {
            item {
                val allSessions = sessions.value
                val prKcal = allSessions.maxOf { it.kcalBurned }
                val prDur  = allSessions.maxOf { it.elapsedSec }
                val prRate = allSessions.maxOf { it.kcalPerMin }
                var expandedId by remember { mutableStateOf<Long?>(null) }
                var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
                BioCard("Historique des sessions", defaultOpen = false, badge = { TealBadge("${allSessions.size}") }) {
                    allSessions.forEach { sess ->
                        val date = sess.timestamp.take(10)
                        val hh   = (sess.elapsedSec / 3600).toInt()
                        val mm   = ((sess.elapsedSec % 3600) / 60).toInt()
                        val dur  = if (hh > 0) "${hh}h ${mm.toString().padStart(2,'0')}m" else "${mm}m ${(sess.elapsedSec % 60).toInt()}s"
                        val isExpanded = expandedId == sess.id
                        val isConfirm  = confirmDeleteId == sess.id
                        val isPRKcal = sess.kcalBurned >= prKcal && allSessions.size > 1
                        val isPRDur  = sess.elapsedSec >= prDur && allSessions.size > 1
                        val isPRRate = sess.kcalPerMin >= prRate && allSessions.size > 1

                        Column(Modifier.fillMaxWidth().clickable { expandedId = if (isExpanded) null else sess.id }.padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(date, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                                        if (sess.ketosis) {
                                            Surface(shape = RoundedCornerShape(3.dp), color = TealHaze, border = androidx.compose.foundation.BorderStroke(1.dp, TealBorder)) {
                                                Text("KÉTO", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (isPRKcal) Badge("Record kcal", Gold)
                                        if (isPRDur) Badge("Record durée", Violet)
                                        if (isPRRate) Badge("Record taux", Teal)
                                    }
                                    Text("${sess.activityLabel} · $dur · ${sess.kcalBurned.toInt()} kcal",
                                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                                    Spacer(Modifier.height(3.dp))
                                    Row(Modifier.fillMaxWidth(0.6f).height(3.dp).background(OnBackground.copy(0.06f), RoundedCornerShape(2.dp))) {
                                        Box(Modifier.fillMaxWidth(sess.fatFrac.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Warm, RoundedCornerShape(2.dp)))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("%.4f kg".format(sess.fatLostKg), style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.SemiBold)
                                    Text("graisse perdue", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                                }
                            }
                            AnimatedVisibility(isExpanded) {
                                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    InfoRow("Taux moyen", "%.3f kcal/min".format(sess.kcalPerMin), "", TextSecondary)
                                    InfoRow("BMR / TDEE", "%.0f / %.0f kcal/j".format(sess.bmrDay, sess.tdeeDay), "", TextSecondary)
                                    InfoRow("Poids début → fin", "%.1f → %.1f kg".format(sess.startWeightKg, sess.endWeightKg), "", TextSecondary)
                                    InfoRow("Fraction graisse", "%.0f%%".format(sess.fatFrac * 100), "", Warm)
                                    if (!isConfirm) {
                                        TextButton(onClick = { confirmDeleteId = sess.id }) {
                                            Text("Supprimer", color = Danger, style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Confirmer la suppression ?", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                                            TextButton(onClick = {
                                                viewModel.deleteSession(sess.id)
                                                confirmDeleteId = null
                                                if (expandedId == sess.id) expandedId = null
                                            }) { Text("Oui", color = Danger, fontWeight = FontWeight.Bold) }
                                            TextButton(onClick = { confirmDeleteId = null }) {
                                                Text("Annuler", color = OnBackground.copy(0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = OnBackground.copy(0.06f))
                    }
                }
            }
        }

        // ── Reminders ──────────────────────────────────────────────────────────
        item { fr.scanneat.presentation.reminders.RemindersCard() }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── Equations & sources sub-card ────────────────────────────────────────────────
@Composable
private fun EquationsCard(met: MetabolicResult, profile: BiolismProfile) {
    BioCard("Équations & sources", defaultOpen = false, badge = { Badge("RÉFÉRENCE", TextSecondary) }) {
        Text(
            "Chaque valeur affichée dans Biolism dérive d'une formule publiée. Ci-dessous : la formule, tes chiffres actuels substitués, et la source.",
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
        )
        Spacer(Modifier.height(10.dp))
        EqBlock("BMR — Mifflin-St Jeor (1990)", "10×poids + 6,25×taille − 5×âge + s",
            "10×%.1f + 6,25×%.1f − 5×%d %s = %.1f kcal/j".format(
                profile.weightKg, profile.heightCm, profile.ageYears,
                if (profile.sex == BiolismSex.MALE) "+ 5" else "− 161", met.bmrMsj))
        EqBlock("BMR — Katch-McArdle (1996)", "370 + 21,6×masse maigre",
            "370 + 21,6×%.1f kg = %.1f kcal/j".format(met.ffm, met.bmrKm))
        EqBlock("Masse grasse — Deurenberg (1991)", "1,20×IMC + 0,23×âge − 10,8×sexe − 5,4 (IMC ajusté ethnie)",
            "= %.1f%% (offset %s : %+.1f)".format(met.bfPct, profile.ethnicMeta.label, profile.ethnicMeta.bmiOffset))
        EqBlock("Surface corporelle — DuBois (1916)", "0,007184 × poids^0,425 × taille^0,725",
            "= %.4f m²".format(met.bsa))
        EqBlock("Échanges gazeux — Weir (1949) / Frayn (1983)", "V̇O₂ = BMR / (équiv. oxycal. × 1440)",
            "%.4f L/min · RQ %.3f".format(met.vo2PerMin, met.sub.rq))
        EqBlock("Oxydation substrats — Atwater", "kcal/min × fraction / densité énergétique (9,0 / 4,0 / 4,1 kcal/g)",
            "Graisses %.3f · Glucides %.3f · Prot. %.3f g/min".format(met.fatOxGPerMin, met.carbOxGPerMin, met.protOxGPerMin))
        EqBlock("Flux lipolytique AGL — Wolfe (1990)", "oxyd. graisses / (1 − fraction réestérification)",
            "= %.4f g/min".format(met.ffaFluxGPerMin))
        EqBlock("β-hydroxybutyrate — McGarry & Foster (1980)", "1 palmitate → 4 BHB (flux hépatique AGL / 0,256 × 4 × activation)",
            "= %.4f mmol/min".format(met.bhbMmolPerMin))
        EqBlock("Acétyl-CoA — Berg, Tymoczko & Stryer (2015)", "β-ox 8/palmitate + PDH 2/glucose + AA ~0,6",
            "= %.3f mmol/min total".format(met.acCoaTotalMmolMin))
        EqBlock("Gluconéogenèse — Cahill (1966) / Jungas (1992)", "oxyd. protéines × fraction GNG × 0,58 g glucose/g protéine",
            "= %.3f g glucose/h".format(met.gngGPerHr))
        EqBlock("Dissipation thermique — Fanger (1970)", "rayonnement 45% + convection 30% + évaporation 23% + conduction 2%",
            "= %.3f W total".format(met.watts))
        EqBlock("Pertes hydriques — Moran (2007) / Pinnagoda (1990)", "resp. = V̇E×60×(44−10)/760×(18/22,4)×1000 · transépid. = 0,45×SC",
            "RWL %.2f + TEWL %.2f = %.2f mL/h".format(met.rwlMlPerHr, met.tewlMlPerHr, met.iwlMlPerHr))
        EqBlock("Poids idéal — Devine/Robinson/Miller", "moyenne des 3 formules linéaires (taille au-dessus de 152,4 cm)",
            "= %.1f kg (moyenne)".format(met.ibwMean))
        EqBlock("Planchers macros — Phillips & Van Loon (2011) / Cahill (2006)", "protéines g/kg MMC selon activité · glucides 120g→30g en cétose >24h",
            "P %.0fg · G %.0fg · L %.0fg".format(met.macroProtMinG, met.macroCarbMinG, met.macroFatMinG))
    }
}

@Composable
private fun EqBlock(title: String, formula: String, substituted: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Gold, fontWeight = FontWeight.SemiBold)
        Text(formula, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        Text(substituted, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f), fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = OnBackground.copy(0.06f))
}

// ── Caloric Balance sub-card ────────────────────────────────────────────────────
@Composable
private fun CaloricBalanceCard(
    meals: List<MealEntry>, met: MetabolicResult,
    sessions: List<fr.scanneat.domain.engine.biolism.BiolismSession>,
    history7d: List<fr.scanneat.data.repository.biolism.BiolismRepository.DayKcalEntry>,
    viewModel: DataViewModel,
) {
    val slots = listOf("breakfast" to "Petit-déjeuner", "lunch" to "Déjeuner", "dinner" to "Dîner", "snack" to "Collations")
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
                            val (l, v) = labelVal
                            OutlinedTextField(value = v, onValueChange = setter, label = { Text(l, fontSize = 10.sp) },
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

            // Today's-intake gauge vs TDEE
            val kcalPct = (totalIn / met.tdeeDay * 100.0).coerceAtMost(120.0)
            Label("Apport du jour vs TDEE", OnBackground.copy(0.4f))
            LinearProgressIndicator(
                progress = { (kcalPct / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = if (totalIn > met.tdeeDay) Danger else Teal,
                trackColor = OnBackground.copy(0.06f),
            )
            Text("%.0f / %.0f kcal".format(totalIn, met.tdeeDay), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
            Spacer(Modifier.height(8.dp))

            // Per-macro progress bars vs targets
            listOf(
                Triple("Protéines", totalProt to met.macroProtMinG, Violet),
                Triple("Glucides", totalCarb to met.macroCarbMinG, Teal),
                Triple("Lipides", totalFat to met.macroFatMinG, Warm),
            ).forEach { (label, vals, color) ->
                val (value, target) = vals
                if (value > 0) {
                    val pct = (value / target.coerceAtLeast(0.001) * 100.0).coerceAtMost(100.0)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                        Text("%.1fg / %.0fg".format(value, target), style = MaterialTheme.typography.labelSmall, color = color)
                    }
                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).padding(bottom = 6.dp),
                        color = color, trackColor = OnBackground.copy(0.06f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
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

            // Surplus/deficit bidirectional gauge
            if (netBal != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(OnBackground.copy(0.06f), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxHeight().width(1.dp).align(Alignment.Center).background(OnBackground.copy(0.2f)))
                    val frac = (kotlin.math.abs(netBal) / met.tdeeDay * 100.0).coerceAtMost(50.0).toFloat() / 100f
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .align(if (netBal > 0) Alignment.CenterEnd else Alignment.CenterStart)
                            .background(if (netBal > 0) Danger else Teal, RoundedCornerShape(3.dp)),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("← déficit", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                    Text("équilibré", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                    Text("surplus →", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                }
            }
        }

        // 7-day intake sparkline
        if (history7d.size > 1) {
            Spacer(Modifier.height(10.dp))
            Label("Historique 7 jours", OnBackground.copy(0.4f))
            val sparkMax = (history7d.maxOfOrNull { it.kcal } ?: 0.0).coerceAtLeast(met.tdeeDay * 1.2)
            val todayStr = java.time.LocalDate.now().toString()
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                history7d.forEach { day ->
                    val isTodayEntry = day.date == todayStr
                    val aboveTdee = day.kcal > met.tdeeDay
                    val h = (if (day.kcal > 0) (day.kcal / sparkMax * 40.0).coerceAtLeast(6.0) else 3.0).toInt()
                    val color = when {
                        isTodayEntry && aboveTdee -> Danger
                        isTodayEntry -> Teal
                        aboveTdee -> Danger.copy(alpha = 0.4f)
                        else -> Teal.copy(alpha = 0.3f)
                    }
                    Box(Modifier.weight(1f).height(h.dp).background(color, RoundedCornerShape(2.dp)))
                }
            }
            Text("TDEE réf : %.0f kcal/j".format(met.tdeeDay), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
        }
    }
}

// ── Session Analytics sub-card ────────────────────────────────────────────────
@Composable
private fun SessionAnalyticsCard(sessions: List<fr.scanneat.domain.engine.biolism.BiolismSession>, currentWeightKg: Double) {
    val withRate = sessions.filter { it.elapsedSec > 0 }
    val avgBurnRate = if (withRate.isNotEmpty())
        withRate.sumOf { it.kcalBurned / (it.elapsedSec / 60.0) } / withRate.size
    else 0.0
    val totalFatLostKg = sessions.sumOf { it.fatLostKg }

    val effScores = sessions.takeLast(8).map { it.kcalBurned / (it.elapsedSec / 60.0).coerceAtLeast(0.0001) }
    val effMax = (effScores.maxOrNull() ?: 0.0).coerceAtLeast(0.001)

    var rolling = 0.0
    val compHistory = sessions.map { sess -> rolling += sess.fatLostKg; rolling }
    val latestFatLost = compHistory.lastOrNull() ?: 0.0
    val latestWeight = currentWeightKg - latestFatLost
    val prevFatLost = if (compHistory.size >= 2) compHistory[compHistory.size - 2] else null
    val sessionDelta = if (prevFatLost != null) latestFatLost - prevFatLost else latestFatLost
    val deltaColor = if (sessionDelta > 0.0005) Teal else if (sessionDelta < -0.0005) Severe else TextSecondary
    val trendLabel = if (sessionDelta > 0.0005) "↓ perte" else if (sessionDelta < -0.0005) "↑ gain" else "→ stable"
    val weightDelta = latestWeight - currentWeightKg

    BioCard("Analyse des sessions", defaultOpen = false, badge = { TealBadge("${sessions.size} SESSIONS") }) {
        MetCellGrid(
            listOf(
                Triple("Taux moyen", "%.3f kcal/min".format(avgBurnRate), ""),
                Triple("Graisse cumulée perdue", if (totalFatLostKg >= 0.01) "%.3f kg".format(totalFatLostKg) else "%.2f g".format(totalFatLostKg * 1000), ""),
                Triple("Δ poids (dernière session)", "%s%.1f g".format(if (weightDelta > 0) "+" else "", weightDelta * 1000), trendLabel),
                Triple("Meilleure efficacité", "%.3f kcal/min".format(effMax), ""),
            ),
            accents = listOf(TextSecondary, Teal, deltaColor, Gold),
        )

        if (effScores.size > 1) {
            Spacer(Modifier.height(10.dp))
            Label("Efficacité de session — ${effScores.size} dernières", OnBackground.copy(0.4f))
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                effScores.forEachIndexed { i, score ->
                    val isLast = i == effScores.size - 1
                    val h = (score / effMax * 44.0).coerceAtLeast(4.0).toInt()
                    val alpha = 0.35f + 0.65f * (i / (effScores.size - 1).coerceAtLeast(1).toFloat())
                    Box(Modifier.weight(1f).height(h.dp).background(if (isLast) Violet else Violet.copy(alpha = alpha), RoundedCornerShape(2.dp)))
                }
            }
        }

        if (compHistory.size > 1) {
            Spacer(Modifier.height(10.dp))
            TintedPanel(Violet) {
                Label("Tendance composition corporelle", Violet)
                val last8 = compHistory.takeLast(8)
                val maxFat = (last8.maxOrNull() ?: 0.001).coerceAtLeast(0.001)
                Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    last8.forEachIndexed { i, fatLost ->
                        val isLast = i == last8.size - 1
                        val h = (fatLost / maxFat * 32.0).coerceAtLeast(3.0).toInt()
                        Box(Modifier.weight(1f).height(h.dp).background(if (isLast) Teal else Teal.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                InfoRow("Graisse oxydée (cumul)", "%.1f g".format(totalFatLostKg * 1000), "", Teal)
                InfoRow("Poids estimé actuel", "%.3f kg".format(latestWeight), "", deltaColor)
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

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val sec = totalSec % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, sec)
}

private fun isToday(iso: String): Boolean {
    return try { iso.startsWith(java.time.LocalDate.now().toString()) } catch (e: Exception) { false }
}
