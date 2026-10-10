package dev.pluto.launcher.ui.home

import dev.pluto.launcher.ui.theme.rememberDockFill
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.components.GlassPanel
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.components.rememberEqual
import androidx.compose.runtime.remember
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoPanel
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.theme.PlutoDimens

/** Below this window content height the dock may move to the end side so the grid keeps its rows. */
private val SideDockMaxHeight = 480.dp

/** Space around the side dock's slots: outer top/bottom padding plus the panel's inner padding. */
private val SideDockVerticalPadding = 4.dp + 8.dp + 8.dp + 8.dp
private val SideDockGap = 2.dp
/** A dock slot is its icon plus the tile's 6dp top and bottom padding. */
private val DockSlotPadding = 12.dp

/**
 * Icon size for the side dock's slots so all [Organization.DOCK_SLOTS] fit [availableHeight]
 * without scrolling, or null when even 48dp touch targets would not fit (use the bottom dock).
 */
internal fun sideDockIconSize(availableHeight: Dp, preferredIcon: Dp): Dp? {
    val slots = Organization.DOCK_SLOTS
    val perSlot = (availableHeight - SideDockVerticalPadding - SideDockGap * (slots - 1)) / slots
    if (perSlot < PlutoDimens.MinTouchTarget) return null
    return minOf(preferredIcon, perSlot - DockSlotPadding)
}

/**
 * Landscape touch home, the same model as portrait: a wider vertically-scrolling favourites
 * grid and a glass dock at the side or bottom chosen from the measured space (not the
 * orientation). Search, Edit and Settings live in the menu opened by pressing and holding
 * empty space (also screen-reader actions), not in a toolbar. The side dock sizes its slots
 * to the height available, so no slot is ever clipped or hidden behind a scroll.
 */
@Composable
fun LandscapeLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val iconSize = state.iconSize()
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
    val dockFill = rememberDockFill(state.settings)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val shortWindow = maxHeight < SideDockMaxHeight
        Column(
            Modifier
                .fillMaxSize()
                .homeMenuOnLongPress(menu, vm)
                .doubleTapToLock(state.settings.doubleTapToLock, vm)
                .swipeUpToOpenDrawer(vm)
                .swipeDownForAction(state.settings.swipeDownAction, vm),
        ) {
            // Measured above the legend, so the dock choice uses the real space left.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sideIcon = if (shortWindow) sideDockIconSize(maxHeight, iconSize * 0.9f) else null
                if (sideIcon != null) {
                    Row(Modifier.fillMaxSize()) {
                        FavouritesGrid(tiles, vm, iconSize, gridState, Modifier.weight(1f).fillMaxHeight())
                        VerticalDock(dock, vm, sideIcon, dockFill, Modifier.padding(end = 16.dp, top = 8.dp, bottom = 8.dp))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        FavouritesGrid(tiles, vm, iconSize, gridState, Modifier.weight(1f).fillMaxWidth())
                        HorizontalDock(
                            dock, vm, iconSize,
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .widthIn(max = 640.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            glass = true,
                            fill = dockFill,
                        )
                    }
                }
            }

            if (showControllerHints(state, focus)) {
                ButtonLegend(
                    state.activeMapping(),
                    Legends.Home,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 6.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
    }
    HomeMenu(menu, vm)
}

@Composable
private fun FavouritesGrid(
    tiles: List<HomeTile>,
    vm: LauncherViewModel,
    iconSize: Dp,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier,
) {
    Box(modifier) {
        if (tiles.isEmpty()) {
            EmptyFavourites(vm, Modifier.align(Alignment.Center))
        } else {
            val (entries, dragProbe) = rememberDragAwareTiles(tiles, gridState)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = iconSize + 44.dp),
                state = gridState,
                // A grid that fits has nothing to scroll: let the surface's swipe-up see the drag
                // directly instead of through the grid's scrollable (which drops travel on a busy frame).
                userScrollEnabled = gridState.canScrollForward || gridState.canScrollBackward,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
 * Glass dock stacked vertically at the end edge, like portrait's dock turned on its side.
 * [slotIconSize] comes from [sideDockIconSize], so all five slots always fit the panel's
 * bounded height; empty ones fold away as in portrait ([showEmptyDockSlots]).
 */
@Composable
private fun VerticalDock(dock: List<AppEntry?>, vm: LauncherViewModel, slotIconSize: Dp, fill: Brush?, modifier: Modifier = Modifier) {
    val showEmpty = showEmptyDockSlots(dock)
    val shape = RoundedCornerShape(32.dp)
    val slots = @Composable {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(SideDockGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(Organization.DOCK_SLOTS) { slot ->
                val entry = dock.getOrNull(slot)
                AnimatedVisibility(
                    visible = entry != null || showEmpty,
                    enter = expandVertically(PlutoMotion.spatialFast()) + fadeIn(PlutoMotion.fadeIn()),
                    exit = shrinkVertically(PlutoMotion.spatialFast()) + fadeOut(PlutoMotion.fadeOut()),
                ) {
                    DockSlot(slot, entry, vm, slotIconSize)
                }
            }
        }
    }
    if (fill != null) GlassPanel(modifier.fillMaxHeight(), shape = shape, fill = fill) { slots() } else GlassPanel(modifier.fillMaxHeight(), shape = shape) { slots() }
}
