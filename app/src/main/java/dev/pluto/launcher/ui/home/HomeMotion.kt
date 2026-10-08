package dev.pluto.launcher.ui.home

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.motion.DrawerRevealState
import dev.pluto.launcher.ui.motion.LocalDrawerReveal
import dev.pluto.launcher.ui.motion.PlutoMotion
import kotlinx.coroutines.channels.Channel

/*
 * Motion helpers for the home layouts, the drawer, folders and Handheld mode. Everything
 * transforms in the draw phase (graphicsLayer lambdas reading State) so animations never
 * recompose a grid per frame, and every animation is a spring/Animatable/Transition, so new
 * input retargets it instead of queueing.
 */

/** Share of the window height a finger travels to open the drawer fully. */
private const val DRAWER_TRAVEL_FRACTION = 0.85f

// ---------------------------------------------------------------------------------------
// Drawer reveal gestures
// ---------------------------------------------------------------------------------------

/**
 * Feeds finger movement into [DrawerRevealState] from the UI thread. Pointer and nested
 * scroll callbacks are not suspending, while [DrawerRevealState.dragBy] is, so deltas are
 * summed into a field and a single long-lived coroutine ([run]) applies them; the conflated
 * channel carries only Unit, so dragging allocates nothing per event.
 *
 * [opening]: true for the home swipe-up (an upward drag starts the reveal), false for the
 * drawer's swipe-down to close.
 */
@Stable
internal class RevealDragDriver(private val reveal: DrawerRevealState, private val opening: Boolean) {
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var pendingDelta = 0f
    private var releasePending = false
    private var releaseVelocity = 0f

    var travelPx = 1f
    var onReleased: (Boolean) -> Unit = {}

    /** True while a finger is driving the reveal through this driver. */
    var active = false
        private set

    fun start() {
        if (active) return
        active = true
        releasePending = false
        reveal.beginDrag()
    }

    /** [deltaUp] > 0 moves towards open (finger moving up). */
    fun dragBy(deltaUp: Float) {
        if (!active || deltaUp == 0f) return
        pendingDelta += deltaUp
        signal.trySend(Unit)
    }

    /** [velocityUp] in px/s, positive when the finger moves up. */
    fun release(velocityUp: Float) {
        if (!active) return
        active = false
        releasePending = true
        releaseVelocity = velocityUp
        signal.trySend(Unit)
    }

    /** Pixels left before the drawer is fully open / fully closed, net of unapplied deltas. */
    fun roomUp(): Float = ((1f - reveal.progress) * travelPx - pendingDelta).coerceAtLeast(0f)
    fun roomDown(): Float = (reveal.progress * travelPx + pendingDelta).coerceAtLeast(0f)

    /** The gesture owner went away mid-drag: never leave the reveal stuck in "dragging". */
    fun abandon() {
        if (!active && !releasePending) return
        active = false
        releasePending = false
        pendingDelta = 0f
        reveal.release(0f, travelPx)
    }

    suspend fun run() {
        for (unused in signal) {
            val delta = pendingDelta
            if (delta != 0f) {
                pendingDelta = 0f
                reveal.dragBy(delta, travelPx)
            }
            if (releasePending && pendingDelta == 0f) {
                releasePending = false
                onReleased(reveal.release(releaseVelocity, travelPx))
            }
        }
    }

    /**
     * Nested scroll: a list inside drives the reveal only with drag it could not use itself
     * (upward at the end of the home grid, downward at the top of the drawer grid). Once the
     * reveal is moving, every drag goes to it until the finger lifts, so reversing direction
     * moves the drawer back instead of scrolling the list underneath.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!active || source != NestedScrollSource.UserInput) return Offset.Zero
            return consumeIntoReveal(available.y)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (!active) {
                val wanted = if (opening) available.y < 0f else available.y > 0f
                if (!wanted) return Offset.Zero
                start()
            }
            return consumeIntoReveal(available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!active) return Velocity.Zero
            release(-available.y)
            return available
        }
    }

    /** Applies a scroll delta (down-positive) to the reveal; returns what was consumed. */
    private fun consumeIntoReveal(dy: Float): Offset {
        val taken = if (dy < 0f) -minOf(-dy, roomUp()) else minOf(dy, roomDown())
        if (taken == 0f) return Offset.Zero
        dragBy(-taken)
        return Offset(0f, taken)
    }
}

