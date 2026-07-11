package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The app's one primary-button recipe — AccentGreen fill, 12dp corners,
 * black semibold label — previously hand-copied across ~8 files with
 * visible drift (two call sites silently reverted to Material's default
 * pill shape). The Part B4/B10 Layer-3 fix: the accent color and shape now
 * only need to change here, not at every call site.
 */
@Composable
fun ScanEatPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape   = RoundedCornerShape(12.dp),
        colors  = ButtonDefaults.buttonColors(containerColor = AccentGreen),
    ) {
        ProvideTextStyle(LocalTextStyle.current.copy(color = Color.Black, fontWeight = FontWeight.SemiBold)) {
            content()
        }
    }
}
