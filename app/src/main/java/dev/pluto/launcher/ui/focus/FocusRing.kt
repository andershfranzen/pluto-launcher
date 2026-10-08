package dev.pluto.launcher.ui.focus

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.motion.PlutoMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/*
 * Controller focus ring visuals.
 *
 * Two presentations share the same drawing ([drawFocusRing]):
 *
 * 1. Per-control ring (default). Every controllerFocusable draws its own ring just outside
 *    its bounds. On focus gain the ring fades in while shrinking from [RING_APPEAR_SCALE]
 *    to 1 (spring PlutoMotion.spatialFast) with a soft glow; on focus loss, or when touch
 *    takes over, it fades out quickly. Because the ring is part of the control's own draw
 *    pass it follows the control exactly (scrolling, reflow, press scale, layer motion) and
 *    is clipped exactly like the control (lazy rows, panels, layers never show a ring for a
 *    control that is hidden behind them). This is the robust default.
 *
 * 2. Gliding overlay (opt-in). A single ring drawn above the content glides from the
 *    previous control's bounds to the newly focused one, like a console home screen. The
 *    ring is positioned relative to the focused control's live coordinates every frame, and
 *    only the *difference* to the previous control is animated, so while the focused control
 *    itself moves (scrolling, reflow) the ring follows it with no lag.
 *
 *    To enable it, the root wraps its content (inside ProvideControllerFocus) in
 *    [FocusRingOverlayHost]:
 *
 *        ProvideControllerFocus(focus) {
 *            FocusRingOverlayHost {
 *                ...launcher content...
 *            }
 *        }
 *
 *    Controls in the same window then stop drawing their own ring (they keep their tonal
 *    focus tint) and hand their coordinates to the overlay. Controls in another window
 *    (dialogs) keep the per-control ring automatically. Caveat: the overlay is not clipped by
 *    scrolling containers, so a focused control partly scrolled under a header shows its
 *    full ring above that header; focused controls are brought fully into view, so this is
 *    momentary. That trade-off is why the overlay is opt-in.
 */

/** Outer (dark) and inner (light) focus ring colours; the pair reads on any wallpaper. */
internal val FocusRingDark = Color(0xFF070A18)
internal val FocusRingLight = Color(0xFFFFFFFF)
internal val FocusRingInnerWidth = 3.dp
internal val FocusRingOuterWidth = 2.dp

/** The ring starts this much larger than its control when it appears, then settles to 1. */
internal const val RING_APPEAR_SCALE = 1.08f

/** Soft glow strokes outside the dark ring: (extra distance in dp, alpha). */
private val GlowSteps = floatArrayOf(2f, 0.22f, 4.5f, 0.12f, 7.5f, 0.05f)

/**
 * Draws the dual focus ring (light inner, dark outer) plus a soft glow around a [size]-sized
 * element whose top-left is at [topLeft], expanded by [growX]/[growY] on each side (scale-in).
 */
