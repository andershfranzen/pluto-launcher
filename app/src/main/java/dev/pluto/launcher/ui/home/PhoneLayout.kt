package dev.pluto.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.motion.PlutoMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import dev.pluto.launcher.ui.theme.rememberDockFill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.EmptyState
import dev.pluto.launcher.ui.components.GlassPanel
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoPanel
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.components.rememberEqual
import dev.pluto.launcher.ui.focus.LocalControllerFocus

/**
 * Portrait home: the favourites grid and a glass five-slot dock (no clock: the status bar has it).
 * Portrait is touch-first: Search, Edit and Settings live in a menu opened by pressing and
 * holding empty space (also screen-reader actions on the home surface), not in a toolbar.
 * The drawer opens by swiping up anywhere (the panel follows the finger) and by controller Y.
 */
@Composable
fun PhoneLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val iconSize = state.iconSize()
    // Equal lists from a rebuilt state keep their instance, so the grid and dock below skip.
    val tiles = rememberEqual(state.homeTiles)
    val dock = rememberEqual(state.dock)
    val gridKeys = remember(tiles) { tiles.map { it.id } }
    val focusIds = remember(tiles) { tiles.map(::homeTileFocusId) }
    val gridState = rememberAnchoredGridState(HOME_SURFACE, state, gridKeys)
    ReportScrollAnchor(HOME_SURFACE, gridState, state, vm, gridKeys)
    RestoreHomeFocusEffect(state, focus, gridState, gridKeys)
    TrackFocusOrder(HOME_SURFACE, focusIds) { gridState.scrollToItem(it) }
    TrackGridNavigation(HOME_SURFACE, gridState, focusIds)
    val defaultId = focusIds.firstOrNull() ?: ID_EMPTY_EDIT
    SideEffect { focus.setDefaultFocus(defaultId) }

    val menu = remember { HomeMenuState() }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .homeMenuOnLongPress(menu, vm)
            .doubleTapToLock(state.settings.doubleTapToLock, vm)
            .swipeUpToOpenDrawer(vm).swipeDownForAction(state.settings.swipeDownAction, vm),
    ) {
        // No clock: the status bar already shows the time; Home starts with the favourites.
        Spacer(Modifier.height(16.dp))

        PhoneFavourites(tiles, vm, iconSize, gridState, Modifier.weight(1f).fillMaxWidth())

        HorizontalDock(
            dock, vm, iconSize,
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            glass = true,
            fill = rememberDockFill(state.settings),
        )

        if (showControllerHints(state, focus)) {
            ButtonLegend(
                state.activeMapping(),
                Legends.Home,
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
            )
        }
    }
    HomeMenu(menu, vm)
}

/**
 * The portrait favourites grid: 4 columns on ordinary phones, 5-6 on wide portrait windows,
 * chosen by the grid itself from its width (tiles grow with icon size).
 */
@Composable
private fun PhoneFavourites(
    tiles: List<HomeTile>,
    vm: LauncherViewModel,
    iconSize: Dp,
    gridState: LazyGridState,
    modifier: Modifier,
) {
    Box(modifier) {
        if (tiles.isEmpty()) {
            EmptyFavourites(vm, Modifier.align(Alignment.Center))
        } else {
            val (entries, dragProbe) = rememberDragAwareTiles(tiles, gridState)
            LazyVerticalGrid(
                columns = AdaptiveCountCells(minCell = iconSize + 36.dp, minCount = 3, maxCount = 6, extra = 24.dp),
                state = gridState,
                // A grid that fits has nothing to scroll: let the surface's swipe-up see the drag
                // directly instead of through the grid's scrollable (which drops travel on a busy frame).
                userScrollEnabled = gridState.canScrollForward || gridState.canScrollBackward,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize().then(dragProbe),
            ) {
                items(entries, key = { it.key }, span = { entry ->
                    // A widget takes the whole row.
                    if (entry is HomeGridEntry.Tile && entry.tile is HomeTile.Widget) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                }, contentType = { entry ->
                    when (entry) {
                        HomeGridEntry.Gap -> "gap"
                        is HomeGridEntry.Tile -> when (entry.tile) {
                            is HomeTile.App -> "app"
                            is HomeTile.FolderTile -> "folder"
                            is HomeTile.Widget -> "widget"
                        }
                    }
                }) { entry ->
                    when (entry) {
                        HomeGridEntry.Gap -> DragGap(iconSize, plutoItem())
                        is HomeGridEntry.Tile -> HomeTileView(entry.tile, iconSize, homeTileFocusId(entry.tile), vm, plutoItem())
                    }
                }
            }
        }
    }
}

/**
 * Whether the dock shows its empty slots. They only take room while they are useful: during a
 * drag (as drop targets), for a controller (Edit fills them), or when the dock has nothing
 * else to show. Otherwise the apps spread over the dock and the empty slots fold away.
 */
@Composable
internal fun showEmptyDockSlots(dock: List<AppEntry?>): Boolean {
    val drag = LocalHomeDrag.current
    val focus = LocalControllerFocus.current
    return drag?.isActive == true || focus.inputMode == InputMode.CONTROLLER || dock.none { it != null }
}

/** The dock's apps in a row, on a panel, or on frosted glass ([glass], portrait home). */
@Composable
internal fun HorizontalDock(
    dock: List<AppEntry?>,
    vm: LauncherViewModel,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    /** Tinted glass for [glass] (see rememberDockFill); null for the neutral glass. */
    fill: Brush? = null,
) {
    val showEmpty = showEmptyDockSlots(dock)
    val slots = @Composable {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = if (glass) 10.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Organization.DOCK_SLOTS) { slot ->
                val entry = dock.getOrNull(slot)
                val shown by animateFloatAsState(if (entry != null || showEmpty) 1f else 0f, PlutoMotion.spatialFast(), label = "dockSlot")
                Box(
                    Modifier
                        .weight(shown.coerceAtLeast(0.001f))
                        .graphicsLayer { alpha = shown.coerceIn(0f, 1f) },
                    contentAlignment = Alignment.Center,
                ) {
                    // A folded slot keeps its node (and drop target) but is not focusable or drawn.
                    if (shown > 0.01f) DockSlot(slot, entry, vm, iconSize)
                }
            }
        }
    }
    if (glass) {
        if (fill != null) GlassPanel(modifier.fillMaxWidth(), fill = fill) { slots() } else GlassPanel(modifier.fillMaxWidth()) { slots() }
    } else {
        PlutoPanel(modifier.fillMaxWidth()) { slots() }
    }
}

internal const val ID_EMPTY_EDIT = "home:empty:edit"

@Composable
internal fun EmptyFavourites(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    EmptyState(
        title = "No favourites yet",
        detail = HomeText.EMPTY_FAVOURITES,
        onWallpaper = true,
        icon = Icons.Outlined.Star,
        modifier = modifier,
    ) {
        PlutoTextButton(id = ID_EMPTY_EDIT, text = "Add favourite", onClick = { vm.openLayer(Layer.Edit) }, emphasized = true)
    }
}
