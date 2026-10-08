package dev.pluto.launcher.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Pluto's motion vocabulary. Every animation in the launcher uses these values so screens
 * feel like one product. Reduce motion needs no special casing: the window's
 * MotionDurationScale is 0 then, and Compose jumps animations straight to their end.
 *
 * Springs are used for anything spatial (position, size, scale) so interrupted or
 * gesture-driven motion stays continuous; tweens only for pure fades/colour.
 */
object PlutoMotion {
    // Material 3 easing curves.
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT_MS = 120
    const val MEDIUM_MS = 220
    const val LONG_MS = 360

    /**
     * Search results swap in place: outgoing results fade out over this, incoming ones fade in
     * after it, so two icons never share a cell and nothing flies across the grid.
     */
    const val SWAP_MS = 70

    /** Layers, pages, sheets: slightly soft, no visible overshoot. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 420f)

    /** Small spatial changes: focus ring glide, tab indicator, press feedback. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 900f)

    /** A touch of bounce for playful, small elements (dock icon landing, badges). */
    fun <T> spatialBouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 600f)

    /** IntOffset springs for AnimatedContent / AnimatedVisibility slides. */
    val slideSpring: FiniteAnimationSpec<IntOffset> =
        spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = IntOffset(1, 1))

    fun <T> fadeIn(): FiniteAnimationSpec<T> = tween(MEDIUM_MS, easing = EmphasizedDecelerate)
    fun <T> fadeOut(): FiniteAnimationSpec<T> = tween(SHORT_MS, easing = EmphasizedAccelerate)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

    /** Pressed tiles shrink to this scale (visual only; layout geometry never changes). */
    const val PRESSED_SCALE = 0.93f

    /** How far the home surface recedes while the drawer covers it. */
    const val RECEDE_SCALE = 0.94f
}
