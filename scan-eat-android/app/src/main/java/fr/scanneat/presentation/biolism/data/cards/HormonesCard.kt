package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun HormonesCard(h: HormoneResult, s: TimerState, met: MetabolicResult, profile: BiolismProfile) {
    BioCard(stringResource(R.string.biolism_hormones_title), defaultOpen = false,
        badge = { GoldBadge(stringResource(R.string.biolism_hormones_badge)) }) {
        Text(stringResource(R.string.biolism_hormones_disclaimer),
            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
            modifier = Modifier.background(OnBackground.copy(0.03f), RoundedCornerShape(6.dp)).padding(Spacing.S))
        Spacer(Modifier.height(Spacing.S))

        Label(stringResource(R.string.biolism_hormones_sex_section), Gold)
        listOf(
            Triple(stringResource(R.string.biolism_hormones_testosterone), h.testosterone, "Harman 2001"),
            Triple(stringResource(R.string.biolism_hormones_estradiol), h.estradiol, "Santoro 2008"),
            h.progesterone?.let { Triple(stringResource(R.string.biolism_hormones_progesterone), it, "Speroff 2011") },
            Triple(stringResource(R.string.biolism_hormones_dheas), h.dheaS, "Orentreich 1984"),
        ).filterNotNull().forEach { (name, reading, note) ->
            HormoneRow(name, reading, note)
        }
        Spacer(Modifier.height(Spacing.S))
        Label(stringResource(R.string.biolism_hormones_metabolic_section), Teal)
        listOf(
            Triple(stringResource(R.string.biolism_hormones_insulin), h.insulin, "Phinney 2012"),
            Triple(stringResource(R.string.biolism_hormones_glucagon), h.glucagon, "Unger 1978"),
            Triple(stringResource(R.string.biolism_hormones_cortisol), h.cortisol, "Bjorntorp 2000"),
            Triple(stringResource(R.string.biolism_hormones_ft3), h.fT3, "Phinney 1983"),
        ).forEach { (name, reading, note) -> HormoneRow(name, reading, note) }
        Spacer(Modifier.height(Spacing.S))
        Label(stringResource(R.string.biolism_hormones_appetite_section), Violet)
        listOf(
            Triple(stringResource(R.string.biolism_hormones_leptin), h.leptin, "Considine 1996"),
            Triple(stringResource(R.string.biolism_hormones_ghrelin), h.ghrelin, "Tschop 2000"),
            Triple(stringResource(R.string.biolism_hormones_gh), h.gh, "Hartman 1992"),
            Triple(stringResource(R.string.biolism_hormones_igf1), h.igf1, "Giustina 2008"),
        ).forEach { (name, reading, note) -> HormoneRow(name, reading, note) }

        val kh = s.ketoHours; val fh = s.fastingHours
        val ketosisLabel = stringResource(R.string.biolism_hormones_mod_ketosis, kh.toInt())
        val ketosisTags = stringResource(R.string.biolism_hormones_mod_ketosis_tags)
        val fastingLabel = stringResource(R.string.biolism_hormones_mod_fasting, fh.toInt())
        val fastingTags = stringResource(R.string.biolism_hormones_mod_fasting_tags)
        val adaptedLabel = stringResource(R.string.biolism_hormones_mod_adapted)
        val adaptedTags = stringResource(R.string.biolism_hormones_mod_adapted_tags)
        val highFatLabel = stringResource(R.string.biolism_hormones_mod_highfat, met.bfPct.toInt())
        val highFatTags = stringResource(
            if (profile.sex == BiolismSex.MALE) R.string.biolism_hormones_mod_highfat_tags_male
            else R.string.biolism_hormones_mod_highfat_tags_female,
        )
        val modifiers = buildList {
            if (kh > 24) add(Triple(ketosisLabel, ketosisTags, Teal))
            if (fh > 12) add(Triple(fastingLabel, fastingTags, Violet))
            if (s.ketoAdapted) add(Triple(adaptedLabel, adaptedTags, Gold))
            if (met.bfPct > 25) add(Triple(highFatLabel, highFatTags, Warm))
        }
        if (modifiers.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.S))
            TintedPanel(OnBackground) {
                Label(stringResource(R.string.biolism_hormones_modifiers_title), OnBackground.copy(0.4f))
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
