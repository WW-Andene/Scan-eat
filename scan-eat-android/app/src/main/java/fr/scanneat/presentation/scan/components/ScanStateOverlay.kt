package fr.scanneat.presentation.scan.components

import compose.icons.tablericons.Camera
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.medication.generateMedicationHints
import fr.scanneat.domain.engine.nonconsumable.CleansingBase
import fr.scanneat.domain.engine.nonconsumable.CosingMatch
import fr.scanneat.domain.engine.nonconsumable.FormulaComplexity
import fr.scanneat.domain.engine.nonconsumable.ShampooQualityResult
import fr.scanneat.domain.engine.nonconsumable.ShowerGelCleansingBase
import fr.scanneat.domain.engine.nonconsumable.ShowerGelQualityResult
import fr.scanneat.domain.engine.nonconsumable.ToothpasteQualityResult
import fr.scanneat.domain.engine.nonconsumable.computeCosmeticTransparency
import fr.scanneat.domain.engine.nonconsumable.computeShampooQuality
import fr.scanneat.domain.engine.nonconsumable.computeShowerGelQuality
import fr.scanneat.domain.engine.nonconsumable.computeToothpasteQuality
import fr.scanneat.domain.engine.nonconsumable.findProhibitedSubstances
import fr.scanneat.domain.engine.nonconsumable.findRestrictedSubstances
import fr.scanneat.domain.engine.nonconsumable.generateNonConsumableHints
import fr.scanneat.domain.engine.nonconsumable.isLikelyShampoo
import fr.scanneat.domain.engine.nonconsumable.isLikelyShowerGel
import fr.scanneat.domain.engine.nonconsumable.isLikelyToothpaste
import fr.scanneat.presentation.medication.InteractionWarning
import fr.scanneat.presentation.medication.components.MedicationInteractionWarningBanner
import fr.scanneat.presentation.result.FactsCautionsColumn
import fr.scanneat.presentation.scan.ScanUiState
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.ErrorBanner
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.domain.engine.nonconsumable.CosmeticTransparencyResult

