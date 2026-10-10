package dev.pluto.launcher.ui.home

import androidx.compose.foundation.gestures.detectTapGestures
import dev.pluto.launcher.system.ScreenLockService
import dev.pluto.launcher.data.prefs.SwipeDownAction
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import dev.pluto.launcher.system.NotificationShade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.ui.DragSource
import dev.pluto.launcher.ui.widgets.HomeWidgetView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppTile
import dev.pluto.launcher.ui.components.FolderTile
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.ui.focus.GridNavigation
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.motion.LocalAppLauncher
import dev.pluto.launcher.ui.motion.PlutoMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density

/*
 * Pieces shared by the Phone and Landscape home layouts. Both use the same focus ids
 * ("home:<HomeItem.id>", "dock:<slot>", "home:<button>") so focus survives a switch
 * between them; Handheld uses its own "hh:" ids and restores through the selected app.
 */

internal const val HOME_SURFACE = "home"

/** Home copy shared by the layouts (inline for 0.1). */
internal object HomeText {
    const val LOCK_NEEDS_SERVICE = "To lock with a double tap, turn on “Pluto screen lock” in Accessibility settings."
    const val EMPTY_FAVOURITES = "Swipe up to find apps, then pin favourites. " +
        "Pressing and holding an app opens its actions; Add favourite works without gestures."

    /** The drop bar that takes an app off Home or out of the dock. */
    const val REMOVE = "Remove"
}

/** A widget row is a tile's height: the icon plus room for its label. */
internal val WidgetRowExtra = 44.dp

internal fun homeTileFocusId(tile: HomeTile): String = "home:${tile.id}"
internal fun dockFocusId(slot: Int): String = "dock:$slot"

/** Base icon size for home tiles, scaled by the user's icon-size setting. */
internal fun LauncherUiState.iconSize(base: Dp = 52.dp): Dp = base * settings.iconScale

/**
 * Shows controller hints while a controller is connected and driving. Handheld mode is
 * controller-first: its legend stays visible whenever a controller is connected, even
 * after a touch, and disappears as soon as the controller is removed.
 */
internal fun showControllerHints(
    state: LauncherUiState,
    focus: ControllerFocusController,
    mode: LauncherMode? = null,
): Boolean = state.controllerConnected && (mode == LauncherMode.HANDHELD || focus.inputMode == InputMode.CONTROLLER)

/** A home favourites tile (app or folder) wired to the view model. */
@Composable
internal fun HomeTileView(
    tile: HomeTile,
    iconSize: Dp,
    focusId: String,
    vm: LauncherViewModel,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    if (tile is HomeTile.Widget) {
        HomeWidgetView(tile.widget, iconSize + WidgetRowExtra, vm, modifier)
        return
    }
    // Press, hold and move: the tile lifts off as a drag (see HomeDrag).
    val draggable = modifier
        .dragFeedback(tile.id)
        .homeDragSource(
            source = {
                DragSource.Home(
                    when (tile) {
                        is HomeTile.App -> HomeItem.App(tile.entry.key)
                        is HomeTile.FolderTile -> HomeItem.FolderRef(tile.folder.folder.id)
                        is HomeTile.Widget -> HomeItem.Widget(tile.widget.appWidgetId)
                    },
                )
            },
            look = {
                when (tile) {
                    is HomeTile.App -> DragLook.App(tile.entry)
                    is HomeTile.FolderTile -> DragLook.Folder(tile.folder)
                    is HomeTile.Widget -> null
                }
            },
        )
    when (tile) {
        is HomeTile.App -> {
            val key = tile.entry.key
            val launcher = LocalAppLauncher.current
            AppTile(
                entry = tile.entry,
                iconSize = iconSize,
                onLaunch = { launcher.launch(key, focusId) },
                onActions = { vm.openLayer(Layer.AppActions(key)) },
                modifier = draggable,
                focusId = focusId,
                showLabel = showLabel,
                onFocused = {
                    vm.onAppSelected(key)
                    vm.onControlFocused(focusId)
                },
            )
        }
        is HomeTile.FolderTile -> {
            val folderId = tile.folder.folder.id
            FolderTile(
                folder = tile.folder,
                iconSize = iconSize,
                focusId = focusId,
                onOpen = { vm.openLayer(Layer.FolderLayer(folderId)) },
                onEdit = {
                    vm.setEditSelection(tile.id)
                    vm.openLayer(Layer.Edit)
                },
                modifier = draggable,
                showLabel = showLabel,
                onFocused = { vm.onControlFocused(focusId) },
            )
        }
        is HomeTile.Widget -> Unit
    }
}