/**
 * Remembers a [RevealDragDriver] bound to [LocalDrawerReveal]; [onReleased] receives
 * whether the drawer should end up open once the finger lifts.
 */
@Composable
internal fun rememberRevealDragDriver(opening: Boolean, onReleased: (Boolean) -> Unit): RevealDragDriver {
    val reveal = LocalDrawerReveal.current
    val travel = LocalWindowInfo.current.containerSize.height * DRAWER_TRAVEL_FRACTION
    val driver = remember(reveal, opening) { RevealDragDriver(reveal, opening) }
    val currentOnReleased by rememberUpdatedState(onReleased)
    SideEffect {
        driver.travelPx = travel.coerceAtLeast(1f)
        driver.onReleased = { currentOnReleased(it) }
    }
    LaunchedEffect(driver) { driver.run() }
    DisposableEffect(driver) { onDispose { driver.abandon() } }
    return driver
}

/**
 * Drags on areas that do not scroll (header, buttons, dock, empty space) move the drawer
 * continuously in [RevealDragDriver]'s direction; nested lists report their leftover drag
 * through the driver's nested scroll connection. Lists consume their own drags first, so a
 * grid scroll never starts a reveal by itself.
 */
internal fun Modifier.revealDrag(driver: RevealDragDriver, opening: Boolean): Modifier = this
    .nestedScroll(driver.nestedScrollConnection)
    .pointerInput(driver, opening) {
        val tracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var overSlop = 0f
            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                val wanted = if (opening) over < 0f else over > 0f
                if (wanted) {
                    overSlop = over
                    change.consume()
                }
            } ?: return@awaitEachGesture
            tracker.resetTracking()
            tracker.addPointerInputChange(down)
            tracker.addPointerInputChange(drag)
            driver.start()
            driver.dragBy(-overSlop)
            var ended = false
            try {
                ended = verticalDrag(drag.id) { change ->
                    tracker.addPointerInputChange(change)
                    driver.dragBy(-change.positionChange().y)
                    change.consume()
                }
            } finally {
                driver.release(if (ended) -tracker.calculateVelocity().y else 0f)
            }
        }
    }

// ---------------------------------------------------------------------------------------
// Lazy item motion
// ---------------------------------------------------------------------------------------

private val ItemFadeIn: FiniteAnimationSpec<Float> = PlutoMotion.fadeIn()
private val ItemFadeOut: FiniteAnimationSpec<Float> = PlutoMotion.fadeOut()
private val ItemPlacement: FiniteAnimationSpec<IntOffset> = PlutoMotion.slideSpring

/** Pin/unpin/reorder/install/uninstall and filtering animate placement and fade in grids. */
internal fun LazyGridItemScope.plutoItem(): Modifier =
    Modifier.animateItem(fadeInSpec = ItemFadeIn, placementSpec = ItemPlacement, fadeOutSpec = ItemFadeOut)

/** Same for rows (Handheld Favourites / Recent launches). */
internal fun LazyItemScope.plutoItem(): Modifier =
    Modifier.animateItem(fadeInSpec = ItemFadeIn, placementSpec = ItemPlacement, fadeOutSpec = ItemFadeOut)

// ---------------------------------------------------------------------------------------
// Staggered entrance
// ---------------------------------------------------------------------------------------

/**
 * A cheap staggered entrance: one linear clock, each slot (a row, a section, a tile) fades
 * and rises a few dp a little after the previous one. Slots past [maxSlots] enter with the
 * last one. Reduce motion makes the clock jump to its end, so everything is simply shown.
 */
