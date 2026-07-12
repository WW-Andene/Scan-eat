package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale — same idea as IconSize.kt, applied to padding/gaps
 * instead of icon sizes. New call sites should reach for one of these
 * instead of another ad hoc *.dp literal; existing call sites are migrated
 * incrementally rather than in one sweep, same rollout IconSize.kt used.
 */
object Spacing {
    val XS: Dp = 4.dp
    val S: Dp = 8.dp
    val M: Dp = 12.dp
    val L: Dp = 16.dp
    val XL: Dp = 24.dp
}
