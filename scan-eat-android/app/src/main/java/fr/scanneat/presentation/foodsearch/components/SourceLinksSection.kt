package fr.scanneat.presentation.foodsearch.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.SourceLink
import fr.scanneat.presentation.ui.theme.*

/**
 * Fixed, query-parameterized reference links (Wikipédia, Open Food Facts,
 * PubChem, ANSES/CIQUAL) - not a scraped result set, just the same manual
 * "search on X" a user could type into their own browser, surfaced up front
 * for whatever they typed (a food name, an additive code, a molecule). Opens
 * in the device's default browser via ACTION_VIEW - never fetched in-app.
 */
@Composable
internal fun SourceLinksSection(query: String, links: List<SourceLink>) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
        Text(
            stringResource(R.string.foodsearch_links_section_header, query),
            style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.XS))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            links.forEach { link ->
                ScanEatCard(
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(link.label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                        Icon(Icons.Rounded.OpenInNew, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Compact))
                    }
                }
            }
        }
    }
}