@Stable
internal class Stagger(
    private val startDelayMs: Int,
    private val stepMs: Int,
    private val durationMs: Int,
    private val maxSlots: Int,
    animate: Boolean,
) {
    private val totalMs = (startDelayMs + stepMs * (maxSlots - 1) + durationMs).toFloat()
    private val clock = Animatable(if (animate) 0f else totalMs)

    suspend fun run() {
        if (clock.value < totalMs) clock.animateTo(totalMs, tween(totalMs.toInt(), easing = LinearEasing))
    }

    /** 0 (not yet shown) .. 1 (in place) for [slot]; reads State, so call it from a draw-phase lambda. */
    fun progress(slot: Int): Float {
        val now = clock.value
        if (now >= totalMs) return 1f
        val s = slot.coerceIn(0, maxSlots - 1)
        val t = ((now - startDelayMs - s * stepMs) / durationMs).coerceIn(0f, 1f)
        return PlutoMotion.EmphasizedDecelerate.transform(t)
    }
}

@Composable
internal fun rememberStagger(
    animate: Boolean,
    startDelayMs: Int = 0,
    stepMs: Int = 28,
    durationMs: Int = PlutoMotion.MEDIUM_MS,
    maxSlots: Int = 5,
): Stagger {
    val stagger = remember { Stagger(startDelayMs, stepMs, durationMs, maxSlots, animate) }
    LaunchedEffect(stagger) { stagger.run() }
    return stagger
}

/** Fades [slot] in and lifts it [riseDp] into place as [stagger] advances (visual only). */
internal fun Modifier.staggered(stagger: Stagger, slot: Int, riseDp: Float = 12f): Modifier =
    graphicsLayer {
        val p = stagger.progress(slot)
        if (p < 1f) {
            alpha = p
            translationY = (1f - p) * riseDp * density
        } else {
            alpha = 1f
            translationY = 0f
        }
    }

/** Content that fades in once when it first appears (e.g. an empty state). */
@Composable
internal fun Modifier.fadeInOnAppear(): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(alpha) { alpha.animateTo(1f, PlutoMotion.fadeIn()) }
    return graphicsLayer { this.alpha = alpha.value }
}

// ---------------------------------------------------------------------------------------
// Category paging
// ---------------------------------------------------------------------------------------

/**
 * Direction-aware page slide for category switches: moving to a later category slides the
 * old content left and brings the new one in from the right (a quarter width, with a fade).
 * Springs keep rapid L1/R1 presses continuous: each new target simply retargets.
 */
internal fun <S> AnimatedContentTransitionScope<S>.categorySlide(direction: Int): ContentTransform {
    val dir = if (direction < 0) -1 else 1
    return (
        slideInHorizontally(PlutoMotion.slideSpring) { it * dir / 4 } + fadeIn(PlutoMotion.fadeIn())
        ) togetherWith (
        slideOutHorizontally(PlutoMotion.slideSpring) { -it * dir / 4 } + fadeOut(PlutoMotion.fadeOut())
        ) using SizeTransform(clip = false)
}

// ---------------------------------------------------------------------------------------
// Carousel scrolling
// ---------------------------------------------------------------------------------------

/**
 * Bring-into-view for console-style rows: the focused tile keeps about one tile of
 * look-ahead on the side it is moving towards, instead of hugging the edge. The scrolling
 * container animates and retargets each request smoothly, so a held D-pad keeps up.
 */
@OptIn(ExperimentalFoundationApi::class)
internal object CarouselBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val lead = minOf(size, ((containerSize - size) / 2f).coerceAtLeast(0f))
        val start = offset - lead
        val end = offset + size + lead
        return when {
            start < 0f && end > containerSize -> 0f
            start < 0f -> start
            end > containerSize -> end - containerSize
            else -> 0f
        }
    }
}

/** How far the "All apps" handle lifts as the drawer is pulled up. */
internal val HandleLift = 14.dp

/** Dock icons land with a little bounce from this scale when a slot is filled. */
internal const val DOCK_LANDING_SCALE = 0.5f

@Composable
internal fun rememberDensityPx(dp: androidx.compose.ui.unit.Dp): Float = with(LocalDensity.current) { dp.toPx() }
