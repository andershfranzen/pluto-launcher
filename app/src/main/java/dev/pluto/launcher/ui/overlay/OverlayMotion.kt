package dev.pluto.launcher.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DeferredTargetAnimation
import androidx.compose.animation.core.ExperimentalAnimatableApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.focusInert
import dev.pluto.launcher.ui.motion.PlutoMotion

/*
 * Motion used inside overlay and settings layers (the layer host animates each layer as a
 * whole; everything here decorates what happens inside one):
 *
 * - [AnimatedDialog]: nested in-window dialogs fade + scale in with their scrim and leave
 *   faster, staying composed (but focus-inert, untouchable and hidden from accessibility)
 *   while they animate out.
 * - [AnimatedItems] + [Modifier.animatePlacement]: keyed rows in a layer body slide to their
 *   new positions when the list is reordered, and fade/shrink in and out when added/removed,
 *   like Lazy animateItem() but for the (non-lazy) scrolling columns of settings screens.
 * - [ExpandingSection]: small panels that expand/collapse in step with the rows around them.
 *
 * Reduce motion needs no special casing: the window's MotionDurationScale is 0 then and every
 * animation here jumps to its end; no logic waits on an animation finishing.
 */

// --- Nested dialogs -----------------------------------------------------------------

/** Enter/exit progress of the nested dialog being composed; null outside [AnimatedDialog]. */
@Stable
internal class DialogPresence(
    private val visibility: MutableTransitionState<Boolean>,
    /** 0..1 opacity of the dialog and its scrim. Read only in draw/layer lambdas. */
    val alpha: State<Float>,
    /** Panel scale (0.92 → 1). Read only in draw/layer lambdas. */
    val scale: State<Float>,
) {
    /** The dialog was closed and is animating out: inert, untouchable, hidden from accessibility. */
    val isExiting: Boolean get() = !visibility.targetState
}

internal val LocalDialogPresence = compositionLocalOf<DialogPresence?> { null }

/** True inside a nested dialog (or list row) that is animating out. */
@Composable
internal fun isDialogExiting(): Boolean = LocalDialogPresence.current?.isExiting == true

private class DialogSlot<T : Any>(val value: T, val visibility: MutableTransitionState<Boolean>, val session: Int)

private class DialogHolder<T : Any> {
    var slot: DialogSlot<T>? = null
    var initialized = false
}

private const val DIALOG_START_SCALE = 0.92f

/**
 * Shows [content] for [value] while it is non-null, animating it in (fade + scale 0.92→1 on a
 * spring, scrim fading with it) and out (a faster fade). While closing, the last value stays
 * composed so the dialog can animate away; [ModalPanel] reads [LocalDialogPresence] to drop
 * Back handling, focus, touch and accessibility for it at once. Reopening while a previous
 * dialog is still leaving starts a fresh dialog (new remembered state, new initial focus).
 * A dialog restored after rotation appears without animation.
 */
@Composable
internal fun <T : Any> AnimatedDialog(value: T?, content: @Composable (T) -> Unit) {
    val holder = remember { DialogHolder<T>() }
    val firstComposition = !holder.initialized
    holder.initialized = true
    var slot = holder.slot
    when {
        value != null && (slot == null || !slot.visibility.targetState) -> {
            val visibility = MutableTransitionState(firstComposition).apply { targetState = true }
            slot = DialogSlot(value, visibility, (slot?.session ?: 0) + 1)
        }
        value != null && slot != null && slot.value != value -> slot = DialogSlot(value, slot.visibility, slot.session)
        value == null && slot != null && slot.visibility.targetState -> slot.visibility.targetState = false
    }
    holder.slot = slot
    if (slot == null) return
    val visibility = slot.visibility
    // Reads below change only when the dialog starts/finishes leaving, not per frame.
    if (!visibility.targetState && !visibility.currentState && visibility.isIdle) return
    key(slot.session) {
        val transition = rememberTransition(visibility, label = "dialog")
        val alpha = transition.animateFloat(
            transitionSpec = { if (targetState) PlutoMotion.fadeIn() else PlutoMotion.fadeOut() },
            label = "dialogAlpha",
        ) { shown -> if (shown) 1f else 0f }
        val scale = transition.animateFloat(
            transitionSpec = {
                if (targetState) PlutoMotion.spatial() else tween(PlutoMotion.SHORT_MS, easing = PlutoMotion.EmphasizedAccelerate)
            },
            label = "dialogScale",
        ) { shown -> if (shown) 1f else DIALOG_START_SCALE }
        val presence = remember(visibility) { DialogPresence(visibility, alpha, scale) }
        CompositionLocalProvider(LocalDialogPresence provides presence) {
            content(slot.value)
        }
    }
}

