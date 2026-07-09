package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A soft top-light catch + hairline edge, applied over an existing card
 * background so it reads as a lit surface rather than a flat fill.
 * No real-time blur — the app's own gradients are the only thing behind it.
 * Clips to [shape] so the overlay never peeks past a rounded corner — pass
 * the same shape used by the card's own Surface/background underneath.
 */
fun Modifier.glassSheen(edgeAlpha: Float = 0.28f, shape: Shape = RoundedCornerShape(16.dp)): Modifier = this
    .clip(shape)
    .drawWithContent {
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