/** One dock slot: the app's icon, or a labelled empty slot that opens Edit with this slot picked. */
@Composable
internal fun DockSlot(
    slot: Int,
    entry: AppEntry?,
    vm: LauncherViewModel,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val focusId = dockFocusId(slot)
    val landing = rememberDockLanding(entry?.key)
    // Every slot (empty or not) takes drops; an app in it can be dragged out.
    val target = modifier.dockDropTarget(slot)
    if (entry != null) {
        val launcher = LocalAppLauncher.current
        AppTile(
            entry = entry,
            iconSize = iconSize,
            onLaunch = { launcher.launch(entry.key, focusId) },
            onActions = { vm.openLayer(Layer.AppActions(entry.key)) },
            modifier = target
                .homeDragSource(source = { DragSource.Dock(slot, entry.key) }, look = { DragLook.App(entry) })
                .graphicsLayer {
                val scale = landing.scale()
                scaleX = scale
                scaleY = scale
                alpha = ((scale - DOCK_LANDING_SCALE) / (1f - DOCK_LANDING_SCALE) * 2f).coerceIn(0f, 1f)
            },
            focusId = focusId,
            showLabel = false,
            onFocused = {
                vm.onAppSelected(entry.key)
                vm.onControlFocused(focusId)
            },
        )
    } else {
        Box(
            target
                .size(iconSize + 12.dp)
                .controllerFocusable(
                    id = focusId,
                    onActivate = {
                        vm.setEditSelection(focusId)
                        vm.openLayer(Layer.Edit)
                    },
                    contentDescription = "Empty dock slot ${slot + 1}. Opens Edit to choose an app",
                    shape = CircleShape,
                    onFocused = { vm.onControlFocused(focusId) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(iconSize * 0.8f)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(iconSize * 0.4f),
                )
            }
        }
    }
}

/**
 * Scale of a dock slot's icon: when the slot gets a different app (newly filled or
 * replaced, not on first composition) the icon lands from [DOCK_LANDING_SCALE] with
 * [PlutoMotion.spatialBouncy]. Visual only; the slot's geometry never changes.
 */
@Stable
internal class DockLanding {
    val anim = Animatable(1f)

    /** Set in composition when the app changes, so the very first frame already starts small. */
    var pending = false

    fun scale(): Float = if (pending) DOCK_LANDING_SCALE else anim.value
}

@Composable
private fun rememberDockLanding(key: AppKey?): DockLanding {
    val landing = remember { DockLanding() }
    val lastKey = remember { arrayOf(key) }
    if (lastKey[0] != key) {
        lastKey[0] = key
        if (key != null) landing.pending = true
    }
    LaunchedEffect(key) {
        if (!landing.pending) return@LaunchedEffect
        landing.anim.snapTo(DOCK_LANDING_SCALE)
        landing.pending = false
        landing.anim.animateTo(1f, PlutoMotion.spatialBouncy())
    }
    return landing
}

/**
 * Lazy grid state that starts at the persisted logical anchor for [surface] (an item key),
 * falling back to the last saved index (clamped) when that item no longer exists.
 */
@Composable
internal fun rememberAnchoredGridState(surface: String, state: LauncherUiState, itemKeys: List<String>): LazyGridState {
    val savedIndex = rememberSaveable(surface) { mutableIntStateOf(0) }
    val initialIndex = remember(surface) {
        val anchor = state.session.scrollAnchors[surface]
        val anchored = anchor?.let(itemKeys::indexOf)?.takeIf { it >= 0 }
        anchored ?: savedIndex.intValue.coerceIn(0, (itemKeys.size - 1).coerceAtLeast(0))
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }.collect { savedIndex.intValue = it }
    }
    return gridState
}

