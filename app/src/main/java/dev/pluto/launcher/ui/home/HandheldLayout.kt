package dev.pluto.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppTile
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.CategoryTabs
import dev.pluto.launcher.ui.components.ClockHeader
import dev.pluto.launcher.ui.components.EmptyState
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.SectionHeader
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.components.promptFor
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.theme.overWallpaper

private const val SURFACE = "handheld"
private const val TAB_PREFIX = "hh:tab"
private const val KEY_FAV_HEADER = "hh:h:fav"
private const val KEY_FAV_ROW = "hh:favrow"
private const val KEY_RECENT_HEADER = "hh:h:recent"
private const val KEY_RECENT_ROW = "hh:recentrow"
private const val KEY_CAT_HEADER = "hh:h:cat"
private const val KEY_CAT_EMPTY = "hh:catempty"
private const val ID_HH_SEARCH = "hh:search"
private const val ID_HH_ALL_APPS = "hh:allapps"
private const val ID_HH_SETTINGS = "hh:settings"

private fun favId(tile: HomeTile) = "hh:fav:${tile.id}"
private fun recentId(key: AppKey) = "hh:recent:${key.encode()}"
private fun catId(key: AppKey) = "hh:cat:${key.encode()}"

/**
 * Controller-first landscape layout: category tabs (L1/R1 or touch), a favourites row,
 * a "Recent launches" row, and a large-tile grid of the active category. The selected app
 * (state.session.selectedApp) is restored after rotation, reflow or category change.
 */
@Composable
fun HandheldLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val iconSize = state.iconSize(base = 68.dp)
    val showRecents = state.settings.historyEnabled && state.recents.isNotEmpty()
    val showFavourites = state.homeTiles.isNotEmpty()
    val mapping = state.activeMapping()
    val hints = showControllerHints(state, focus)

    // Grid item keys, in display order; also used as logical scroll anchors.
    val headerKeys = buildList {
        if (showFavourites) addAll(listOf(KEY_FAV_HEADER, KEY_FAV_ROW))
        if (showRecents) addAll(listOf(KEY_RECENT_HEADER, KEY_RECENT_ROW))
        add(KEY_CAT_HEADER)
    }
    val gridKeys = headerKeys + if (state.categoryApps.isEmpty()) listOf(KEY_CAT_EMPTY) else state.categoryApps.map { catId(it.key) }
    val gridState = rememberAnchoredGridState(SURFACE, state, gridKeys)
    val favState = rememberLazyListState()
    val recentState = rememberLazyListState()
    ReportScrollAnchor(SURFACE, gridState, vm)

    val defaultId = state.categoryApps.firstOrNull()?.let { catId(it.key) }
        ?: state.activeCategory?.let { "$TAB_PREFIX:${it.id}" }
    SideEffect { focus.setDefaultFocus(defaultId) }

    HandheldSelectionEffects(state, focus, gridState, favState, recentState, headerKeys)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryTabs(
                categories = state.categories,
                selected = state.activeCategory,
                onSelect = { vm.selectCategory(if (it.isAll) null else it.id) },
                idPrefix = TAB_PREFIX,
                prevKey = if (hints) mapping.promptFor(ControllerAction.PREV_CATEGORY) else null,
                nextKey = if (hints) mapping.promptFor(ControllerAction.NEXT_CATEGORY) else null,
                onFocused = { vm.onControlFocused("$TAB_PREFIX:${it.id}") },
                modifier = Modifier.weight(1f),
            )
            ClockHeader(Modifier.padding(horizontal = 12.dp), compact = true)
            PlutoIconButton(ID_HH_SEARCH, Icons.Outlined.Search, "Search apps", { vm.openDrawer(withSearch = true) }, onWallpaper = true)
            PlutoIconButton(ID_HH_ALL_APPS, Icons.Outlined.Apps, "All apps", { vm.openDrawer() }, onWallpaper = true)
            PlutoIconButton(ID_HH_SETTINGS, Icons.Outlined.Settings, "Launcher settings", { vm.openLayer(Layer.Settings) }, onWallpaper = true)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = iconSize + 64.dp),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (showFavourites) {
                item(key = KEY_FAV_HEADER, span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Favourites") }
                item(key = KEY_FAV_ROW, span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        state = favState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.homeTiles, key = { it.id }) { tile ->
                            HomeTileView(tile, iconSize * 0.85f, favId(tile), vm, Modifier.width(iconSize + 44.dp))
                        }
                    }
                }
            }
            if (showRecents) {
                item(key = KEY_RECENT_HEADER, span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Recent launches") }
                item(key = KEY_RECENT_ROW, span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        state = recentState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.recents, key = { it.key.encode() }) { entry ->
                            HandheldTile(entry, iconSize * 0.85f, recentId(entry.key), vm, Modifier.width(iconSize + 44.dp))
                        }
                    }
                }
            }
            item(key = KEY_CAT_HEADER, span = { GridItemSpan(maxLineSpan) }) {
                val name = state.activeCategory?.name ?: "All apps"
                val count = state.categoryApps.size
                SectionHeader("$name · " + if (count == 1) "1 app" else "$count apps")
            }
            if (state.categoryApps.isEmpty()) {
                item(key = KEY_CAT_EMPTY, span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "No apps in ${state.activeCategory?.name ?: "this category"} yet",
                        detail = "Add apps from an app's Actions → Categories, or manage categories in Settings.",
                        onWallpaper = true,
                    ) {
                        PlutoTextButton("hh:empty:categories", "Categories", { vm.openLayer(Layer.Categories) })
                        PlutoTextButton("hh:empty:allapps", "All apps", { vm.openDrawer() }, emphasized = true)
                    }
                }
            } else {
                items(state.categoryApps, key = { catId(it.key) }) { entry ->
                    HandheldTile(entry, iconSize, catId(entry.key), vm)
                }
            }
        }

        if (hints) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val selectedLabel = state.session.selectedApp?.let { key -> state.allApps.firstOrNull { it.key == key }?.label }
                Text(
                    selectedLabel.orEmpty(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold).overWallpaper(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ButtonLegend(mapping, Legends.Handheld)
            }
        }
    }
}

