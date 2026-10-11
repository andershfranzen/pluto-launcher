package dev.pluto.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.theme.LocalDarkTheme

/** Frosted glass: a light sheen over whatever is behind, brighter at the top. */
private val GlassFill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f)))

/** A hairline rim that catches the light along the top edge and fades out below. */
private val GlassRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.02f)))

/** The known XMB colour lets glass pick up its surroundings without sampling every frame. */
val LocalGlassBackdrop = staticCompositionLocalOf<Color?> { null }

/** Softer tinted surfaces and stronger secondary text over vivid built-in backgrounds. */
@Composable
fun GlassBackdropTheme(backdrop: Color?, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val amount = if (dark) 0.08f else 0.16f
    val tinted = if (backdrop == null) scheme else scheme.copy(
        surfaceContainerHigh = lerp(scheme.surfaceContainerHigh, backdrop, amount),
        surfaceContainer = lerp(scheme.surfaceContainer, backdrop, amount),
        surfaceContainerLow = lerp(scheme.surfaceContainerLow, backdrop, amount),
        surfaceVariant = lerp(scheme.surfaceVariant, backdrop, amount),
        onSurfaceVariant = if (dark) scheme.onSurfaceVariant else lerp(scheme.onSurfaceVariant, scheme.onSurface, 0.65f),
    )
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        MaterialTheme(colorScheme = tinted, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}

/** Denser than dock glass so names, controls and long app lists stay readable. */
@Composable
fun Modifier.glassSurface(shape: Shape, rim: Boolean = true): Modifier {
    val dark = LocalDarkTheme.current
    val backdrop = LocalGlassBackdrop.current
    val neutral = lerp(MaterialTheme.colorScheme.surface, if (dark) Color(0xFF131518) else Color(0xFFF9F8F5), 0.75f)
    val tint = if (backdrop == null) neutral else lerp(neutral, backdrop, if (dark) 0.08f else 0.16f)
    val softened = backdrop != null && !dark
    val fill = Brush.verticalGradient(listOf(
        tint.copy(alpha = if (softened) 0.77f else 0.86f),
        tint.copy(alpha = if (softened) 0.72f else 0.76f),
    ))
    val edge = Brush.verticalGradient(listOf(
        Color.White.copy(alpha = if (dark) 0.20f else 0.24f),
        Color.White.copy(alpha = 0.04f),
    ))
    return clip(shape).background(fill).then(if (rim) Modifier.border(1.dp, edge, shape) else Modifier)
}

/**
 * A rounded glass surface (portrait dock, home menu). Drawing only: the backdrop is not
 * blurred (the system wallpaper is outside the app), so [fill] carries the frosting; pass a
 * denser fill where text has to stay readable.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    fill: Brush = GlassFill,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, GlassRim, shape),
        content = content,
    )
}
