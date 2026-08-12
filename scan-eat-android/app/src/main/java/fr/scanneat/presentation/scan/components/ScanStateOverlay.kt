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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import fr.scanneat.presentation.report.ReportMisclassificationDialog
import fr.scanneat.domain.engine.nonconsumable.CleansingBase
import fr.scanneat.domain.engine.nonconsumable.CosingMatch
import fr.scanneat.domain.engine.nonconsumable.FormulaComplexity
import fr.scanneat.domain.engine.nonconsumable.ShampooQualityResult
import fr.scanneat.domain.engine.nonconsumable.AbsorbentHygieneFacts
import fr.scanneat.domain.engine.nonconsumable.CosmeticActivesResult
import fr.scanneat.domain.engine.nonconsumable.IntimateWipeQualityResult
import fr.scanneat.domain.engine.nonconsumable.MakeupEducationalFacts
import fr.scanneat.domain.engine.nonconsumable.MakeupQualityResult
import fr.scanneat.domain.engine.nonconsumable.ShowerGelCleansingBase
import fr.scanneat.domain.engine.nonconsumable.ShowerGelQualityResult
import fr.scanneat.domain.engine.nonconsumable.ToothpasteQualityResult
import fr.scanneat.domain.engine.nonconsumable.computeCosmeticActives
import fr.scanneat.domain.engine.nonconsumable.computeCosmeticTransparency
import fr.scanneat.domain.engine.nonconsumable.computeIntimateWipeQuality
import fr.scanneat.domain.engine.nonconsumable.computeMakeupQuality
import fr.scanneat.domain.engine.nonconsumable.computeShampooQuality
import fr.scanneat.domain.engine.nonconsumable.computeShowerGelQuality
import fr.scanneat.domain.engine.nonconsumable.computeToothpasteQuality
import fr.scanneat.domain.engine.nonconsumable.findProhibitedSubstances
import fr.scanneat.domain.engine.nonconsumable.findRestrictedSubstances
import fr.scanneat.domain.engine.nonconsumable.generateAbsorbentHygieneFacts
import fr.scanneat.domain.engine.nonconsumable.generateMakeupEducationalFacts
import fr.scanneat.domain.engine.nonconsumable.generateNonConsumableHints
import fr.scanneat.domain.engine.nonconsumable.isLikelyAbsorbentHygieneProduct
import fr.scanneat.domain.engine.nonconsumable.isLikelyGeneralCosmetic
import fr.scanneat.domain.engine.nonconsumable.isLikelyIntimateWipe
import fr.scanneat.domain.engine.nonconsumable.isLikelyMakeup
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
    onReportMisclassification: (barcode: String?, productName: String, brand: String, currentClassification: String, correctedClassification: String, note: String) -> Unit = { _, _, _, _, _, _ -> },
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
                if (isLikelyShampoo(s.entry.name, s.entry.brand)) computeShampooQuality(s.entry.ingredientsText) else null
            }
            // app-audit: étape 3b (per-category functional score, gel douche
            // second) - see ShowerGelQualityScore.kt's own header for the data source.
            val showerGelQuality = remember(s.entry) {
                if (isLikelyShowerGel(s.entry.name, s.entry.brand)) computeShowerGelQuality(s.entry.ingredientsText) else null
            }
            // app-audit: étape 3c (per-category functional score, dentifrice
            // third) - see ToothpasteQualityScore.kt's own header for the data source.
            val toothpasteQuality = remember(s.entry) {
                if (isLikelyToothpaste(s.entry.name, s.entry.brand)) computeToothpasteQuality(s.entry.ingredientsText) else null
            }
            // app-audit: étape 3d (per-category functional score, cosmétique
            // général fourth) - see CosmeticActivesScore.kt's own header for
            // the data source. Only checked when the more specific
            // shampoo/gel-douche/dentifrice gates above didn't already match.
            val cosmeticActives = remember(s.entry) {
                if (shampooQuality == null && showerGelQuality == null && toothpasteQuality == null && isLikelyGeneralCosmetic(s.entry.name, s.entry.brand)) {
                    computeCosmeticActives(s.entry.ingredientsText)
                } else null
            }
            // app-audit: étape 3e (per-category functional score, hygiène
            // intime fifth/last) - see IntimateHygieneScore.kt's own header
            // for why this splits into an ingredient-based wipe score and a
            // name-based educational fact set for tampons/pads.
            val intimateWipeQuality = remember(s.entry) {
                if (isLikelyIntimateWipe(s.entry.name)) computeIntimateWipeQuality(s.entry.ingredientsText) else null
            }
            val absorbentHygieneFacts = remember(s.entry, language) {
                if (isLikelyAbsorbentHygieneProduct(s.entry.name, s.entry.brand)) generateAbsorbentHygieneFacts(s.entry.name, language) else null
            }
            // app-audit: étape 3f (per-category functional score, maquillage
            // last) - see MakeupQualityScore.kt's own header for the data source.
            val makeupQuality = remember(s.entry) {
                if (isLikelyMakeup(s.entry.name, s.entry.brand)) computeMakeupQuality(s.entry.ingredientsText) else null
            }
            val makeupEducationalFacts = remember(s.entry, language) {
                if (isLikelyMakeup(s.entry.name, s.entry.brand)) generateMakeupEducationalFacts(s.entry.name, language) else null
            }
            // User-requested: "signaler une erreur de classification" - e.g.
            // a real food product landing here instead of ResultScreen. See
            // ReportMisclassificationDialog's own header.
            var showReportDialog by remember(s.entry) { mutableStateOf(false) }
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
                        CosmeticTransparencySection(transparency, prohibited, restricted, showRetinolNote = cosmeticActives?.retinoid == true)
                        if (shampooQuality != null) ShampooQualitySection(shampooQuality)
                        if (showerGelQuality != null) ShowerGelQualitySection(showerGelQuality)
                        if (toothpasteQuality != null) ToothpasteQualitySection(toothpasteQuality)
                        if (cosmeticActives != null) CosmeticActivesSection(cosmeticActives)
                        if (intimateWipeQuality != null) IntimateWipeQualitySection(intimateWipeQuality)
                        if (absorbentHygieneFacts != null) AbsorbentHygieneFactsSection(absorbentHygieneFacts)
                        if (makeupQuality != null) MakeupQualitySection(makeupQuality)
                        if (makeupEducationalFacts != null && makeupEducationalFacts.facts.isNotEmpty()) MakeupEducationalFactsSection(makeupEducationalFacts)
                        FactsCautionsColumn(hints.facts, hints.cautions)
                    }
                },
                confirmButton = { TextButton(onClick = onDismissFound) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
                dismissButton = {
                    TextButton(onClick = { showReportDialog = true }) {
                        Text(stringResource(R.string.report_misclassification_action_cd), color = OnBackground.copy(0.6f))
                    }
                },
            )
            if (showReportDialog) {
                ReportMisclassificationDialog(
                    productName = s.entry.name,
                    currentClassificationLabel = s.entry.category.name,
                    onSubmit = { corrected, note ->
                        onReportMisclassification(s.entry.barcode, s.entry.name, s.entry.brand, s.entry.category.name, corrected, note)
                        showReportDialog = false
                    },
                    onDismiss = { showReportDialog = false },
                )
            }
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
private fun CosmeticTransparencySection(
    result: CosmeticTransparencyResult?, prohibited: List<CosingMatch>, restricted: List<CosingMatch>,
    // True when CosmeticActivesScore.kt also flagged this same product's retinol/
    // retinyl esters as a RETINOID (pregnancy-precaution) caution - shown alongside
    // this section's own Annex III restricted-substance line for the exact same
    // ingredient (ref 376 in cosing_annex_iii_restricted.csv, added by Regulation
    // 2024/996's population vitamin-A-overexposure concentration cap). Both facts
    // are real and independently sourced, not a duplicate of one signal, but
    // without this note a user sees "restricted substance" and "pregnancy
    // caution" for the same ingredient with no indication they're two distinct
    // regulatory concerns rather than one alarm shown twice.
    showRetinolNote: Boolean = false,
) {
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
            if (showRetinolNote && restricted.any { it.referenceNumber == "376" }) {
                Text(
                    stringResource(R.string.nonconsumable_retinol_annex_iii_note),
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                )
            }
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
            CleansingBase.GENTLE  -> stringResource(R.string.cleansing_base_gentle) to semanticGreen()
            CleansingBase.MIXED   -> stringResource(R.string.cleansing_base_mixed) to semanticAmber()
            CleansingBase.HARSH   -> stringResource(R.string.cleansing_base_harsh) to semanticAmber()
            CleansingBase.UNKNOWN -> stringResource(R.string.nonconsumable_transparency_no_data) to OnBackground.copy(0.5f)
        }
        Text(stringResource(R.string.cleansing_base_label, baseLabel), style = MaterialTheme.typography.bodySmall, color = baseColor)
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
            ShowerGelCleansingBase.GENTLE  -> stringResource(R.string.cleansing_base_gentle) to semanticGreen()
            ShowerGelCleansingBase.MIXED   -> stringResource(R.string.cleansing_base_mixed) to semanticAmber()
            ShowerGelCleansingBase.HARSH   -> stringResource(R.string.cleansing_base_harsh) to semanticAmber()
            ShowerGelCleansingBase.UNKNOWN -> stringResource(R.string.nonconsumable_transparency_no_data) to OnBackground.copy(0.5f)
        }
        Text(stringResource(R.string.cleansing_base_label, baseLabel), style = MaterialTheme.typography.bodySmall, color = baseColor)
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