/** What [ReportScrollAnchor] needs from one grid layout pass. */
private data class GridAnchorSnapshot(val viewport: IntSize, val columns: Int, val firstLineKeys: List<Any?>)

/** A resting grid's position and geometry: [ReportScrollAnchor] looks again only when this changes. */
private data class GridRest(val firstIndex: Int, val viewport: IntSize, val columns: Int, val count: Int)

/**
 * Keeps the logical scroll anchor (an item key) for [surface] in the session.
 *
 * A lazy grid always starts its first line at a line boundary, so after a reflow (rotation,
 * window resize, a different column count) its first visible item is an *earlier* item
 * than the anchor. Adopting that item as the new anchor would drift the list towards the
 * top on every rotation. So:
 * - the anchor only changes when it is no longer on the first visible line (the user or a
 *   focus move really scrolled), and
 * - a geometry change scrolls the grid back to the anchor's line instead of re-reporting.
 */
@Composable
internal fun ReportScrollAnchor(
    surface: String,
    gridState: LazyGridState,
    state: LauncherUiState,
    vm: LauncherViewModel,
    itemKeys: List<String>,
) {
    val currentVm by rememberUpdatedState(vm)
    val currentKeys by rememberUpdatedState(itemKeys)
    val anchor = remember(surface) { arrayOf(state.session.scrollAnchors[surface]) }
    LaunchedEffect(gridState, surface) {
        var geometry: Pair<IntSize, Int>? = null
        // Nothing is read per frame while a scroll is in progress (only isScrollInProgress
        // is observed then); the first line is looked at once the grid has come to rest,
        // after a programmatic jump (first index changed), or after a reflow.
        snapshotFlow {
            if (gridState.isScrollInProgress) return@snapshotFlow null
            val info = gridState.layoutInfo
            GridRest(gridState.firstVisibleItemIndex, info.viewportSize, info.maxSpan, info.totalItemsCount)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .map {
                val info = gridState.layoutInfo
                val first = info.visibleItemsInfo.firstOrNull()
                val line = if (first == null) emptyList() else info.visibleItemsInfo.filter { it.row == first.row }.map { it.key }
                GridAnchorSnapshot(info.viewportSize, info.maxSpan, line)
            }
            .distinctUntilChanged()
            .collect { snap ->
                if (snap.firstLineKeys.isEmpty()) return@collect
                val newGeometry = snap.viewport to snap.columns
                val previousGeometry = geometry
                geometry = newGeometry
                val current = anchor[0]
                if (previousGeometry != null && previousGeometry != newGeometry) {
                    // Reflow: restore the closest valid position (the anchor's line), never re-anchor.
                    val index = current?.let(currentKeys::indexOf) ?: -1
                    if (index >= 0 && !snap.firstLineKeys.contains(current)) gridState.scrollToItem(index)
                    return@collect
                }
                if (current != null && snap.firstLineKeys.contains(current)) return@collect
                val key = snap.firstLineKeys.first() as? String ?: return@collect
                anchor[0] = key
                currentVm.onScrollAnchor(surface, key)
            }
    }
}

/** Lazy row/column state that starts at the persisted logical anchor for [surface]. */
@Composable
internal fun rememberAnchoredListState(surface: String, state: LauncherUiState, itemKeys: List<String>): LazyListState {
    val initialIndex = remember(surface) {
        state.session.scrollAnchors[surface]?.let(itemKeys::indexOf)?.takeIf { it >= 0 } ?: 0
    }
    return rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
}

/** Reports the first visible item's key of a single-line lazy list as the anchor for [surface]. */
@Composable
internal fun ReportListScrollAnchor(surface: String, listState: LazyListState, vm: LauncherViewModel) {
    val currentVm by rememberUpdatedState(vm)
    LaunchedEffect(listState, surface) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String }
            .distinctUntilChanged()
            .collect { key -> if (key != null) currentVm.onScrollAnchor(surface, key) }
    }
}