/** [value] or, once it became null, the last non-null value (keeps leaving content intact). */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return last[0] as T?
}

/** Consumes every touch before children see it (content animating away must not react). */
private val BlockPointer: Modifier = Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** For content that is animating away: hidden from accessibility and untouchable. */
internal fun Modifier.leaving(leaving: Boolean): Modifier =
    if (leaving) this.clearAndSetSemantics { }.then(BlockPointer) else this

// --- List placement -----------------------------------------------------------------

/**
 * The lookahead scope of the current layer body (see [LayerScaffold]); rows that use
 * [animatePlacement] slide to their new lookahead position inside it.
 */
internal val LocalPlacementScope = compositionLocalOf<LookaheadScope?> { null }

/** Rows move on the same spatial spring as rows expanding/shrinking, so neighbours move in step. */
private val PlacementSpring: FiniteAnimationSpec<IntOffset> = PlutoMotion.slideSpring
private val SizeSpring: FiniteAnimationSpec<IntSize> = PlutoMotion.spatial()

/**
 * Animates this element from its previous to its new position when the list was reordered:
 * [order] (its index) changed while [count] (the list size) did not. Every other move (a
 * panel above expanding or collapsing, a row added or removed) is already animated by that
 * neighbour's own size animation pushing this row through layout; animating the placement
 * on top of it made rows drift away from their neighbours (expanding controls drawn over the
 * next row, gaps under a collapsing one), so those moves follow layout directly.
 * Layout-phase only: the element is measured normally and placed at an animated offset, so
 * nothing recomposes per frame and layout geometry (touch targets, focus) is unchanged once
 * settled. The target is the lookahead (final) position, so rows never chase a moving target.
 * Scrolling and whole-layer motion happen outside the scope and are not animated here.
 */
@OptIn(ExperimentalAnimatableApi::class)
internal fun Modifier.animatePlacement(order: Int, count: Int): Modifier = composed {
    val scope = LocalPlacementScope.current ?: return@composed this
    val offset = remember { DeferredTargetAnimation(IntOffset.VectorConverter) }
    val coroutineScope = rememberCoroutineScope()
    // Plain fields updated in composition: the reorder that the next placement should animate.
    val last = remember { intArrayOf(order, count) }
    val reordering = remember { booleanArrayOf(false) }
    if (order != last[0] || count != last[1]) {
        reordering[0] = count == last[1]
        last[0] = order
        last[1] = count
    }
    this.approachLayout(
        isMeasurementApproachInProgress = { false },
        isPlacementApproachInProgress = { lookaheadCoordinates ->
            val target = with(scope) {
                lookaheadScopeCoordinates.localLookaheadPositionOf(lookaheadCoordinates)
            }.round()
            if (!reordering[0]) return@approachLayout false
            offset.updateTarget(target, coroutineScope, PlacementSpring)
            if (offset.isIdle) reordering[0] = false
            !offset.isIdle
        },
    ) { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val coordinates = coordinates
            if (coordinates == null) {
                placeable.place(0, 0)
            } else {
                val origin = with(scope) { lookaheadScopeCoordinates }
                val target = with(scope) { origin.localLookaheadPositionOf(coordinates) }.round()
                val current = origin.localPositionOf(coordinates, Offset.Zero).round()
                if (reordering[0]) {
                    val animated = offset.updateTarget(target, coroutineScope, PlacementSpring)
                    placeable.place(animated - current)
                } else {
                    // Follow layout exactly; remember where we are so a reorder starts here.
                    offset.updateTarget(current, coroutineScope, snap())
                    placeable.place(0, 0)
                }
            }
        }
    }
}

private val RowEnter: EnterTransition =
    fadeIn(PlutoMotion.fadeIn()) + expandVertically(SizeSpring, expandFrom = androidx.compose.ui.Alignment.Top)
private val RowExit: ExitTransition =
    fadeOut(PlutoMotion.fadeOut()) + shrinkVertically(SizeSpring, shrinkTowards = androidx.compose.ui.Alignment.Top)

private class ItemEntry<T>(val key: Any, var item: T, var index: Int, val visibility: MutableTransitionState<Boolean>)

