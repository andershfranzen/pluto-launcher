package dev.pluto.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.ClockHeader
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
 * Landscape touch home: compact header (time, search, all apps, edit, settings), a wider
 * vertically-scrolling favourites grid, and a dock at the side or bottom chosen from the
 * measured space (not the orientation). The side dock sizes its slots to the height left
 * below the header, so no slot is ever clipped or hidden behind a scroll.
 */
@Composable
fun LandscapeLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val iconSize = state.iconSize()
    val gridKeys = state.homeTiles.map { it.id }
    val gridState = rememberAnchoredGridState(HOME_SURFACE, state, gridKeys)
    ReportScrollAnchor(HOME_SURFACE, gridState, state, vm, gridKeys)
    RestoreHomeFocusEffect(state, focus, gridState, gridKeys)
    TrackFocusOrder(HOME_SURFACE, state.homeTiles.map(::homeTileFocusId)) { gridState.scrollToItem(it) }
    TrackGridNavigation(HOME_SURFACE, gridState, state.homeTiles.map(::homeTileFocusId))
    val defaultId = state.homeTiles.firstOrNull()?.let(::homeTileFocusId) ?: ID_ALL_APPS
    SideEffect { focus.setDefaultFocus(defaultId) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val shortWindow = maxHeight < SideDockMaxHeight
        Column(Modifier.fillMaxSize().swipeUpToOpenDrawer(vm)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClockHeader(Modifier.weight(1f), compact = true)
                HomeToolbar(vm, includeAllApps = true)
            }

            // Measured below the header (and above the legend), so the dock choice uses the real space left.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sideIcon = if (shortWindow) sideDockIconSize(maxHeight, iconSize * 0.9f) else null
                if (sideIcon != null) {
                    Row(Modifier.fillMaxSize()) {
                        FavouritesGrid(state, vm, iconSize, gridState, Modifier.weight(1f).fillMaxHeight())
                        VerticalDock(state, vm, sideIcon, Modifier.padding(end = 8.dp, top = 4.dp, bottom = 8.dp))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        FavouritesGrid(state, vm, iconSize, gridState, Modifier.weight(1f).fillMaxWidth())
                        HorizontalDock(
                            state.dock, vm, iconSize,
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .widthIn(max = 640.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
}

@Composable
private fun FavouritesGrid(
    state: LauncherUiState,
    vm: LauncherViewModel,
    iconSize: Dp,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier,
) {
    Box(modifier) {
        if (state.homeTiles.isEmpty()) {
            EmptyFavourites(vm, Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = iconSize + 44.dp),
                state = gridState,
                // A grid that fits has nothing to scroll: let the surface's swipe-up see the drag
                // directly instead of through the grid's scrollable (which drops travel on a busy frame).
                userScrollEnabled = gridState.canScrollForward || gridState.canScrollBackward,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.homeTiles, key = { it.id }) { tile ->
                    HomeTileView(tile, iconSize, homeTileFocusId(tile), vm, plutoItem())
                }
            }
        }
    }
}

/**
 * Dock stacked vertically at the end edge. [slotIconSize] comes from [sideDockIconSize], so
 * all five slots always fit the panel's bounded height; nothing scrolls or hides.
 */
@Composable
private fun VerticalDock(state: LauncherUiState, vm: LauncherViewModel, slotIconSize: Dp, modifier: Modifier = Modifier) {
    PlutoPanel(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(SideDockGap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(Organization.DOCK_SLOTS) { slot ->
                DockSlot(slot, state.dock.getOrNull(slot), vm, slotIconSize)
            }
        }
    }
}
