package fr.scanneat.presentation.scan.components

import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.minTouchTarget

@Composable
internal fun BoxScope.ScanPhotoQueue(images: List<ImagePayload>, topInset: Dp, onRemovePhoto: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = topInset + 88.dp)
            .padding(horizontal = Spacing.L),
    ) {
        Box(Modifier.glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(10.dp))) {
            Surface(shape = RoundedCornerShape(10.dp), color = Background.copy(0.7f), shadowElevation = 0.dp, modifier = Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(10.dp), ambientColor = ShadowTint, spotColor = ShadowTint).clip(RoundedCornerShape(10.dp))) {
                Column(Modifier.padding(horizontal = Spacing.SM, vertical = 6.dp)) {
                    Text(pluralStringResource(R.plurals.scan_photo_count, images.size, images.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.8f))
                    Spacer(Modifier.height(Spacing.S))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        // Without a key, removing a photo from the middle of the queue
                        // (onRemovePhoto) shifts every later index, so Compose recomposes
                        // every item after it and can lose their remembered thumbnail
                        // state instead of just removing the one item. `images` itself
                        // is small (capped at MAX_QUEUED_PHOTOS) and each ImagePayload
                        // is structurally unique per captured photo, so its base64 is a
                        // stable, sufficient key - NOT the ImagePayload itself: Compose's
                        // lazy-list key must be a saveable type (String/primitive/
                        // Parcelable/Serializable, for scroll-state restoration), and a
                        // plain data class fails that check with
                        // "IllegalArgumentException: Type of the key ... is not
                        // supported" the moment this row ever renders - i.e. on every
                        // single photo capture.
                        itemsIndexed(images, key = { _, payload -> payload.base64 }) { index, payload ->
                            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                .border(1.dp, OnSurface.copy(0.2f), RoundedCornerShape(8.dp))) {
                                val bmp = remember(payload) { payload.thumbnail }
                                if (bmp != null) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = stringResource(R.string.scan_photo_index, index + 1),
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Box(Modifier.fillMaxSize().background(SurfaceVariant), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Image, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Inline))
                                    }
                                }
                                // IconButton's own footprint kept at minTouchTarget()'s 48dp floor for
                                // a real tap target (previously a fixed 20dp, well under the WCAG/
                                // Material minimum) - the visible circle/glyph stay small via inner
                                // Modifier.size() calls so the 64dp thumbnail isn't visually overrun.
                                IconButton(onClick = { onRemovePhoto(index) },
                                    modifier = Modifier.align(Alignment.TopEnd).minTouchTarget()) {
                                    Box(Modifier.size(20.dp).background(Background.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(TablerIcons.X, stringResource(R.string.common_remove), tint = OnSurface, modifier = Modifier.size(IconSize.Micro))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