/**
 * Reports the on-screen order of a list's focus ids to the focus controller, so a control
 * that vanishes (uninstall, hide, unpin, move to folder) hands focus to its nearest
 * surviving neighbour. [scrollTo] scrolls the list so an off-screen neighbour is composed.
 */
@Composable
internal fun TrackFocusOrder(surface: String, ids: List<String>, scrollTo: (suspend (Int) -> Unit)? = null) {
    val focus = LocalControllerFocus.current
    val inert = LocalFocusInert.current
    SideEffect { focus.setOrder(surface, ids, scrollTo) }
    DisposableEffect(focus, surface) { onDispose { focus.clearScroller(surface) } }
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(ids) {
        if (firstRun[0]) {
            firstRun[0] = false
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (!inert.value) focus.followVanished(surface)
    }
}

/**
 * Registers [gridState] for logical Up/Down controller movement (same column, row by row)
 * over [ids], its single-span items starting at grid index [firstIndex].
 */
@Composable
internal fun TrackGridNavigation(surface: String, gridState: LazyGridState, ids: List<String>, firstIndex: Int = 0) {
    val focus = LocalControllerFocus.current
    val scope = rememberCoroutineScope()
    SideEffect { focus.setGrid(surface, GridNavigation(gridState, ids, firstIndex, scope)) }
    DisposableEffect(focus, surface) { onDispose { focus.setGrid(surface, null) } }
}

/**
 * Swipe up anywhere on the home surface to pull the drawer up continuously: drags on
 * non-scrolling areas, and upward drag the favourites grid leaves over at its end. Releasing
 * past half way (or with an upward fling) opens it; otherwise LauncherRoot settles it back.
 * Without gestures the drawer is one tap away through the header's Search button (and its
 * "All apps" accessibility action / long press), and controller Y.
 */
@Composable
internal fun Modifier.swipeUpToOpenDrawer(vm: LauncherViewModel): Modifier {
    val driver = rememberRevealDragDriver(opening = true) { open -> if (open) vm.openDrawer() }
    return revealDrag(driver, opening = true)
}

/**
 * Swipe down anywhere on the home surface for the user's chosen [SwipeDownAction]: by default
 * pull down the notification shade, as stock launchers do. Drags on non-scrolling areas, and
 * downward drag the favourites grid leaves over at its top. Fires once per gesture.
 */
@Composable
internal fun Modifier.swipeDownForAction(action: SwipeDownAction, vm: LauncherViewModel): Modifier {
    val context = LocalContext.current
    val gesture = remember { ShadeGesture() }
    if (action == SwipeDownAction.NOTHING) return this
    gesture.expand = when (action) {
        SwipeDownAction.SEARCH -> { { vm.openDrawer(withSearch = true) } }
        else -> { { NotificationShade.expand(context) } }
    }
    return this
        // Observes (never consumes) the finger, so one gesture can open the shade only once.
        .pointerInput(gesture) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.any { it.pressed }
                    if (pressed && !gesture.pointerDown) gesture.fired = false
                    gesture.pointerDown = pressed
                }
            }
        }
        .nestedScroll(gesture)
        .pointerInput(gesture) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over > 0f) {
                        change.consume()
                        gesture.fire()
                    }
                }
            }
        }
}

/**
 * Double-tap empty space on the home surface to lock the screen, when the user turned that
 * on. Tiles and dock slots take their own taps. Without Pluto's screen-lock service enabled
 * the tap explains why and opens Accessibility settings instead.
 */
@Composable
internal fun Modifier.doubleTapToLock(enabled: Boolean, vm: LauncherViewModel): Modifier {
    if (!enabled) return this
    val context = LocalContext.current
    return pointerInput(vm) {
        detectTapGestures(onDoubleTap = {
            if (!ScreenLockService.lock()) {
                vm.showMessage(HomeText.LOCK_NEEDS_SERVICE)
                ScreenLockService.openSettings(context)
            }
        })
    }
}

private class ShadeGesture : NestedScrollConnection {
    var expand: () -> Unit = {}
    var pointerDown = false
    var fired = false

    fun fire() {
        if (fired) return
        fired = true
        expand()
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && pointerDown && available.y > 0f) fire()
        return Offset.Zero
    }
}