@Composable
internal fun BoxScope.ScanStateOverlay(
    state: ScanUiState,
    hasCamera: Boolean,
    cameraUnavailable: Boolean,
    bottomNavClearance: Dp,
    language: String,
    healthConditions: Set<String>,
    onRetryScore: () -> Unit,
    onDismissError: () -> Unit,
    onDismissFound: () -> Unit,
    onSaveDetectedMedication: (fr.scanneat.domain.engine.medication.MedicationDbEntry) -> Unit,
    onPickMultiFood: (Long) -> Unit,
    medicationInteractionWarnings: List<InteractionWarning> = emptyList(),
) {
    when (val s = state) {
        is ScanUiState.Idle, is ScanUiState.Scanning, is ScanUiState.Success -> Unit
        is ScanUiState.Error -> {
            if (hasCamera && !cameraUnavailable) {
                if (s.needsPhoto) {
                    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + Spacing.XXL * 3)
                        .glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f)
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL))
                        .clip(RoundedCornerShape(CardRadius.CONTROL)),
                        color = SurfaceVariant.copy(alpha = 0.42f), shape = RoundedCornerShape(CardRadius.CONTROL), shadowElevation = 0.dp) {
                        Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                            Icon(TablerIcons.Camera, null, tint = AccentCoral)
                            Spacer(Modifier.width(Spacing.S))
                            Text(stringResource(R.string.scan_needs_photo),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            // The IconButton itself must keep Material's default 48dp touch
                            // target (WCAG 2.5.5/2.5.8) - sizing it down to 32dp shrank the
                            // tappable area, not just the glyph. Constrain the icon instead.
                            IconButton(onClick = onDismissError) {
                                Icon(TablerIcons.X, stringResource(R.string.common_close), tint = OnSurface, modifier = Modifier.size(IconSize.Inline))
                            }
                        }
                    }
                } else {
                    ErrorBanner(
                        message     = s.message,
                        modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + Spacing.XXL * 3),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction    = onRetryScore,
                        onDismiss   = onDismissError,
                    )
                }
            } else {
                // Same error surface as the camera path above, but reachable from the
                // no-camera/camera-unavailable fallbacks too - those flows call
                // viewModel.score() straight from manual barcode entry, with no FAB or
                // camera preview underneath, so a scoring failure there still needs
                // somewhere to show up instead of silently going nowhere.
                ErrorBanner(
                    message     = s.message,
                    modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + 24.dp),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction    = onRetryScore,
                    onDismiss   = onDismissError,
                )
            }
        }
        is ScanUiState.MedicationFound -> {
            val hints = remember(s.entry, language, healthConditions) {
                generateMedicationHints(s.entry, healthConditions, language)
            }
            AlertDialog(
                onDismissRequest = onDismissFound,
                containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
                modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                title = { Text(stringResource(R.string.scan_medication_found_title), color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(stringResource(R.string.scan_medication_found_body, s.entry.name), color = OnBackground.copy(0.7f))
                        // User-requested: surfaced right here, at scan time, against every
                        // already-active saved medication - not just later on the
                        // Médicament tab's own list screen (see
                        // ScanViewModel.medicationInteractionWarnings' own doc comment).
                        medicationInteractionWarnings.forEach { warning -> MedicationInteractionWarningBanner(warning) }
                        FactsCautionsColumn(hints.facts, hints.cautions)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSaveDetectedMedication(s.entry) }) {
                        Text(stringResource(R.string.scan_medication_found_add), color = Teal)
                    }
                },
                dismissButton = { TextButton(onClick = onDismissFound) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
            )
        }
        is ScanUiState.NonConsumableFound -> {
            val hints = remember(s.entry, language) { generateNonConsumableHints(s.entry.category, language) }
            val transparency = remember(s.entry) { computeCosmeticTransparency(s.entry.ingredientsText) }
            val appContext = LocalContext.current.applicationContext
            // app-audit: étape 2 - real EU Annex II/III regulatory-status check,
            // see CosingRegulatoryDb.kt's own header for the data source.
            // ScanViewModel's init already warms CosingStore's cache off Main,
            // so this lookup only ever pays a cheap in-memory map read here.
            val prohibited = remember(s.entry) { findProhibitedSubstances(appContext, s.entry.ingredientsText) }
            val restricted = remember(s.entry) { findRestrictedSubstances(appContext, s.entry.ingredientsText) }
            // app-audit: étape 3 (per-category functional score, shampoo first) -
            // see ShampooQualityScore.kt's own header for the data source.
            val shampooQuality = remember(s.entry) {
                if (isLikelyShampoo(s.entry.name)) computeShampooQuality(s.entry.ingredientsText) else null
            }
            // app-audit: étape 3b (per-category functional score, gel douche
            // second) - see ShowerGelQualityScore.kt's own header for the data source.
            val showerGelQuality = remember(s.entry) {
                if (isLikelyShowerGel(s.entry.name)) computeShowerGelQuality(s.entry.ingredientsText) else null
            }
            // app-audit: étape 3c (per-category functional score, dentifrice
            // third) - see ToothpasteQualityScore.kt's own header for the data source.
            val toothpasteQuality = remember(s.entry) {
                if (isLikelyToothpaste(s.entry.name)) computeToothpasteQuality(s.entry.ingredientsText) else null
            }
            AlertDialog(
                onDismissRequest = onDismissFound,
                containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
                modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                title = { Text(stringResource(R.string.scan_nonconsumable_found_title), color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(stringResource(R.string.scan_nonconsumable_found_body, s.entry.name, s.entry.brand), color = OnBackground.copy(0.8f))
                        Text(stringResource(R.string.scan_nonconsumable_safety_line), color = semanticRed(), fontWeight = FontWeight.SemiBold)
                        CosmeticTransparencySection(transparency, prohibited, restricted)
                        if (shampooQuality != null) ShampooQualitySection(shampooQuality)
                        if (showerGelQuality != null) ShowerGelQualitySection(showerGelQuality)
                        if (toothpasteQuality != null) ToothpasteQualitySection(toothpasteQuality)
                        FactsCautionsColumn(hints.facts, hints.cautions)
                    }
                },
                confirmButton = { TextButton(onClick = onDismissFound) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
            )
        }
        is ScanUiState.MultiFoodFound -> {
            MultiFoodFoundDialog(items = s.items, onPick = onPickMultiFood, onDismiss = onDismissFound)
        }
    }
}

