package dev.pluto.launcher.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shared, continuous position of the app drawer: 0 = closed (home fully visible),
 * 1 = open. The drawer's on-screen offset, the home surface's recede and the scrim are
 * all driven from [progress], whatever started the motion (swipe, button, controller Y,
 * Back, Home).
 *
 * Ownership:
 * - Gesture owners (home swipe-up, drawer swipe-down at list top) call [beginDrag],
 *   [dragBy] and finally [release], then tell the ViewModel the outcome
 *   (vm.openDrawer() / vm.back()) when [release] returns a different open state.
 * - LauncherRoot observes whether Layer.Drawer is in the stack and calls [settle] to
 *   animate to 0 or 1, reusing the fling velocity captured by [release]. It keeps the
 *   drawer composed while [isVisible], so closing animates out before it disappears.
 */
@Stable
class DrawerRevealState(initiallyOpen: Boolean = false) {
    private val anim = Animatable(if (initiallyOpen) 1f else 0f)

    val progress: Float get() = anim.value

    /** True while a finger is moving the drawer. */
    var isDragging by mutableStateOf(false)
        private set

    /** Drawer should be composed (open, opening, closing, or being dragged). */
    val isVisible: Boolean get() = isDragging || anim.value > 0f || anim.isRunning

    private var pendingVelocity = 0f

    /**
     * Where the last [release] decided the drawer should go, until the next [settle],
     * [snap] or [beginDrag]. Lets LauncherRoot head there at once, before the ViewModel's
     * layer stack (which follows asynchronously) reflects the gesture's outcome.
     */
    var releaseTarget: Boolean? = null
        private set

    fun beginDrag() {
        isDragging = true
        pendingVelocity = 0f
        releaseTarget = null
    }

    /**
     * Moves by [deltaPx] of a [travelPx]-tall travel distance. Positive delta opens
     * (finger moving up). Clamped to 0..1.
     */
    suspend fun dragBy(deltaPx: Float, travelPx: Float) {
        if (travelPx <= 0f) return
        anim.snapTo((anim.value + deltaPx / travelPx).coerceIn(0f, 1f))
    }

    /**
     * Ends a drag. [velocityPxPerSec] is positive when the finger moves up (opening).
     * Returns whether the drawer should end up open; the caller syncs the ViewModel, and
     * LauncherRoot's [settle] animates there carrying this velocity.
     */
    fun release(velocityPxPerSec: Float, travelPx: Float): Boolean {
        isDragging = false
        pendingVelocity = if (travelPx > 0f) velocityPxPerSec / travelPx else 0f
        val open = when {
            pendingVelocity > FLING_THRESHOLD -> true
            pendingVelocity < -FLING_THRESHOLD -> false
            else -> anim.value >= 0.5f
        }
        releaseTarget = open
        return open
    }

    /**
     * Ends a drag whose outcome is already decided (e.g. a predictive Back gesture that
     * committed or was cancelled), without fling velocity.
     */
    fun endDrag(open: Boolean) {
        isDragging = false
        pendingVelocity = 0f
        releaseTarget = open
    }

    /** Animates to open/closed (called by LauncherRoot when the Drawer layer appears or goes). */
    suspend fun settle(open: Boolean) {
        if (isDragging) return
        // A fresh fling velocity wins; otherwise keep the current motion's velocity so a
        // re-settle (interrupted or retargeted) continues smoothly instead of stopping dead.
        val velocity = if (pendingVelocity != 0f) pendingVelocity else anim.velocity
        pendingVelocity = 0f
        releaseTarget = null
        val target = if (open) 1f else 0f
        if (anim.value == target && !anim.isRunning) return
        anim.animateTo(target, PlutoMotion.spatial(), initialVelocity = velocity)
    }

    /** Jumps without animation (e.g. restoring an open drawer after process death). */
    suspend fun snap(open: Boolean) {
        pendingVelocity = 0f
        releaseTarget = null
        anim.snapTo(if (open) 1f else 0f)
    }

    private companion object {
        /** Drawer heights per second needed to count as a fling. */
        const val FLING_THRESHOLD = 0.6f
    }
}

val LocalDrawerReveal = staticCompositionLocalOf<DrawerRevealState> { error("DrawerRevealState not provided") }