/**
 * User-requested: a real functional profile for general cosmetics/skincare
 * (creams, lotions, serums - fourth of the planned series) - see
 * CosmeticActivesScore.kt's own header for the sourcing. Presence-only
 * signal, not an efficacy guarantee (see header on vitamin C formulation
 * stability specifically).
 */
@Composable
private fun CosmeticActivesSection(result: CosmeticActivesResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.cosmetic_actives_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        if (result.hasNiacinamide) {
            Text(stringResource(R.string.cosmetic_has_niacinamide), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        if (result.hasVitaminC) {
            Text(stringResource(R.string.cosmetic_has_vitamin_c), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        if (result.hasRetinoid) {
            Text(stringResource(R.string.cosmetic_has_retinoid_caution), style = MaterialTheme.typography.bodySmall, color = semanticAmber())
        }
        if (result.humectantCount > 0) {
            Text(stringResource(R.string.cosmetic_has_humectant, result.humectantCount), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        }
        if (result.mineralUvFilterCount > 0) {
            Text(stringResource(R.string.cosmetic_has_mineral_uv_filter, result.mineralUvFilterCount), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        if (result.chemicalUvFilterCautionCount > 0) {
            Text(stringResource(R.string.cosmetic_has_chemical_uv_filter_caution, result.chemicalUvFilterCautionCount), style = MaterialTheme.typography.bodySmall, color = semanticAmber())
        }
        Text(stringResource(R.string.cosmetic_actives_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
    }
}

/**
 * User-requested: a real functional profile for intimate wet wipes
 * specifically (fifth/last of the planned series) - see
 * IntimateHygieneScore.kt's own header for the sourcing.
 */
@Composable
private fun IntimateWipeQualitySection(result: IntimateWipeQualityResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.intimate_wipe_quality_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        if (result.hasFragrance) {
            Text(stringResource(R.string.intimate_wipe_has_fragrance), style = MaterialTheme.typography.bodySmall, color = semanticAmber())
        }
        if (result.hasAlcohol) {
            Text(stringResource(R.string.intimate_wipe_has_alcohol), style = MaterialTheme.typography.bodySmall, color = semanticAmber())
        }
        if (result.hasPhBuffering) {
            Text(stringResource(R.string.intimate_wipe_has_ph_buffering), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        Text(stringResource(R.string.intimate_wipe_quality_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
    }
}

/**
 * NOT a per-ingredient score (tampons/pads rarely carry a usable ingredient
 * list - see IntimateHygieneScore.kt's own header) - a small set of
 * well-sourced educational facts, explicitly separating what's
 * well-established from what's weakly evidenced or still under active
 * research, rather than a hazard/quality verdict.
 */
@Composable
private fun AbsorbentHygieneFactsSection(result: AbsorbentHygieneFacts) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.absorbent_hygiene_facts_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        result.facts.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f)) }
        result.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = semanticAmber()) }
    }
}

/**
 * User-requested: a real functional profile for makeup specifically (last of
 * the planned series) - see MakeupQualityScore.kt's own header for the
 * sourcing.
 */
@Composable
private fun MakeupQualitySection(result: MakeupQualityResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.makeup_quality_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        if (result.hasComedogenicContested) {
            Text(stringResource(R.string.makeup_has_comedogenic_contested), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        }
        if (result.hasRegulatedPreservative) {
            Text(stringResource(R.string.makeup_has_regulated_preservative), style = MaterialTheme.typography.bodySmall, color = semanticGreen())
        }
        if (result.hasTalc) {
            Text(stringResource(R.string.makeup_has_talc), style = MaterialTheme.typography.bodySmall, color = semanticAmber())
        }
        Text(stringResource(R.string.makeup_quality_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
    }
}

/**
 * NOT a per-ingredient score - trace-contaminant regulatory status and
 * applicator-hygiene guidance can't be read from an ingredient list, see
 * MakeupQualityScore.kt's own header.
 */
@Composable
private fun MakeupEducationalFactsSection(result: MakeupEducationalFacts) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(
            stringResource(R.string.makeup_educational_facts_title),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnBackground,
        )
        result.facts.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f)) }
    }
}