/** Index of the first dock slot holding [key], or -1. */
internal fun LauncherUiState.dockSlotOf(key: AppKey?): Int =
    if (key == null) -1 else dock.indexOfFirst { it?.key == key }

/**
 * Where focus should go when a Phone/Landscape home (re)appears: the remembered control
 * if it exists here, else the selected app's tile or dock slot.
 * Returns the focus id plus the grid index to scroll to first (or null).
 */
internal fun LauncherUiState.homeRestoreTarget(gridKeys: List<String>): Pair<String, Int?>? {
    val control = session.focusedControlId
    if (control != null) {
        val tileKey = control.removePrefix("home:")
        val gridIndex = gridKeys.indexOf(tileKey)
        if (control.startsWith("home:") && gridIndex >= 0) return control to gridIndex
        if (control.startsWith("dock:") && dock.getOrNull(control.removePrefix("dock:").toIntOrNull() ?: -1) != null) {
            return control to null
        }
        if (control in HOME_BUTTON_IDS) return control to null
    }
    val selected = session.selectedApp ?: return null
    val tileIndex = homeTiles.indexOfFirst { it is HomeTile.App && it.entry.key == selected }
    if (tileIndex >= 0) return homeTileFocusId(homeTiles[tileIndex]) to tileIndex
    val folderIndex = homeTiles.indexOfFirst { it is HomeTile.FolderTile && selected in it.folder.folder.apps }
    if (folderIndex >= 0) return homeTileFocusId(homeTiles[folderIndex]) to folderIndex
    val slot = dockSlotOf(selected)
    if (slot >= 0) return dockFocusId(slot) to null
    return null
}

internal const val ID_SEARCH = "home:search"
internal const val ID_EDIT = "home:edit"
internal const val ID_SETTINGS = "home:settings"
private val HOME_BUTTON_IDS = setOf(ID_SEARCH, ID_EDIT, ID_SETTINGS)

/**
 * Fixed column count chosen from the grid's own width (no BoxWithConstraints, so nothing is
 * composed in the measure pass): as many [minCell]-wide columns as fit, within [minCount]..[maxCount].
 * [extra] is width outside the grid's content (its content padding) that counts as available.
 */
internal class AdaptiveCountCells(
    private val minCell: Dp,
    private val minCount: Int,
    private val maxCount: Int,
    private val extra: Dp = 0.dp,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = ((availableSize + extra.roundToPx()) / minCell.roundToPx().coerceAtLeast(1)).coerceIn(minCount, maxCount)
        val gridSize = availableSize - spacing * (count - 1)
        val cell = gridSize / count
        val remainder = gridSize % count
        return List(count) { cell + if (it < remainder) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean =
        other is AdaptiveCountCells && other.minCell == minCell && other.minCount == minCount &&
            other.maxCount == maxCount && other.extra == extra

    override fun hashCode(): Int = ((minCell.hashCode() * 31 + minCount) * 31 + maxCount) * 31 + extra.hashCode()
}

/**
 * When a Phone/Landscape home (re)appears (first launch, rotation, closing a full-screen
 * layer) or a controller is connected, restore focus to the remembered control / selected
 * app. Skipped when something else (e.g. the layer-close restore in LauncherRoot) already
 * placed focus.
 */
@Composable
internal fun RestoreHomeFocusEffect(
    state: LauncherUiState,
    focus: ControllerFocusController,
    gridState: LazyGridState,
    gridKeys: List<String>,
) {
    val currentState by rememberUpdatedState(state)
    val currentKeys by rememberUpdatedState(gridKeys)
    LaunchedEffect(state.controllerConnected) {
        withFrameNanos { }
        val s = currentState
        if (s.session.layers.isNotEmpty() || focus.focusedId != null) return@LaunchedEffect
        val (id, index) = s.homeRestoreTarget(currentKeys) ?: return@LaunchedEffect
        if (focus.inputMode == InputMode.CONTROLLER) {
            if (index != null && gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                gridState.scrollToItem(index)
            }
            focus.requestFocusWhenReady(id)
        } else {
            focus.rememberFocusTarget(id)
        }
    }
}
