package dev.pluto.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.ClockHeader
import dev.pluto.launcher.ui.components.EmptyState
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.components.PlutoPanel
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.components.rememberEqual
import dev.pluto.launcher.ui.focus.LocalControllerFocus

/**
 * Portrait home: clock and date, a toolbar (Search, Edit, Settings), the favourites grid and
 * the five-slot dock. The drawer opens by swiping up anywhere (the panel follows the finger),
 * by the Search button (one tap; its "All apps" accessibility action / long press opens the
 * drawer without the keyboard) and by controller Y; there is no separate "All apps" button.
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
    val defaultId = focusIds.firstOrNull() ?: ID_SEARCH
    SideEffect { focus.setDefaultFocus(defaultId) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .swipeUpToOpenDrawer(vm),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ClockHeader(Modifier.weight(1f).padding(top = 8.dp))
            HomeToolbar(vm)
        }

        PhoneFavourites(tiles, vm, iconSize, gridState, Modifier.weight(1f).fillMaxWidth())

        HorizontalDock(
            dock, vm, iconSize,
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
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
            LazyVerticalGrid(
                columns = AdaptiveCountCells(minCell = iconSize + 36.dp, minCount = 3, maxCount = 6, extra = 24.dp),
                state = gridState,
                // A grid that fits has nothing to scroll: let the surface's swipe-up see the drag
                // directly instead of through the grid's scrollable (which drops travel on a busy frame).
                userScrollEnabled = gridState.canScrollForward || gridState.canScrollBackward,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(tiles, key = { it.id }, contentType = { if (it is HomeTile.App) "app" else "folder" }) { tile ->
                    HomeTileView(tile, iconSize, homeTileFocusId(tile), vm, plutoItem())
                }
            }
        }
    }
}

/**
 * Search, Edit and Settings as visible buttons (no long-press needed). Search opens the
 * drawer with the keyboard up; its "All apps" accessibility action (also a long press or X)
 * opens the drawer without it.
 */
@Composable
internal fun HomeToolbar(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    // Separate discs with a clear gap, not a touching chain.
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlutoIconButton(
            id = ID_SEARCH,
            icon = Icons.Outlined.Search,
            label = "Search apps",
            onClick = { vm.openDrawer(withSearch = true) },
            onWallpaper = true,
            onFocused = { vm.onControlFocused(ID_SEARCH) },
            onSecondary = { vm.openDrawer() },
            secondaryLabel = HomeText.ALL_APPS_ACTION,
        )
        PlutoIconButton(
            id = ID_EDIT,
            icon = Icons.Outlined.Edit,
            label = "Edit home",
            onClick = { vm.openLayer(Layer.Edit) },
            onWallpaper = true,
            onFocused = { vm.onControlFocused(ID_EDIT) },
        )
        PlutoIconButton(
            id = ID_SETTINGS,
            icon = Icons.Outlined.Settings,
            label = "Launcher settings",
            onClick = { vm.openLayer(Layer.Settings) },
            onWallpaper = true,
            onFocused = { vm.onControlFocused(ID_SETTINGS) },
        )
    }
}

/** The five dock slots in a row, on a panel. */
@Composable
internal fun HorizontalDock(dock: List<AppEntry?>, vm: LauncherViewModel, iconSize: Dp, modifier: Modifier = Modifier) {
    PlutoPanel(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Organization.DOCK_SLOTS) { slot ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DockSlot(slot, dock.getOrNull(slot), vm, iconSize)
                }
            }
        }
    }
}

@Composable
internal fun EmptyFavourites(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    EmptyState(
        title = "No favourites yet",
        detail = HomeText.EMPTY_FAVOURITES,
        onWallpaper = true,
        icon = Icons.Outlined.Star,
        modifier = modifier,
    ) {
        PlutoTextButton(id = "home:empty:edit", text = "Add favourite", onClick = { vm.openLayer(Layer.Edit) }, emphasized = true)
    }
}
