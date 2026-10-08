package dev.pluto.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.ui.motion.LocalDrawerReveal

/**
 * Portrait home: clock and date, a toolbar (Search, Edit, Settings), the favourites grid,
 * an "All apps" button (swipe up works too) and the five-slot dock.
 */
@Composable
fun PhoneLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val reveal = LocalDrawerReveal.current
    val liftPx = rememberDensityPx(HandleLift)
    val iconSize = state.iconSize()
    val gridKeys = state.homeTiles.map { it.id }
    val gridState = rememberAnchoredGridState(HOME_SURFACE, state, gridKeys)
    ReportScrollAnchor(HOME_SURFACE, gridState, state, vm, gridKeys)
    RestoreHomeFocusEffect(state, focus, gridState, gridKeys)
    TrackFocusOrder(HOME_SURFACE, state.homeTiles.map(::homeTileFocusId)) { gridState.scrollToItem(it) }
    val defaultId = state.homeTiles.firstOrNull()?.let(::homeTileFocusId) ?: ID_ALL_APPS
    SideEffect { focus.setDefaultFocus(defaultId) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // 4 columns on ordinary phones, 5-6 on wide portrait windows; tiles grow with icon size.
        val columns = (maxWidth / (iconSize + 36.dp)).toInt().coerceIn(3, 6)
        Column(
            Modifier
                .fillMaxSize()
                .swipeUpToOpenDrawer(vm),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ClockHeader(Modifier.weight(1f).padding(top = 8.dp))
                HomeToolbar(vm)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.homeTiles.isEmpty()) {
                    EmptyFavourites(vm, Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.homeTiles, key = { it.id }) { tile ->
                            HomeTileView(tile, iconSize, homeTileFocusId(tile), vm, plutoItem())
                        }
                    }
                }
            }

            PlutoIconButton(
                id = ID_ALL_APPS,
                icon = Icons.Outlined.KeyboardArrowUp,
                label = "All apps",
                onClick = { vm.openDrawer() },
                showLabel = true,
                onWallpaper = true,
                onFocused = { vm.onControlFocused(ID_ALL_APPS) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp)
                    // The handle rises and fades as the drawer is pulled up over it.
                    .graphicsLayer {
                        val p = reveal.progress
                        translationY = -p * liftPx
                        alpha = 1f - p
                    },
            )

            HorizontalDock(
                state, vm, iconSize,
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
}

/** Search, Edit and Settings as visible buttons (no long-press needed). */
@Composable
internal fun HomeToolbar(vm: LauncherViewModel, modifier: Modifier = Modifier, includeAllApps: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PlutoIconButton(
            id = ID_SEARCH,
            icon = Icons.Outlined.Search,
            label = "Search apps",
            onClick = { vm.openDrawer(withSearch = true) },
            onWallpaper = true,
            onFocused = { vm.onControlFocused(ID_SEARCH) },
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (includeAllApps) {
            PlutoIconButton(
                id = ID_ALL_APPS,
                icon = Icons.Outlined.KeyboardArrowUp,
                label = "All apps",
                onClick = { vm.openDrawer() },
                showLabel = true,
                onWallpaper = true,
                onFocused = { vm.onControlFocused(ID_ALL_APPS) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        PlutoIconButton(
            id = ID_EDIT,
            icon = Icons.Outlined.Edit,
            label = "Edit home",
            onClick = { vm.openLayer(Layer.Edit) },
            onWallpaper = true,
            onFocused = { vm.onControlFocused(ID_EDIT) },
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        PlutoIconButton(
            id = ID_SETTINGS,
            icon = Icons.Outlined.Settings,
            label = "Launcher settings",
            onClick = { vm.openLayer(Layer.Settings) },
            onWallpaper = true,
            onFocused = { vm.onControlFocused(ID_SETTINGS) },
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** The five dock slots in a row, on a translucent panel. */
@Composable
internal fun HorizontalDock(state: LauncherUiState, vm: LauncherViewModel, iconSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    PlutoPanel(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(Organization.DOCK_SLOTS) { slot ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    DockSlot(slot, state, vm, iconSize)
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
        modifier = modifier,
    ) {
        PlutoTextButton(id = "home:empty:edit", text = "Add favourite", onClick = { vm.openLayer(Layer.Edit) })
        PlutoTextButton(id = "home:empty:allapps", text = "All apps", onClick = { vm.openDrawer() }, emphasized = true)
    }
}
