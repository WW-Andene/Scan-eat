package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The app's one card primitive — glassSheen() top-light + hairline edge over
 * a flat SurfaceVariant fill, 16dp corners by default. Generalizes the
 * pattern BioCard() already proved out, so a hand-rolled `Surface(...)`
 * doesn't need to be re-derived (and its glassSheen/radius drifted) on every
 * new screen. glassSheen is on by default — the app's one distinctive
 * surface treatment should be the default, not a per-screen coin flip.
 */
@Composable
fun ScanEatCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardRadius.CARD),
    color: Color = SurfaceVariant,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = shape)) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = color) {
            Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
        }
    }
}
