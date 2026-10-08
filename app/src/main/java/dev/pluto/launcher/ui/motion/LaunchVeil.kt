package dev.pluto.launcher.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * The hand-off from a tile to a launching app. Android's launch animation from a source
 * rectangle (ActivityOptions.makeScaleUpAnimation) is honoured by AOSP, but OxygenOS 16 (the
 * reference Nord 5) replaces it with its own full-screen fade for third-party launchers, so
 * a tap there showed no connection to the tile. Pluto therefore first grows a veil in its
 * own window from the tile's bounds to the whole window ([LaunchMotion.VEIL_MS]), then
 * starts the activity, whose system transition (whatever the OEM uses) continues from a
 * screen that already "became" the app. The veil is removed once Pluto stops, or after a
 * short timeout if the launch failed and Pluto stayed in front.
 */
@Stable
class LaunchVeilState {
    internal val progress = Animatable(0f)
    internal var origin: Rect? = null
    internal var windowOffset = Offset.Zero

    /** Grows from [originInWindow] to full window, suspending until it covers the window. */
    suspend fun cover(originInWindow: Rect) {
        origin = originInWindow
        progress.snapTo(0f)
        progress.animateTo(1f, tween(LaunchMotion.VEIL_MS, easing = PlutoMotion.EmphasizedAccelerate))
    }

    /** Fades the veil away (launch failed, or Pluto came back without stopping). */
    suspend fun reveal() {
        if (progress.value == 0f) return
        progress.animateTo(0f, PlutoMotion.fadeOut())
        origin = null
    }

    /** Removes the veil at once (Pluto went to the background behind the launched app). */
    suspend fun clear() {
        progress.snapTo(0f)
        origin = null
    }
}

object LaunchMotion {
    /** How long the veil takes to grow from the tile to the full window before the launch. */
    const val VEIL_MS = 110

    /** If Pluto is still in front this long after a launch, the veil is lifted again. */
    const val VEIL_TIMEOUT_MS = 900L
}

/** Draws [state]'s veil in [color] over everything; place it last in the root, filling it. */
@Composable
fun LaunchVeil(state: LaunchVeilState, color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .onPlaced { state.windowOffset = it.positionInWindow() }
            .drawBehind {
                val p = state.progress.value
                val o = state.origin ?: return@drawBehind
                if (p <= 0f) return@drawBehind
                val start = o.translate(-state.windowOffset)
                val left = lerp(start.left, 0f, p)
                val top = lerp(start.top, 0f, p)
                val right = lerp(start.right, size.width, p)
                val bottom = lerp(start.bottom, size.height, p)
                val corner = lerp(16.dp.toPx(), 0f, p)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(corner, corner),
                    alpha = lerp(0.5f, 1f, (p * 2f).coerceAtMost(1f)),
                )
            },
    )
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
