package dev.pluto.launcher.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Horizontal safe-drawing padding, the larger side applied to both: a camera cutout or
 * system bar on one edge (landscape) would otherwise leave that side with far more margin
 * than the other. On phones without side insets this is no padding at all.
 */
@Composable
fun Modifier.symmetricHorizontalSafeDrawing(): Modifier {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val side = with(density) { maxOf(insets.getLeft(density, direction), insets.getRight(density, direction)).toDp() }
    return padding(horizontal = side)
}
