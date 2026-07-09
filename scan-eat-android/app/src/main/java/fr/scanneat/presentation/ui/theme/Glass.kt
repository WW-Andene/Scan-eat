package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A soft top-light catch + hairline edge, applied over an existing card
 * background so it reads as a lit surface rather than a flat fill.
 * No real-time blur — the app's own gradients are the only thing behind it.
 */
fun Modifier.glassSheen(edgeAlpha: Float = 0.28f): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
            endY = size.height * 0.45f,
        ),
    )
    val inset = size.width * 0.14f
    drawLine(
        color = Color.White.copy(alpha = edgeAlpha),
        start = Offset(inset, 0.5f),
        end = Offset(size.width - inset, 0.5f),
        strokeWidth = 1.5f,
    )
}
