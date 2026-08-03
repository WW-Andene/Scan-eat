package fr.scanneat.presentation.scan.components

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.Grade
import fr.scanneat.presentation.ui.theme.*

/**
 * One in-flight or resolved "shelf peek" — a box the user tapped in shelf-scan
 * mode. [id] is a locally-generated monotonic counter (not a server/DB id),
 * used only to find-and-replace/remove the right entry in ScanScreen's peek
 * list as the identify call resolves.
 */
data class ShelfPeek(val id: Long, val anchor: Offset, val status: ShelfPeekStatus)

sealed interface ShelfPeekStatus {
    data object Loading : ShelfPeekStatus
    data class Ready(val name: String, val grade: Grade, val resultId: Long) : ShelfPeekStatus
    data class Failed(val message: String) : ShelfPeekStatus
}

/**
 * Live on-device bounding boxes over the camera preview (shelf-scan mode),
 * drawn with the same image→screen "fit center" mapping ScanBoundingBoxesOverlay
 * already uses for the single barcode box (see its own comment), extended to
 * N boxes and made tappable. A tap is translated back into image space via the
 * inverse of that same mapping to find which box (if any) was hit; the
 * reported position is the raw *screen* tap point (not the box's own
 * position), so the caller can anchor a mini-panel exactly where the user
 * touched rather than guessing at a box-center conversion a second time.
 *
 * No product identity is drawn here by design — see [DetectedBox]'s own doc
 * comment. This overlay only shows "something distinct is here, tap it."
 */
@Composable
fun ScanShelfObjectOverlay(objects: List<DetectedBox>, imgW: Int, imgH: Int, onBoxTapped: (DetectedBox, Offset) -> Unit) {
    // objects/imgW/imgH change on every detected frame (every ~100-300ms while
    // shelf mode is active) - keying pointerInput on them would restart
    // detectTapGestures's coroutine mid-gesture on every new frame, dropping
    // any tap that takes longer than one detection cycle to complete.
    // rememberUpdatedState lets the gesture handler always read the latest
    // values without the detector itself ever restarting (keyed on Unit).
    val currentObjects = rememberUpdatedState(objects)
    val currentImgW = rememberUpdatedState(imgW)
    val currentImgH = rememberUpdatedState(imgH)
    val currentOnBoxTapped = rememberUpdatedState(onBoxTapped)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val w = currentImgW.value
                    val h = currentImgH.value
                    val scaleX = size.width  / w.toFloat()
                    val scaleY = size.height / h.toFloat()
                    val scale  = maxOf(scaleX, scaleY)
                    val offX   = (size.width  - w  * scale) / 2f
                    val offY   = (size.height - h * scale) / 2f
                    val imgX = ((tap.x - offX) / scale).toInt()
                    val imgY = ((tap.y - offY) / scale).toInt()
                    currentObjects.value.firstOrNull { it.rect.contains(imgX, imgY) }?.let { currentOnBoxTapped.value(it, tap) }
                }
            },
    ) {
        val scaleX = size.width  / imgW.toFloat()
        val scaleY = size.height / imgH.toFloat()
        val scale  = maxOf(scaleX, scaleY)
        val offX   = (size.width  - imgW  * scale) / 2f
        val offY   = (size.height - imgH * scale) / 2f
        objects.forEach { box ->
            val r = box.rect
            val left   = offX + r.left   * scale
            val top    = offY + r.top    * scale
            val right  = offX + r.right  * scale
            val bottom = offY + r.bottom * scale
            drawRoundRect(
                color        = Teal,
                topLeft      = Offset(left, top),
                size         = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(12f, 12f),
                style        = Stroke(width = 2.5f),
                alpha        = 0.75f,
            )
        }
    }
}

/**
 * Compact anchored result chip for one [ShelfPeek] — pinned at the screen
 * position the user tapped (not continuously re-tracked against the live
 * box, which the object detector could keep moving as the camera pans; a
 * fixed "note placed there" reads clearly enough for a quick glance and
 * avoids a second layer of per-frame position-syncing complexity). Tapping a
 * Ready chip opens the full Result screen; any other tap (Loading/Failed, or
 * Ready's own dismiss) just clears the peek.
 */
@Composable
fun BoxScope.ScanShelfPeekChip(peek: ShelfPeek, onDismiss: () -> Unit, onOpenResult: (Long) -> Unit) {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val chipWidth = 172.dp
    val xDp = with(density) { peek.anchor.x.toDp() } - (chipWidth / 2)
    val yDp = with(density) { peek.anchor.y.toDp() } + 14.dp
    val clampedX = xDp.coerceIn(Spacing.S, (screenWidthDp - chipWidth - Spacing.S).coerceAtLeast(Spacing.S))

    Box(modifier = Modifier.align(Alignment.TopStart).padding(start = clampedX, top = yDp).widthIn(max = chipWidth)) {
        Surface(
            shape = RoundedCornerShape(CardRadius.CONTROL),
            color = SurfaceVariant.copy(0.94f),
            onClick = { if (peek.status is ShelfPeekStatus.Ready) onOpenResult(peek.status.resultId) else onDismiss() },
            modifier = Modifier
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
                .clip(RoundedCornerShape(CardRadius.CONTROL)),
            // design-aesthetic-audit §DH: floats freely over the live camera
            // preview like ScanBarcodeChip/ScanHeaderOverlay, but had none.
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                when (val status = peek.status) {
                    is ShelfPeekStatus.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Teal)
                        Text(stringResource(R.string.scan_shelf_peek_loading), style = MaterialTheme.typography.labelSmall, color = OnSurface)
                    }
                    is ShelfPeekStatus.Ready -> {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(gradeColor(status.grade)), contentAlignment = Alignment.Center) {
                            Text(status.grade.label, style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Text(status.name, style = MaterialTheme.typography.labelSmall, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    is ShelfPeekStatus.Failed -> {
                        Icon(TablerIcons.AlertCircle, null, tint = semanticRed(), modifier = Modifier.size(14.dp))
                        Text(status.message, style = MaterialTheme.typography.labelSmall, color = OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
