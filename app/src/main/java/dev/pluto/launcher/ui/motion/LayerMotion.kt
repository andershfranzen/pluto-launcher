package dev.pluto.launcher.ui.motion

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Motion shapes for layers, all functions of one progress value p (0 = absent, 1 = shown).
 * Pure math so the draw-phase lambdas stay allocation-free and the curves are testable.
 */
object LayerMotion {
    /** Folder: grows from its tile. */
    const val FOLDER_START_SCALE = 0.6f

    /** Sheets / dialogs settle from slightly larger (their full-screen scrim never shrinks). */
    const val SHEET_ENTER_SCALE = 1.06f
    const val SHEET_EXIT_SCALE = 1.03f

    /** Onboarding: subtle scale-up. */
    const val ONBOARDING_START_SCALE = 0.96f

    /** Pushed pages travel this share of the width (capped, see [pageTravel]). */
    const val PAGE_TRAVEL_FRACTION = 0.2f
    const val PAGE_TRAVEL_MAX_DP = 120f

    /** The page beneath a pushed page shifts this share of the width toward the start. */
    const val BENEATH_SHIFT_FRACTION = 0.06f
    const val BENEATH_SHIFT_MAX_DP = 40f

    /** Home alpha with the drawer fully open. */
    const val RECEDE_ALPHA = 0.4f

    /** Extra dim over home with the drawer fully open. */
    const val DRAWER_DIM = 0.32f

    /** Share of the exit a predictive Back swipe scrubs before the user commits. */
    const val BACK_SCRUB = 0.3f

    fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

    private fun unit(value: Float) = value.coerceIn(0f, 1f)

    /** Fade that completes over the first [span] of progress. */
    fun fadeEarly(p: Float, span: Float): Float = unit(p / span)

    /** Fade that only starts after [delay] of progress (shared-axis incoming page). */
    fun fadeLate(p: Float, delay: Float): Float = unit((p - delay) / (1f - delay))

    fun folderScale(p: Float): Float = lerp(FOLDER_START_SCALE, 1f, p)

    fun sheetScale(p: Float, entering: Boolean): Float =
        lerp(if (entering) SHEET_ENTER_SCALE else SHEET_EXIT_SCALE, 1f, unit(p))

    fun onboardingScale(p: Float): Float = lerp(ONBOARDING_START_SCALE, 1f, p)

    /** Drawer panel offset: fully below the window when closed. */
    fun drawerOffset(p: Float, height: Float): Float = (1f - p) * height

    fun recedeScale(drawerProgress: Float): Float = lerp(1f, PlutoMotion.RECEDE_SCALE, unit(drawerProgress))
    fun recedeAlpha(drawerProgress: Float): Float = lerp(1f, RECEDE_ALPHA, unit(drawerProgress))

    /** Alpha of what lies beneath a page that is [coverProgress] of the way in. */
    fun beneathPageAlpha(coverProgress: Float): Float = 1f - unit(coverProgress / 0.65f)

    fun pageTravel(width: Float, density: Float): Float =
        minOf(width * PAGE_TRAVEL_FRACTION, PAGE_TRAVEL_MAX_DP * density)

    fun beneathShift(width: Float, density: Float): Float =
        minOf(width * BENEATH_SHIFT_FRACTION, BENEATH_SHIFT_MAX_DP * density)

    /** Drawer progress for a predictive Back swipe of [backProgress] (0..1). */
    fun backScrub(backProgress: Float): Float = 1f - BACK_SCRUB * unit(backProgress)

    /**
     * Transform origin (fractions of a layer of [size] whose top-left sits at [layerOffset]
     * in the window) at the centre of [origin] (window coordinates); centre when unknown.
     */
    fun originOf(origin: Rect?, layerOffset: Offset, size: Size): TransformOrigin {
        if (origin == null || size.width <= 0f || size.height <= 0f) return TransformOrigin.Center
        val c = origin.center
        return TransformOrigin(
            unit((c.x - layerOffset.x) / size.width),
            unit((c.y - layerOffset.y) / size.height),
        )
    }
}

/**
 * Swallows every pointer event for a layer that is animating out, so a tap during its
 * exit cannot activate it (and, e.g., pop a second layer through its scrim).
 */
fun Modifier.blockPointerInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * A Back dispatcher nobody dispatches to: provided to a leaving layer so its BackHandlers
 * stop intercepting Back while it animates out.
 */
class InertBackDispatcherOwner(private val owner: LifecycleOwner) : OnBackPressedDispatcherOwner {
    override val onBackPressedDispatcher = OnBackPressedDispatcher()
    override val lifecycle: Lifecycle get() = owner.lifecycle
}