@Composable
private fun HandheldTile(entry: AppEntry, iconSize: Dp, focusId: String, vm: LauncherViewModel, modifier: Modifier = Modifier) {
    AppTile(
        entry = entry,
        iconSize = iconSize,
        onLaunch = { vm.launch(entry.key) },
        onActions = { vm.openLayer(Layer.AppActions(entry.key)) },
        modifier = modifier,
        focusId = focusId,
        onFocused = {
            vm.onAppSelected(entry.key)
            vm.onControlFocused(focusId)
        },
    )
}

/**
 * Keeps the selected app focused across appearance, rotation, category changes and
 * catalogue changes. Rows/grid are scrolled to the target first so it is composed.
 */
@Composable
private fun HandheldSelectionEffects(
    state: LauncherUiState,
    focus: ControllerFocusController,
    gridState: LazyGridState,
    favState: LazyListState,
    recentState: LazyListState,
    headerKeys: List<String>,
) {
    val currentState by rememberUpdatedState(state)
    val currentHeaders by rememberUpdatedState(headerKeys)

    suspend fun restore(preferCategory: Boolean) {
        val s = currentState
        val selected = s.session.selectedApp
        val catIndex = s.categoryApps.indexOfFirst { it.key == selected }
        val favIndex = s.homeTiles.indexOfFirst { it is HomeTile.App && it.entry.key == selected }
        val recentIndex = if (s.settings.historyEnabled) s.recents.indexOfFirst { it.key == selected } else -1
        val control = s.session.focusedControlId

        val target: String
        when {
            !preferCategory && control != null && control.startsWith("hh:") && focus.requestFocus(control) -> return
            catIndex >= 0 -> {
                gridState.scrollIfHidden(currentHeaders.size + catIndex)
                target = catId(s.categoryApps[catIndex].key)
            }
            !preferCategory && favIndex >= 0 -> {
                gridState.scrollIfHidden(currentHeaders.indexOf(KEY_FAV_ROW))
                favState.scrollIfHidden(favIndex)
                target = favId(s.homeTiles[favIndex])
            }
            !preferCategory && recentIndex >= 0 -> {
                gridState.scrollIfHidden(currentHeaders.indexOf(KEY_RECENT_ROW))
                recentState.scrollIfHidden(recentIndex)
                target = recentId(s.recents[recentIndex].key)
            }
            s.categoryApps.isNotEmpty() -> {
                gridState.scrollIfHidden(currentHeaders.size)
                target = catId(s.categoryApps.first().key)
            }
            else -> return
        }
        if (focus.inputMode == InputMode.CONTROLLER) focus.requestFocusWhenReady(target) else focus.rememberFocusTarget(target)
    }

    // Layout (re)appears: after the first frame, unless focus was already placed by a layer restore.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (currentState.session.layers.isEmpty() && focus.focusedId == null) restore(preferCategory = false)
    }

    // Category changed (L1/R1 or tab): bring the grid back to the category and keep the
    // selection if it is a member, else select the first app — unless the user is on the tabs.
    val categoryId = state.activeCategory?.id
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(categoryId) {
        if (firstRun[0]) {
            firstRun[0] = false
            return@LaunchedEffect
        }
        withFrameNanos { }
        val focused = focus.focusedId
        if (currentState.session.layers.isNotEmpty()) return@LaunchedEffect
        if (focused == null || focused.startsWith("hh:cat:")) {
            restore(preferCategory = true)
        } else {
            gridState.scrollIfHidden(currentHeaders.indexOf(KEY_CAT_HEADER))
        }
    }

    // The focused app vanished (uninstall, hide): the VM moves the selection to a neighbour; follow it.
    val categoryKeys = state.categoryApps.map { it.key }
    val firstKeysRun = remember { booleanArrayOf(true) }
    LaunchedEffect(categoryKeys) {
        if (firstKeysRun[0]) {
            firstKeysRun[0] = false
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (currentState.session.layers.isEmpty() && focus.inputMode == InputMode.CONTROLLER && focus.focusedId == null) {
            restore(preferCategory = false)
        }
    }
}

private suspend fun LazyGridState.scrollIfHidden(index: Int) {
    if (index < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}

private suspend fun LazyListState.scrollIfHidden(index: Int) {
    if (index < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
}
