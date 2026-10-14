package fr.scanneat.presentation.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.ENGINE_VERSION
import fr.scanneat.presentation.ui.theme.*

/**
 * One data source the app actually uses. Every entry matches a provenance
 * comment in the code (file named in [codeRef]) - nothing is listed that the
 * code does not use, and the version/date is the one recorded there.
 */
internal data class DataSourceInfo(
    val nameRes: Int,
    val usageRes: Int,
    val version: String,
    val url: String,
    val codeRef: String,
)

internal val DATA_SOURCES = listOf(
    DataSourceInfo(R.string.src_off_name, R.string.src_off_usage, "API v2", "https://world.openfoodfacts.org", "OpenFoodFactsApi.kt"),
    DataSourceInfo(R.string.src_rappelconso_name, R.string.src_rappelconso_usage, "rappelconso-v2-gtin-trie", "https://rappel.conso.gouv.fr", "RappelConsoApi.kt"),
    DataSourceInfo(R.string.src_ciqual_name, R.string.src_ciqual_usage, "CIQUAL 2020 · DOI 10.5281/zenodo.4770600", "https://ciqual.anses.fr", "FoodDb.kt"),
    DataSourceInfo(R.string.src_inco_name, R.string.src_inco_usage, "Règlement (UE) n° 1169/2011", "https://eur-lex.europa.eu/eli/reg/2011/1169/oj", "AllergenDetector.kt, NutritionalDensityPillar.kt"),
    DataSourceInfo(R.string.src_efsa_name, R.string.src_efsa_usage, "DRV 2010–2017", "https://www.efsa.europa.eu/en/topics/topic/dietary-reference-values", "DailyTargets.kt"),
    DataSourceInfo(R.string.src_bdpm_name, R.string.src_bdpm_usage, "2026-07-13", "https://base-donnees-publique.medicaments.gouv.fr", "MedicationLookupDb.kt"),
    DataSourceInfo(R.string.src_cosing_name, R.string.src_cosing_usage, "Règlement (CE) n° 1223/2009 · 2026-08-12", "https://single-market-economy.ec.europa.eu/sectors/cosmetics/cosmetic-ingredient-database_en", "CosingRegulatoryDb.kt"),
    DataSourceInfo(R.string.src_opf_name, R.string.src_opf_usage, "API v2", "https://world.openproductsfacts.org", "ScanRepositoryHistory.kt"),
)

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.sources_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.L)) }
            item {
                ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(stringResource(R.string.sources_method_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = OnBackground)
                    Text(stringResource(R.string.sources_method_body), style = MaterialTheme.typography.bodySmall, color = OnBackground)
                    Text(stringResource(R.string.sources_method_personal), style = MaterialTheme.typography.bodySmall, color = OnBackground)
                    Text(stringResource(R.string.sources_method_ocr), style = MaterialTheme.typography.bodySmall, color = OnBackground)
                    Text(stringResource(R.string.sources_engine_version, ENGINE_VERSION), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(alpha = 0.6f))
                }
            }
            item { Text(stringResource(R.string.sources_list_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = OnBackground) }
            items(DATA_SOURCES, key = { it.url }) { s ->
                ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(stringResource(s.nameRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = OnBackground)
                    Text(stringResource(s.usageRes), style = MaterialTheme.typography.bodySmall, color = OnBackground)
                    Text(s.version, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(alpha = 0.6f))
                    TextButton(onClick = { runCatching { uriHandler.openUri(s.url) } }, contentPadding = PaddingValues(0.dp)) {
                        Text(s.url.removePrefix("https://"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}
