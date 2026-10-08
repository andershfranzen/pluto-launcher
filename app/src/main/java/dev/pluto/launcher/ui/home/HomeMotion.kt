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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
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

    // Nested-scroll drags: our own velocity estimate, because a list's fling velocity can
    // arrive as zero when input was delayed (its tracker then treats the pointer as stopped).
    private val nestedTracker = VelocityTracker1D(isDataDifferential = true)

    // Last few nested deltas, for when input arrives too sparsely for either tracker (both
    // read widely spaced samples as a stopped finger) but the finger was clearly moving.
    private val sampleTimes = LongArray(SAMPLES)
    private val sampleDeltas = FloatArray(SAMPLES)
    private var sampleCount = 0

    private fun recordSample(time: Long, dy: Float) {
        val i = sampleCount % SAMPLES
        sampleTimes[i] = time
        sampleDeltas[i] = dy
        sampleCount++
    }

    /** Average upward speed over the recent samples, or 0 if the finger had paused. */
    private fun recentAverageVelocityUp(): Float {
        val n = minOf(sampleCount, SAMPLES)
        if (n < 2) return 0f
        val newest = sampleTimes[(sampleCount - 1) % SAMPLES]
        if (android.os.SystemClock.uptimeMillis() - newest > PAUSE_MS) return 0f
        val oldestIndex = (sampleCount - n) % SAMPLES
        val span = (newest - sampleTimes[oldestIndex]).coerceAtLeast(1L)
        var sum = 0f
        // The oldest sample's delta happened before its timestamp's interval starts.
        for (k in 1 until n) sum += sampleDeltas[(sampleCount - n + k) % SAMPLES]
        return -sum * 1000f / span
    }

    private companion object {
        const val SAMPLES = 4
        const val PAUSE_MS = 100L
    }

    var travelPx = 1f
    var onReleased: (Boolean) -> Unit = {}

    /** True while a finger is driving the reveal through this driver. */
    var active = false
        private set

    fun start() {
        if (active) return
        active = true
        releasePending = false
        nestedTracker.resetTracking()
        sampleCount = 0
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
            var own = -nestedTracker.calculateVelocity()
            if (own == 0f && available.y == 0f) own = recentAverageVelocityUp()
            val list = -available.y
            // Prefer whichever estimate is stronger; both are positive when moving up.
            release(if (kotlin.math.abs(own) > kotlin.math.abs(list)) own else list)
            return available
        }
    }

    /** Applies a scroll delta (down-positive) to the reveal; returns what was consumed. */
    private fun consumeIntoReveal(dy: Float): Offset {
        val taken = if (dy < 0f) -minOf(-dy, roomUp()) else minOf(dy, roomDown())
        if (taken == 0f) return Offset.Zero
        val now = android.os.SystemClock.uptimeMillis()
        nestedTracker.addDataPoint(now, dy)
        recordSample(now, dy)
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
            // Follows the pointer by absolute position rather than per-event deltas, and
            // includes the final (up) event: when the main thread stalls (e.g. the drawer's
            // first composition), the remaining movement can arrive merged with the up event,
            // which verticalDrag never reports, so a quick swipe used to lose most of its travel.
            var lastY = drag.position.y
            var ended = false
            var upMoved = false
            var upTime = drag.uptimeMillis
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == drag.id } ?: break
                    val dy = change.position.y - lastY
                    // Velocity from moves only (as verticalDrag does): an up event can arrive late
                    // after a stall, and the tracker would then read the finger as stopped.
                    if (change.pressed) tracker.addPointerInputChange(change)
                    lastY = change.position.y
                    if (dy != 0f) driver.dragBy(-dy)
                    change.consume()
                    if (!change.pressed) {
                        ended = true
                        upMoved = dy != 0f
                        upTime = change.uptimeMillis
                        break
                    }
                }
            } finally {
                var velocityUp = if (ended) -tracker.calculateVelocity().y else 0f
                if (ended && upMoved && velocityUp == 0f) {
                    // The finger was still moving when it lifted, but the tracker saw too few
                    // samples (stalled frame): fall back to the gesture's average speed.
                    val dt = (upTime - down.uptimeMillis).coerceAtLeast(1L)
                    velocityUp = (down.position.y - lastY) * 1000f / dt
                }
                driver.release(velocityUp)
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

private val SearchFadeIn: FiniteAnimationSpec<Float> =
    tween(PlutoMotion.MEDIUM_MS, delayMillis = PlutoMotion.SWAP_MS, easing = PlutoMotion.Standard)
private val SearchFadeOut: FiniteAnimationSpec<Float> = tween(PlutoMotion.SWAP_MS, easing = PlutoMotion.EmphasizedAccelerate)

/**
 * Drawer results while searching: a big result change (first character, Clear) would send
 * placement springs on long diagonal paths across each other, so results swap in place
 * instead: the old ones fade out, then the new ones fade in.
 */
internal fun LazyGridItemScope.searchResultItem(): Modifier =
    Modifier.animateItem(fadeInSpec = SearchFadeIn, placementSpec = null, fadeOutSpec = SearchFadeOut)

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

    /**
     * Set during composition by [arm] so the very next frame already draws every slot hidden,
     * before the effect that replays the clock has run (plain field: read only in draw).
     */
    private var armed = false

    suspend fun run() {
        if (clock.value < totalMs) clock.animateTo(totalMs, tween(totalMs.toInt(), easing = LinearEasing))
    }

    /**
     * Plays the entrance again (for content that stays composed while hidden, such as the
     * parked drawer). Call from composition; [replay] then runs the clock.
     */
    fun arm() {
        armed = true
    }

    suspend fun replay() {
        if (!armed) return
        clock.snapTo(0f)
        armed = false
        run()
    }

    /** 0 (not yet shown) .. 1 (in place) for [slot]; reads State, so call it from a draw-phase lambda. */
    fun progress(slot: Int): Float {
        if (armed) return 0f
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

/**
 * Fades [slot] in and lifts it [riseDp] into place as [stagger] advances (visual only).
 * A modifier node with value equality, so recomposing a tile's parent (a lazy item, the
 * drawer opening) hands the tile an equal modifier and the tile itself can skip.
 */
internal fun Modifier.staggered(stagger: Stagger, slot: Int, riseDp: Float = 12f): Modifier =
    this then StaggerElement(stagger, slot, riseDp)

private data class StaggerElement(val stagger: Stagger, val slot: Int, val riseDp: Float) :
    ModifierNodeElement<StaggerNode>() {
    override fun create() = StaggerNode(stagger, slot, riseDp)
    override fun update(node: StaggerNode) {
        node.stagger = stagger
        node.slot = slot
        node.riseDp = riseDp
        node.invalidatePlacement()
        node.invalidateDraw()
    }
}

/**
 * Alpha through a layer (not positional), the rise as a draw-time translation: positional
 * layer changes make Compose recompute the window bounds of the whole tile on every frame.
 */
private class StaggerNode(var stagger: Stagger, var slot: Int, var riseDp: Float) :
    Modifier.Node(), LayoutModifierNode, DrawModifierNode {
    private val layer: GraphicsLayerScope.() -> Unit = { alpha = stagger.progress(slot) }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layer) }
    }

    override fun ContentDrawScope.draw() {
        val p = stagger.progress(slot)
        if (p >= 1f) {
            drawContent()
        } else {
            translate(top = (1f - p) * riseDp * density) { this@draw.drawContent() }
        }
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

private val PageFadeIn: FiniteAnimationSpec<Float> =
    tween(PlutoMotion.MEDIUM_MS, delayMillis = PlutoMotion.SWAP_MS, easing = PlutoMotion.EmphasizedDecelerate)
private val PageFadeOut: FiniteAnimationSpec<Float> = tween(PlutoMotion.SWAP_MS, easing = PlutoMotion.EmphasizedAccelerate)

/**
 * Direction-aware page slide for category switches: moving to a later category slides the
 * old content left and brings the new one in from the right (a quarter width, with a fade).
 * Springs keep rapid L1/R1 presses continuous: each new target simply retargets.
 */
internal fun <S> AnimatedContentTransitionScope<S>.categorySlide(direction: Int): ContentTransform {
    val dir = if (direction < 0) -1 else 1
    // The outgoing page is gone (SWAP_MS) before the incoming one fades in, so the two grids
    // never show icons stacked on each other while they slide past.
    return (
        slideInHorizontally(PlutoMotion.slideSpring) { it * dir / 4 } + fadeIn(PageFadeIn)
        ) togetherWith (
        slideOutHorizontally(PlutoMotion.slideSpring) { -it * dir / 4 } + fadeOut(PageFadeOut)
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

/**
 * Soft edges for a scrolling list under a header / above a legend: content fades out over
 * [top] / [bottom] where the list can scroll further that way, instead of being sliced
 * through a tile. Draw phase only (an offscreen layer masked with a gradient).
 */
internal fun Modifier.scrollFadeEdges(
    canScrollBackward: () -> Boolean,
    canScrollForward: () -> Boolean,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (canScrollBackward()) {
            val h = top.toPx()
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to androidx.compose.ui.graphics.Color.Black,
                    startY = 0f,
                    endY = h,
                ),
                size = androidx.compose.ui.geometry.Size(size.width, h),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
        if (canScrollForward()) {
            val h = bottom.toPx()
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Black,
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                    startY = size.height - h,
                    endY = size.height,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
                size = androidx.compose.ui.geometry.Size(size.width, h),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
    }

/** How far the "All apps" handle lifts as the drawer is pulled up. */
internal val HandleLift = 14.dp

/** Dock icons land with a little bounce from this scale when a slot is filled. */
internal const val DOCK_LANDING_SCALE = 0.5f

@Composable
internal fun rememberDensityPx(dp: androidx.compose.ui.unit.Dp): Float = with(LocalDensity.current) { dp.toPx() }
