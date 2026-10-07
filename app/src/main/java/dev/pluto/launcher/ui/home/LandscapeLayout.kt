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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Below this content height the dock moves to the end side so the grid keeps its rows. */
private val SideDockMaxHeight = 480.dp

/**
 * Landscape touch home: compact header (time, search, all apps, edit, settings), a wider
 * vertically-scrolling favourites grid, and a dock at the side or bottom chosen from the
 * measured space (not the orientation).
 */
@Composable
fun LandscapeLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val iconSize = state.iconSize()
    val gridKeys = state.homeTiles.map { it.id }
    val gridState = rememberAnchoredGridState(HOME_SURFACE, state, gridKeys)
    ReportScrollAnchor(HOME_SURFACE, gridState, vm)
    RestoreHomeFocusEffect(state, focus, gridState, gridKeys)
    val defaultId = state.homeTiles.firstOrNull()?.let(::homeTileFocusId) ?: ID_ALL_APPS
    SideEffect { focus.setDefaultFocus(defaultId) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val sideDock = maxHeight < SideDockMaxHeight
        Column(Modifier.fillMaxSize().swipeUpToOpen { vm.openDrawer() }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClockHeader(Modifier.weight(1f), compact = true)
                HomeToolbar(vm, includeAllApps = true)
            }

            if (sideDock) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    FavouritesGrid(state, vm, iconSize, gridState, Modifier.weight(1f).fillMaxHeight())
                    VerticalDock(state, vm, iconSize, Modifier.padding(end = 8.dp, top = 4.dp, bottom = 8.dp))
                }
            } else {
                FavouritesGrid(state, vm, iconSize, gridState, Modifier.weight(1f).fillMaxWidth())
                HorizontalDock(
                    state, vm, iconSize,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.homeTiles, key = { it.id }) { tile ->
                    HomeTileView(tile, iconSize, homeTileFocusId(tile), vm)
                }
            }
        }
    }
}

/** Dock stacked vertically at the end edge; scrolls if text/icon scaling makes it taller than the window. */
@Composable
private fun VerticalDock(state: LauncherUiState, vm: LauncherViewModel, iconSize: Dp, modifier: Modifier = Modifier) {
    PlutoPanel(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(Organization.DOCK_SLOTS) { slot ->
                DockSlot(slot, state, vm, iconSize * 0.9f)
            }
        }
    }
}