/** Merges list updates into a display list that keeps removed rows (animating out) in place. */
private class AnimatedItemsState<T> {
    private var entries: List<ItemEntry<T>> = emptyList()
    private var initialized = false

    fun update(items: List<T>, keyOf: (T) -> Any): List<ItemEntry<T>> {
        val first = !initialized
        initialized = true
        // Rows that finished leaving are dropped now.
        val old = entries.filter { it.visibility.targetState || it.visibility.currentState || !it.visibility.isIdle }
        val oldByKey = HashMap<Any, ItemEntry<T>>(old.size)
        old.forEach { oldByKey[it.key] = it }
        val newKeys = HashSet<Any>(items.size)
        val result = ArrayList<ItemEntry<T>>(items.size + 4)
        items.forEachIndexed { index, item ->
            val k = keyOf(item)
            newKeys += k
            val existing = oldByKey[k]
            if (existing != null) {
                existing.item = item
                existing.index = index
                if (!existing.visibility.targetState) existing.visibility.targetState = true
                result += existing
            } else {
                result += ItemEntry(k, item, index, MutableTransitionState(first).apply { targetState = true })
            }
        }
        // Removed rows stay right after the row that preceded them, so they shrink in place.
        var anchor: Any? = null
        for (entry in old) {
            if (entry.key in newKeys) {
                anchor = entry.key
                continue
            }
            if (entry.visibility.targetState) entry.visibility.targetState = false
            val position = if (anchor == null) 0 else result.indexOfFirst { it.key == anchor } + 1
            result.add(position, entry)
            anchor = entry.key
        }
        entries = result
        return result
    }
}

/**
 * Emits one row per item of [items] (unique [key]s) into the enclosing column: rows slide to
 * new positions on reorder ([animatePlacement]), new rows fade/expand in, removed rows fade and
 * shrink out while focus-inert, untouchable and hidden from accessibility. Nothing animates on
 * first composition. Each row's content is a single layout child; use a Column for several.
 */
@Composable
internal fun <T> AnimatedItems(items: List<T>, key: (T) -> Any, content: @Composable (item: T, index: Int) -> Unit) {
    val state = remember { AnimatedItemsState<T>() }
    val entries = state.update(items, key)
    for (entry in entries) {
        key(entry.key) {
            val item = entry.item
            val index = entry.index
            val leaving = !entry.visibility.targetState
            AnimatedVisibility(
                visibleState = entry.visibility,
                // Clipped to its own (animated) bounds: an expanding panel never draws over the
                // next row before that row has moved out of the way.
                modifier = Modifier
                    .animatePlacement(order = items.indexOfFirst { key(it) == entry.key }, count = items.size)
                    .clipToBounds()
                    .leaving(leaving),
                enter = RowEnter,
                exit = RowExit,
            ) {
                CompositionLocalProvider(LocalFocusInert provides focusInert(leaving)) {
                    content(item, index)
                }
            }
        }
    }
}

/**
 * A small panel under a row (edit controls, notices) that expands/collapses vertically in step
 * with the rows around it. While collapsing it is focus-inert, untouchable and hidden from
 * accessibility.
 */
@Composable
internal fun ExpandingSection(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.clipToBounds().leaving(!visible),
        enter = RowEnter,
        exit = RowExit,
    ) {
        CompositionLocalProvider(LocalFocusInert provides focusInert(!visible)) {
            content()
        }
    }
}

// --- Entrance stagger ---------------------------------------------------------------

private const val STAGGER_STEP_MS = 24
private const val STAGGER_MAX_STEPS = 8
private val StaggerOffset = 12.dp

/**
 * A subtle entrance for the [index]th row of a freshly opened sheet: fades in while rising
 * a few dp, each row slightly after the previous one. Visual only (graphicsLayer); the row is
 * focusable and clickable throughout. [enabled] = false shows it settled immediately (e.g.
 * after rotation). Uses tween delays, which Reduce motion scales to zero.
 */
internal fun Modifier.staggerIn(index: Int, enabled: Boolean): Modifier = composed {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    val offsetPx = with(LocalDensity.current) { StaggerOffset.toPx() }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(
                durationMillis = PlutoMotion.MEDIUM_MS,
                delayMillis = index.coerceIn(0, STAGGER_MAX_STEPS) * STAGGER_STEP_MS,
                easing = PlutoMotion.EmphasizedDecelerate,
            ),
        )
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * offsetPx
    }
}
