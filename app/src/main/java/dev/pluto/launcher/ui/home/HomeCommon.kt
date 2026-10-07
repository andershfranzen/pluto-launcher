package dev.pluto.launcher.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppTile
import dev.pluto.launcher.ui.components.FolderTile
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.controllerFocusable
import kotlinx.coroutines.flow.distinctUntilChanged

/*
 * Pieces shared by the Phone and Landscape home layouts. Both use the same focus ids
 * ("home:<HomeItem.id>", "dock:<slot>", "home:<button>") so focus survives a switch
 * between them; Handheld uses its own "hh:" ids and restores through the selected app.
 */

internal const val HOME_SURFACE = "home"

internal fun homeTileFocusId(tile: HomeTile): String = "home:${tile.id}"
internal fun dockFocusId(slot: Int): String = "dock:$slot"

/** Base icon size for home tiles, scaled by the user's icon-size setting. */
internal fun LauncherUiState.iconSize(base: Dp = 52.dp): Dp = base * settings.iconScale

/** Shows controller hints only while a controller is connected and actually driving. */
internal fun showControllerHints(state: LauncherUiState, focus: ControllerFocusController): Boolean =
    state.controllerConnected && focus.inputMode == InputMode.CONTROLLER

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
    when (tile) {
        is HomeTile.App -> {
            val key = tile.entry.key
            AppTile(
                entry = tile.entry,
                iconSize = iconSize,
                onLaunch = { vm.launch(key) },
                onActions = { vm.openLayer(Layer.AppActions(key)) },
                modifier = modifier,
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
                modifier = modifier,
                showLabel = showLabel,
                onFocused = { vm.onControlFocused(focusId) },
            )
        }
    }
}

/** One dock slot: the app's icon, or a labelled empty slot that opens Edit with this slot picked. */
@Composable
internal fun DockSlot(
    slot: Int,
    state: LauncherUiState,
    vm: LauncherViewModel,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val entry = state.dock.getOrNull(slot)
    val focusId = dockFocusId(slot)
    if (entry != null) {
        AppTile(
            entry = entry,
            iconSize = iconSize,
            onLaunch = { vm.launch(entry.key) },
            onActions = { vm.openLayer(Layer.AppActions(entry.key)) },
            modifier = modifier,
            focusId = focusId,
            showLabel = false,
            onFocused = {
                vm.onAppSelected(entry.key)
                vm.onControlFocused(focusId)
            },
        )
    } else {
        Box(
            modifier
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

/** Reports the first visible item's key as the logical scroll anchor for [surface]. */
@Composable
internal fun ReportScrollAnchor(surface: String, gridState: LazyGridState, vm: LauncherViewModel) {
    val currentVm by rememberUpdatedState(vm)
    LaunchedEffect(gridState, surface) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String }
            .distinctUntilChanged()
            .collect { key -> if (key != null) currentVm.onScrollAnchor(surface, key) }
    }
}

/**
 * Opens the drawer on an upward swipe: either a drag on non-scrolling areas, or upward
 * scroll left over when the favourites grid is already at its end. The visible
 * "All apps" button keeps the drawer reachable without gestures.
 */
internal fun Modifier.swipeUpToOpen(onOpen: () -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val connection = remember {
        object : NestedScrollConnection {
            var accumulated = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f) {
                    accumulated += -available.y
                    if (accumulated > threshold) {
                        accumulated = 0f
                        currentOnOpen()
                    }
                } else if (available.y > 0f) {
                    accumulated = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                accumulated = 0f
                return Velocity.Zero
            }
        }
    }
    this
        .nestedScroll(connection)
        .pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total < -threshold) currentOnOpen() },
                onDragCancel = { total = 0f },
            ) { change, amount ->
                total += amount
                change.consume()
            }
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
internal const val ID_ALL_APPS = "home:allapps"
internal const val ID_EDIT = "home:edit"
internal const val ID_SETTINGS = "home:settings"
private val HOME_BUTTON_IDS = setOf(ID_SEARCH, ID_ALL_APPS, ID_EDIT, ID_SETTINGS)

/**
 * When a Phone/Landscape home (re)appears (first launch, rotation, closing a full-screen
 * layer) restore focus to the remembered control / selected app. Skipped when something
 * else (e.g. the layer-close restore in LauncherRoot) already placed focus.
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
    LaunchedEffect(Unit) {
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
