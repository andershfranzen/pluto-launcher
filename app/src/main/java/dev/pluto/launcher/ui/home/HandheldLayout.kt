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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.LauncherMode
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
import dev.pluto.launcher.ui.focus.LocalShowTouchSelection
import dev.pluto.launcher.ui.theme.overWallpaper

private const val SURFACE = "handheld"
private const val FAV_SURFACE = "handheld:fav"
private const val RECENT_SURFACE = "handheld:recent"
private const val FOCUS_FAV = "hh:fav"
private const val FOCUS_RECENT = "hh:recent"
private const val FOCUS_CAT = "hh:cat"

/** Category tabs stay in the header row only while they get at least this much room (or all they need). */
private val MinInlineTabsWidth = 320.dp
private val HeaderGap = 12.dp
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
 * The selection stays visible while touch drives (a thin outline), and the button legend
 * stays visible as long as a controller is connected.
 */
@Composable
fun HandheldLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val iconSize = state.iconSize(base = 68.dp)
    val showRecents = state.settings.historyEnabled && state.recents.isNotEmpty()
    val showFavourites = state.homeTiles.isNotEmpty()
    val mapping = state.activeMapping()
    val hints = showControllerHints(state, focus, LauncherMode.HANDHELD)

    // Grid item keys, in display order; also used as logical scroll anchors.
    val headerKeys = buildList {
        if (showFavourites) addAll(listOf(KEY_FAV_HEADER, KEY_FAV_ROW))
        if (showRecents) addAll(listOf(KEY_RECENT_HEADER, KEY_RECENT_ROW))
        add(KEY_CAT_HEADER)
    }
    val gridKeys = headerKeys + if (state.categoryApps.isEmpty()) listOf(KEY_CAT_EMPTY) else state.categoryApps.map { catId(it.key) }
    val gridState = rememberAnchoredGridState(SURFACE, state, gridKeys)
    val favKeys = state.homeTiles.map { it.id }
    val recentKeys = state.recents.map { it.key.encode() }
    val favState = rememberAnchoredListState(FAV_SURFACE, state, favKeys)
    val recentState = rememberAnchoredListState(RECENT_SURFACE, state, recentKeys)
    ReportScrollAnchor(SURFACE, gridState, state, vm, gridKeys)
    ReportListScrollAnchor(FAV_SURFACE, favState, vm)
    ReportListScrollAnchor(RECENT_SURFACE, recentState, vm)

    // Ordered ids per row, so a vanished app hands focus to its neighbour in the same row.
    val currentHeaders by rememberUpdatedState(headerKeys)
    TrackFocusOrder(FOCUS_FAV, state.homeTiles.map(::favId)) { index ->
        gridState.scrollIfHidden(currentHeaders.indexOf(KEY_FAV_ROW))
        favState.scrollToItem(index)
    }
    TrackFocusOrder(FOCUS_RECENT, if (showRecents) state.recents.map { recentId(it.key) } else emptyList()) { index ->
        gridState.scrollIfHidden(currentHeaders.indexOf(KEY_RECENT_ROW))
        recentState.scrollToItem(index)
    }
    TrackFocusOrder(FOCUS_CAT, state.categoryApps.map { catId(it.key) }) { index ->
        gridState.scrollToItem(currentHeaders.size + index)
    }

    val defaultId = state.categoryApps.firstOrNull()?.let { catId(it.key) }
        ?: state.activeCategory?.let { "$TAB_PREFIX:${it.id}" }
    SideEffect { focus.setDefaultFocus(defaultId) }

    HandheldSelectionEffects(state, focus, gridState, favState, recentState, headerKeys)

    CompositionLocalProvider(LocalShowTouchSelection provides true) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            HandheldHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp),
                tabs = {
                    CategoryTabs(
                        categories = state.categories,
                        selected = state.activeCategory,
                        onSelect = { vm.selectCategory(if (it.isAll) null else it.id) },
                        idPrefix = TAB_PREFIX,
                        prevKey = if (hints) mapping.promptFor(ControllerAction.PREV_CATEGORY) else null,
                        nextKey = if (hints) mapping.promptFor(ControllerAction.NEXT_CATEGORY) else null,
                        onFocused = { vm.onControlFocused("$TAB_PREFIX:${it.id}") },
                    )
                },
                clock = { ClockHeader(compact = true) },
                buttons = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlutoIconButton(ID_HH_SEARCH, Icons.Outlined.Search, "Search apps", { vm.openDrawer(withSearch = true) }, onWallpaper = true)
                        PlutoIconButton(ID_HH_ALL_APPS, Icons.Outlined.Apps, "All apps", { vm.openDrawer() }, onWallpaper = true)
                        PlutoIconButton(ID_HH_SETTINGS, Icons.Outlined.Settings, "Launcher settings", { vm.openLayer(Layer.Settings) }, onWallpaper = true)
                    }
                },
            )

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
                            detail = "Open an app's actions (X, press and hold, or Actions in All apps) and choose " +
                                "Categories…, or manage categories in Settings.",
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
}

