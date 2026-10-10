package dev.pluto.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Frosted glass: a light sheen over whatever is behind, brighter at the top. */
private val GlassFill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f)))

/** A hairline rim that catches the light along the top edge and fades out below. */
private val GlassRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.02f)))

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