internal fun DrawScope.drawFocusRing(
    shape: Shape,
    topLeft: Offset,
    size: Size,
    alpha: Float,
    glow: Color,
    growX: Float = 0f,
    growY: Float = 0f,
) {
    if (alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    val inner = FocusRingInnerWidth.toPx()
    val outer = FocusRingOuterWidth.toPx()
    var i = 0
    while (i < GlowSteps.size) {
        // Each glow band lies entirely outside the dark ring, never over the control.
        val width = GlowSteps[i].dp.toPx()
        val glowAlpha = GlowSteps[i + 1] * alpha
        val base = inner + outer + width / 2f
        ringAt(shape, topLeft, size, growX + base, growY + base, width, glow.copy(alpha = glowAlpha))
        i += 2
    }
    ringAt(shape, topLeft, size, growX + inner / 2f, growY + inner / 2f, inner, FocusRingLight.copy(alpha = alpha))
    ringAt(shape, topLeft, size, growX + inner + outer / 2f, growY + inner + outer / 2f, outer, FocusRingDark.copy(alpha = alpha))
}

private fun DrawScope.ringAt(shape: Shape, topLeft: Offset, size: Size, expandX: Float, expandY: Float, width: Float, color: Color) {
    val ringSize = Size(size.width + expandX * 2f, size.height + expandY * 2f)
    val outline = shape.createOutline(ringSize, layoutDirection, this)
    translate(left = topLeft.x - expandX, top = topLeft.y - expandY) {
        drawOutline(outline, color, style = Stroke(width))
    }
}

/**
 * Animated visibility of one control's focus indication: [alpha] fades, [scale] settles
 * from [RING_APPEAR_SCALE] to 1 when the ring appears. Read the values in draw only.
 */
@Stable
internal class FocusRingAnimation {
    val alpha = Animatable(0f)
    val scale = Animatable(1f)

    /** Animates towards shown/hidden. Interruptible: a newer call cancels the running one. */
    suspend fun animateTo(show: Boolean) = coroutineScope {
        if (show) {
            if (alpha.value < 0.05f) scale.snapTo(RING_APPEAR_SCALE)
            launch { scale.animateTo(1f, PlutoMotion.spatialFast()) }
            launch { alpha.animateTo(1f, PlutoMotion.fadeIn()) }
        } else {
            launch { alpha.animateTo(0f, PlutoMotion.fadeOut()) }
        }
    }
}

// --- Gliding overlay ---------------------------------------------------------------------

/** What a focused control hands to the overlay: its live coordinates and ring shape. */
internal class FocusRingTarget(val shape: Shape, val visibility: () -> Float = { 1f }) {
    var coordinates: LayoutCoordinates? = null
}

/**
 * How visible the surface holding a control is (0..1), read in draw. Layers that move in the
 * draw phase only (their layout already sits at rest) provide their entry progress, so the
 * gliding ring fades in as the layer settles instead of waiting at the final position.
 */
val LocalFocusRingVisibility = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * State of the opt-in gliding focus ring overlay of one window. Create it with
 * [FocusRingOverlayHost] (or [rememberFocusRingOverlayState] + [FocusRingOverlay]).
 */
@Stable
class FocusRingOverlayState internal constructor(internal val view: View) {
    internal var target by mutableStateOf<FocusRingTarget?>(null)

    /** Bumped whenever the current target is repositioned, so the overlay redraws. */
    internal var moves by mutableIntStateOf(0)

    internal fun show(t: FocusRingTarget) {
        target = t
    }

    internal fun hide(t: FocusRingTarget) {
        if (target === t) target = null
    }

    internal fun moved(t: FocusRingTarget) {
        if (target === t) moves++
    }
}

/**
 * Non-null where focus rings are drawn by a gliding overlay instead of per control.
 * Default null: every control draws its own animated ring.
 */
val LocalFocusRingOverlay = staticCompositionLocalOf<FocusRingOverlayState?> { null }

@Composable
fun rememberFocusRingOverlayState(): FocusRingOverlayState {
    val view = LocalView.current
    return remember(view) { FocusRingOverlayState(view) }
}

/**
 * Wraps [content] so controllerFocusable controls inside it use a single gliding focus ring
 * drawn above [content]. Place it inside ProvideControllerFocus at the root.
 */
@Composable
fun FocusRingOverlayHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberFocusRingOverlayState()
    CompositionLocalProvider(LocalFocusRingOverlay provides state) {
        Box(modifier, propagateMinConstraints = true) {
            content()
            FocusRingOverlay(state, Modifier.matchParentSize())
        }
    }
}

/**
 * The gliding ring itself. Must fill the area it overlays and be drawn above the content;
 * [LocalFocusRingOverlay] must provide [state] to the content.
 */
