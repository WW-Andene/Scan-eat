package fr.scanneat.presentation.scan.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.gradeColor
import fr.scanneat.presentation.ui.theme.semanticAmber

@Composable
internal fun BoxScope.ScanHeaderBar(
    topInset: Dp,
    todayScanCount: Int,
    isScanning: Boolean,
    barcode: String?,
    hasQueuedPhotosNoBarcode: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.55f), Color.Transparent)))
            .padding(horizontal = 20.dp).padding(top = topInset + Spacing.L, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            // New: today's scan count badge — previously there was no way to know how
            // many products you'd already scanned today without leaving the scan tab.
            if (todayScanCount > 0) {
                Surface(shape = RoundedCornerShape(50), color = AccentCoral.copy(0.85f)) {
                    Text(
                        "$todayScanCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // Improvement: state-aware subtitle instead of static hint — previously the
        // text never changed between Idle, Scanning, and photos-queued-no-barcode states,
        // so users had no text feedback that analysis was happening or what to do next.
        Text(
            when {
                isScanning -> stringResource(R.string.scan_analyzing)
                barcode != null -> stringResource(R.string.scan_barcode_prefix, barcode)
                hasQueuedPhotosNoBarcode -> stringResource(R.string.scan_hint_photos_ready)
                else -> stringResource(R.string.scan_hint)
            },
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f),
        )
    }
}

@Composable
internal fun BoxScope.ScanBarcodeChip(barcode: String, topInset: Dp, cachedPreview: ScanResult?, warning: String? = null) {
    Box(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 96.dp)
            .glassSheen(edgeAlpha = 0.22f, shape = RoundedCornerShape(24.dp), glowTint = AccentCoral, glowAlpha = 0.07f),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceVariant.copy(0.9f),
        ) {
            Column {
                Row(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.S))
                    Text(barcode, style = MaterialTheme.typography.labelLarge, color = OnSurface, fontWeight = FontWeight.Medium)
                    // "Already scanned this" cue — the local-cache lookup
                    // scoreBarcode() already does to skip the network on a
                    // rescan, surfaced here for the first time so the user
                    // sees it's a known product before even tapping the score
                    // FAB, instead of only finding out after the round-trip.
                    cachedPreview?.takeIf { it.barcode == barcode }?.let { cached ->
                        Spacer(Modifier.width(Spacing.S))
                        Surface(shape = RoundedCornerShape(12.dp), color = gradeColor(cached.audit.grade).copy(alpha = 0.2f)) {
                            Text(
                                "${cached.audit.score} ${cached.audit.grade.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = gradeColor(cached.audit.grade),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = Spacing.S, vertical = 2.dp),
                            )
                        }
                    }
                }
                // Same allergen/diet warning already shown on History/Dashboard/
                // Diary/MealPlan/Grocery/Recipes/Templates for this exact product -
                // previously this quick "already scanned" preview was the one
                // remaining place showing a familiar product's score with no hint
                // it conflicts with the user's own profile, ahead of even tapping
                // Score to reach the Result screen that does check it.
                if (warning != null && cachedPreview?.barcode == barcode) {
                    Row(
                        Modifier.padding(start = Spacing.L, end = Spacing.L, bottom = Spacing.S),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                    ) {
                        Icon(Icons.Default.WarningAmber, null, tint = semanticAmber(), modifier = Modifier.size(14.dp))
                        Text(warning, style = MaterialTheme.typography.labelSmall, color = semanticAmber())
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScanBoundingBoxOverlay(rect: android.graphics.Rect, imgW: Int, imgH: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / imgW.toFloat()
        val scaleY = size.height / imgH.toFloat()
        val scale  = maxOf(scaleX, scaleY)
        val offX   = (size.width  - imgW  * scale) / 2f
        val offY   = (size.height - imgH * scale) / 2f
        val left   = offX + rect.left   * scale
        val top    = offY + rect.top    * scale
        val right  = offX + rect.right  * scale
        val bottom = offY + rect.bottom * scale
        drawRoundRect(
            color        = AccentCoral,
            topLeft      = Offset(left, top),
            size         = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = CornerRadius(8f, 8f),
            style        = Stroke(width = 3f),
            alpha        = 0.85f,
        )
    }
}