/**
 * User-requested: a "score" for non-food products, alongside (not replacing)
 * the category-level safety cautions above - see CosmeticTransparencyScore.kt's
 * own header for why this is a composition/transparency signal, not a
 * toxicology verdict. null [result] means no ingredient data was available
 * (most non-food barcodes) - shown honestly as such rather than hidden.
 * [prohibited]/[restricted] are real EU Annex II/III regulatory matches (see
 * CosingRegulatoryDb.kt) - kept visually and textually distinct since Annex
 * III (restricted-with-conditions) covers ordinary, legally-used ingredients
 * like Retinol, not a hazard flag, unlike Annex II (genuinely prohibited).
 */
@Composable
private fun CosmeticTransparencySection(result: CosmeticTransparencyResult?, prohibited: List<CosingMatch>, restricted: List<CosingMatch>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.nonconsumable_transparency_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        if (result == null) {
            Text(stringResource(R.string.nonconsumable_transparency_no_data), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            return
        }
        val (complexityLabel, complexityColor) = when (result.complexity) {
            FormulaComplexity.SIMPLE   -> stringResource(R.string.nonconsumable_complexity_simple) to semanticGreen()
            FormulaComplexity.MODERATE -> stringResource(R.string.nonconsumable_complexity_moderate) to semanticAmber()
            FormulaComplexity.COMPLEX  -> stringResource(R.string.nonconsumable_complexity_complex) to semanticRed()
            FormulaComplexity.UNKNOWN  -> stringResource(R.string.nonconsumable_transparency_no_data) to OnBackground.copy(0.5f)
        }
        Text(
            stringResource(R.string.nonconsumable_ingredient_count, result.ingredientCount ?: 0, complexityLabel),
            style = MaterialTheme.typography.bodySmall, color = complexityColor,
        )
        if (result.detectedAllergens.isNotEmpty()) {
            Text(
                stringResource(R.string.nonconsumable_allergens_detected, result.detectedAllergens.size),
                style = MaterialTheme.typography.bodySmall, color = semanticAmber(),
            )
        }
        if (prohibited.isNotEmpty()) {
            Text(
                stringResource(R.string.nonconsumable_prohibited_detected, prohibited.size, prohibited.joinToString(", ") { it.name }),
                style = MaterialTheme.typography.bodySmall, color = semanticRed(), fontWeight = FontWeight.SemiBold,
            )
        }
        if (restricted.isNotEmpty()) {
            Text(
                stringResource(R.string.nonconsumable_restricted_detected, restricted.size, restricted.joinToString(", ") { it.name }),
                style = MaterialTheme.typography.bodySmall, color = semanticAmber(),
            )
        }
        Text(stringResource(R.string.nonconsumable_transparency_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
    }
}

/**
 * User-requested: a real functional profile for shampoo specifically (first
 * of a planned series: shampoo → gel douche → maquillage → cosmétique →
 * hygiène intime) - see ShampooQualityScore.kt's own header for the CosIng
 * function-taxonomy source and why this is a cleansing-base PROFILE, not a
 * hazard verdict (sulfates/silicones are both legal, common ingredients).
 */
@Composable
private fun ShampooQualitySection(result: ShampooQualityResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.shampoo_quality_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        val (baseLabel, baseColor) = when (result.cleansingBase) {
            CleansingBase.GENTLE  -> stringResource(R.string.shampoo_base_gentle) to semanticGreen()
            CleansingBase.MIXED   -> stringResource(R.string.shampoo_base_mixed) to semanticAmber()
            CleansingBase.HARSH   -> stringResource(R.string.shampoo_base_harsh) to semanticAmber()
            CleansingBase.UNKNOWN -> stringResource(R.string.nonconsumable_transparency_no_data) to OnBackground.copy(0.5f)
        }
        Text(stringResource(R.string.shampoo_base_label, baseLabel), style = MaterialTheme.typography.bodySmall, color = baseColor)
        if (result.siliconeCount > 0) {
            Text(stringResource(R.string.shampoo_contains_silicone, result.siliconeCount), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        }
        if (result.gentleConditionerCount > 0) {
            Text(stringResource(R.string.shampoo_contains_gentle_conditioner, result.gentleConditionerCount), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        Text(stringResource(R.string.shampoo_quality_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
    }
}

/**
 * User-requested: a real functional profile for shower gel/body wash
 * specifically (second of the planned series) - see
 * ShowerGelQualityScore.kt's own header for the sourcing and why this is a
 * cleansing-base PROFILE, not a hazard verdict.
 */
@Composable
private fun ShowerGelQualitySection(result: ShowerGelQualityResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.shower_gel_quality_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        val (baseLabel, baseColor) = when (result.cleansingBase) {
            ShowerGelCleansingBase.GENTLE  -> stringResource(R.string.shampoo_base_gentle) to semanticGreen()
            ShowerGelCleansingBase.MIXED   -> stringResource(R.string.shampoo_base_mixed) to semanticAmber()
            ShowerGelCleansingBase.HARSH   -> stringResource(R.string.shampoo_base_harsh) to semanticAmber()
            ShowerGelCleansingBase.UNKNOWN -> stringResource(R.string.nonconsumable_transparency_no_data) to OnBackground.copy(0.5f)
        }
        Text(stringResource(R.string.shampoo_base_label, baseLabel), style = MaterialTheme.typography.bodySmall, color = baseColor)
        if (result.soapBasedCount > 0) {
            Text(stringResource(R.string.shower_gel_contains_soap, result.soapBasedCount), style = MaterialTheme.typography.bodySmall, color = semanticAmber())
        }
        if (result.provenEmollientCount > 0) {
            Text(stringResource(R.string.shower_gel_contains_proven_emollient, result.provenEmollientCount), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        if (result.marketingEmollientCount > 0) {
            Text(stringResource(R.string.shower_gel_contains_marketing_emollient, result.marketingEmollientCount), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        }
        Text(stringResource(R.string.shower_gel_quality_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
    }
}

/**
 * User-requested: a real functional profile for toothpaste/dentifrice
 * specifically (third of the planned series) - see
 * ToothpasteQualityScore.kt's own header for the sourcing and the important
 * limitation that INCI ingredient lists carry no concentration (ppm/RDA)
 * data, so fluoride/abrasivity here are presence-only signals.
 */
@Composable
private fun ToothpasteQualitySection(result: ToothpasteQualityResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.toothpaste_quality_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        Text(
            if (result.hasFluoride) stringResource(R.string.toothpaste_has_fluoride) else stringResource(R.string.toothpaste_no_fluoride),
            style = MaterialTheme.typography.bodySmall, color = if (result.hasFluoride) semanticGreen() else semanticAmber(),
        )
        if (result.higherAbrasiveCount > 0) {
            Text(stringResource(R.string.toothpaste_higher_abrasive, result.higherAbrasiveCount), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        }
        if (result.lowerAbrasiveCount > 0) {
            Text(stringResource(R.string.toothpaste_lower_abrasive, result.lowerAbrasiveCount), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        }
        if (result.oralIrritantCount > 0) {
            Text(stringResource(R.string.toothpaste_oral_irritant, result.oralIrritantCount), style = MaterialTheme.typography.bodySmall, color = semanticAmber())
        }
        if (result.sensitivityCareCount > 0) {
            Text(stringResource(R.string.toothpaste_sensitivity_care, result.sensitivityCareCount), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        Text(stringResource(R.string.toothpaste_quality_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
    }
}
