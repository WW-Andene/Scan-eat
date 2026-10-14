package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap A6: grade colours readable (WCAG AA 4.5:1) on every dark theme background. */
class GradeColorContrastTest {
    private val darkBackgrounds = listOf(0xFF000000, 0xFF120F0B, 0xFF0F130B, 0xFF120F16, 0xFF141008, 0xFF0A0F16).map { Color(it) }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble(); val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test fun `every dark palette grade meets 4_5 to 1`() {
        listOf(NormalGradeColors, ProtanDeuteranSafeGradeColors, TritanopiaSafeGradeColors).forEach { palette ->
            palette.forEach { (grade, color) ->
                darkBackgrounds.forEach { bg ->
                    val c = contrast(color, bg)
                    assertTrue("$grade on $bg = $c", c >= 4.5)
                }
            }
        }
    }
}