/**
 * Header with category tabs, the compact clock and the icon buttons. The buttons always
 * keep their 48dp targets; the clock gets what is left (and ellipsizes). The tabs share the
 * row only while they fit (or get at least [MinInlineTabsWidth] to scroll in); otherwise,
 * e.g. at large text sizes, they move to their own full-width row below.
 */
@Composable
private fun HandheldHeader(
    modifier: Modifier,
    tabs: @Composable () -> Unit,
    clock: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
) {
    Layout(contents = listOf(tabs, clock, buttons), modifier = modifier) { (tabsM, clockM, buttonsM), constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = HeaderGap.roundToPx()
        val buttonsP = buttonsM.first().measure(loose)
        val clockP = clockM.first().measure(loose.copy(maxWidth = (width - buttonsP.width - gap).coerceAtLeast(0)))
        val tabsMeasurable = tabsM.first()
        val inlineWidth = width - buttonsP.width - clockP.width - gap * 2
        val tabsWanted = tabsMeasurable.maxIntrinsicWidth(constraints.maxHeight.takeIf { it != Int.MAX_VALUE } ?: 0)
        val inline = inlineWidth > 0 && inlineWidth >= minOf(tabsWanted, MinInlineTabsWidth.roundToPx())
        val tabsP = tabsMeasurable.measure(loose.copy(maxWidth = if (inline) inlineWidth else width))
        if (inline) {
            val height = maxOf(tabsP.height, clockP.height, buttonsP.height)
            layout(width, height) {
                tabsP.place(0, (height - tabsP.height) / 2)
                clockP.place(width - buttonsP.width - gap - clockP.width, (height - clockP.height) / 2)
                buttonsP.place(width - buttonsP.width, (height - buttonsP.height) / 2)
            }
        } else {
            val top = maxOf(clockP.height, buttonsP.height)
            layout(width, top + tabsP.height) {
                clockP.place(0, (top - clockP.height) / 2)
                buttonsP.place(width - buttonsP.width, (top - buttonsP.height) / 2)
                tabsP.place(0, top)
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
 * controller connection. In controller mode rows/grid are scrolled to the target first so
 * it is composed; while touch drives, the target is only remembered (and outlined), never
 * scrolled to, so the layout opens at the top with Favourites and Recent launches visible.
 * Vanished apps are handled by [TrackFocusOrder] (nearest neighbour in the same row).
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
        val controllerMode = focus.inputMode == InputMode.CONTROLLER
        val selected = s.session.selectedApp
        val catIndex = s.categoryApps.indexOfFirst { it.key == selected }
        val favIndex = s.homeTiles.indexOfFirst { it is HomeTile.App && it.entry.key == selected }
        val recentIndex = if (s.settings.historyEnabled) s.recents.indexOfFirst { it.key == selected } else -1
        val control = s.session.focusedControlId?.takeIf { it.startsWith("hh:") }

        if (!preferCategory && control != null) {
            if (focus.isRegistered(control)) {
                if (controllerMode) focus.requestFocus(control) else focus.rememberFocusTarget(control)
                return
            }
            // The remembered row item vanished: its nearest neighbour in the same row.
            val neighbour = focus.replacementFor(control)
            if (neighbour != null && neighbour != control) {
                if (!controllerMode) {
                    focus.rememberFocusTarget(neighbour)
                    return
                }
                if (focus.focusOrNeighbour(control)) return
            }
        }

        val inFav = control?.startsWith("$FOCUS_FAV:") == true
        val inRecent = control?.startsWith("$FOCUS_RECENT:") == true
        val target: String
        var scroll: (suspend () -> Unit)? = null
        when {
            !preferCategory && inFav && favIndex >= 0 -> {
                target = favId(s.homeTiles[favIndex])
                scroll = { gridState.scrollIfHidden(currentHeaders.indexOf(KEY_FAV_ROW)); favState.scrollIfHidden(favIndex) }
            }
            !preferCategory && inRecent && recentIndex >= 0 -> {
                target = recentId(s.recents[recentIndex].key)
                scroll = { gridState.scrollIfHidden(currentHeaders.indexOf(KEY_RECENT_ROW)); recentState.scrollIfHidden(recentIndex) }
            }
            // Selection came from another layout (no Handheld control remembered): prefer the
            // rows at the top so entering Handheld never opens scrolled past Favourites.
            !preferCategory && control == null && favIndex >= 0 -> {
                target = favId(s.homeTiles[favIndex])
                scroll = { gridState.scrollIfHidden(currentHeaders.indexOf(KEY_FAV_ROW)); favState.scrollIfHidden(favIndex) }
            }
            !preferCategory && control == null && recentIndex >= 0 -> {
                target = recentId(s.recents[recentIndex].key)
                scroll = { gridState.scrollIfHidden(currentHeaders.indexOf(KEY_RECENT_ROW)); recentState.scrollIfHidden(recentIndex) }
            }
            catIndex >= 0 -> {
                target = catId(s.categoryApps[catIndex].key)
                scroll = { gridState.scrollIfHidden(currentHeaders.size + catIndex) }
            }
            !preferCategory && favIndex >= 0 -> {
                target = favId(s.homeTiles[favIndex])
                scroll = { gridState.scrollIfHidden(currentHeaders.indexOf(KEY_FAV_ROW)); favState.scrollIfHidden(favIndex) }
            }
            !preferCategory && recentIndex >= 0 -> {
                target = recentId(s.recents[recentIndex].key)
                scroll = { gridState.scrollIfHidden(currentHeaders.indexOf(KEY_RECENT_ROW)); recentState.scrollIfHidden(recentIndex) }
            }
            // Nothing selected yet: the first favourite (top of the screen), else the first app.
            !preferCategory && s.homeTiles.isNotEmpty() -> target = favId(s.homeTiles.first())
            s.categoryApps.isNotEmpty() -> {
                target = catId(s.categoryApps.first().key)
                if (preferCategory) scroll = { gridState.scrollIfHidden(currentHeaders.size) }
            }
            else -> return
        }
        if (controllerMode) {
            scroll?.invoke()
            focus.requestFocusWhenReady(target)
        } else {
            focus.rememberFocusTarget(target)
        }
    }

    // Layout (re)appears or a controller connects: after the first frame, unless focus was
    // already placed by a layer restore.
    LaunchedEffect(state.controllerConnected) {
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
        if (focused == null || focused.startsWith("$FOCUS_CAT:")) {
            restore(preferCategory = true)
        } else if (focus.inputMode == InputMode.CONTROLLER) {
            gridState.scrollIfHidden(currentHeaders.indexOf(KEY_CAT_HEADER))
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