@Composable
fun FocusRingOverlay(state: FocusRingOverlayState, modifier: Modifier = Modifier) {
    val glow = MaterialTheme.colorScheme.primary
    val holder = remember { OverlayHolder() }
    val alpha = remember { Animatable(0f) }
    // Offset of the drawn ring from the target's real bounds; animated to zero.
    val delta = remember { Animatable(Rect.Zero, Rect.VectorConverter) }

    LaunchedEffect(state) {
        snapshotFlow { state.target }.collectLatest { target ->
            coroutineScope {
                if (target == null) {
                    alpha.animateTo(0f, PlutoMotion.fadeOut())
                    return@coroutineScope
                }
                val bounds = holder.boundsOf(target)
                val last = holder.lastDrawn
                val startDelta = when {
                    bounds == null -> Rect.Zero
                    last == null || alpha.value < 0.05f -> {
                        // Appearing: settle from slightly larger, like the per-control ring.
                        val gx = bounds.width * (RING_APPEAR_SCALE - 1f) / 2f
                        val gy = bounds.height * (RING_APPEAR_SCALE - 1f) / 2f
                        Rect(-gx, -gy, gx, gy)
                    }
                    else -> Rect(
                        last.left - bounds.left,
                        last.top - bounds.top,
                        last.right - bounds.right,
                        last.bottom - bounds.bottom,
                    )
                }
                launch {
                    // Keep the ring's current velocity so retargeting mid-glide stays continuous.
                    val velocity = delta.velocity
                    delta.snapTo(startDelta)
                    delta.animateTo(Rect.Zero, PlutoMotion.spatialFast(), initialVelocity = velocity)
                }
                launch { alpha.animateTo(1f, PlutoMotion.fadeIn()) }
            }
        }
    }

    Spacer(
        modifier
            .clearAndSetSemantics { }
            .onPlaced { holder.own = it }
            .drawBehind {
                @Suppress("UNUSED_EXPRESSION")
                state.moves // Observed: redraw when the target moves.
                val a = alpha.value
                val target = state.target
                if (target != null) holder.shape = target.shape
                val bounds = target?.let(holder::boundsOf) ?: holder.lastBase
                if (target != null && bounds != null) holder.lastBase = bounds
                val shape = holder.shape
                if (a <= 0f || bounds == null || shape == null) {
                    holder.lastDrawn = null
                    return@drawBehind
                }
                val d = delta.value
                val drawn = Rect(bounds.left + d.left, bounds.top + d.top, bounds.right + d.right, bounds.bottom + d.bottom)
                holder.lastDrawn = drawn
                val visible = a * (target?.visibility?.invoke() ?: 1f)
                // Clip like the control itself is clipped (a tile scrolled half under a header
                // shows half a ring): sides cut by a scrolling container clip the ring there.
                val clip = target?.let(holder::clippedBoundsOf)
                if (clip != null && clip.isEmpty) return@drawBehind
                if (clip == null || clip == bounds) {
                    drawFocusRing(shape, drawn.topLeft, drawn.size, visible, glow)
                } else {
                    clipRect(
                        left = if (clip.left > bounds.left + 0.5f) clip.left else -Float.MAX_VALUE / 4,
                        top = if (clip.top > bounds.top + 0.5f) clip.top else -Float.MAX_VALUE / 4,
                        right = if (clip.right < bounds.right - 0.5f) clip.right else Float.MAX_VALUE / 4,
                        bottom = if (clip.bottom < bounds.bottom - 0.5f) clip.bottom else Float.MAX_VALUE / 4,
                    ) {
                        drawFocusRing(shape, drawn.topLeft, drawn.size, visible, glow)
                    }
                }
            },
    )
}

private class OverlayHolder {
    var own: LayoutCoordinates? = null
    var lastDrawn: Rect? = null
    var lastBase: Rect? = null
    var shape: Shape? = null

    /** The target's bounds after clipping by its ancestors (scrolling lists, panels). */
    fun clippedBoundsOf(target: FocusRingTarget): Rect? {
        val self = own?.takeIf { it.isAttached } ?: return null
        val coords = target.coordinates?.takeIf { it.isAttached } ?: return null
        return try {
            self.localBoundingBoxOf(coords, clipBounds = true)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun boundsOf(target: FocusRingTarget): Rect? {
        val self = own?.takeIf { it.isAttached } ?: return null
        val coords = target.coordinates?.takeIf { it.isAttached } ?: return null
        return try {
            self.localBoundingBoxOf(coords, clipBounds = false)
        } catch (e: IllegalArgumentException) {
            // Different layout hierarchies (should not happen: same-window check).
            null
        }
    }
}
