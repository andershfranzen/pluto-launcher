package dev.pluto.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
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
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.LocalShowTouchSelection
import dev.pluto.launcher.ui.motion.LocalAppLauncher
import dev.pluto.launcher.ui.motion.PlutoMotion
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
 * Controller-first landscape layout ("console mode"): category tabs (L1/R1 or touch), a
 * favourites row, a "Recent launches" row, and a large-tile grid of the active category. The
 * selected app (state.session.selectedApp) is restored after rotation, reflow or category
 * change. The selection stays visible while touch drives (a thin outline), and the button
 * legend stays visible as long as a controller is connected.
 *
 * Motion: switching category slides the whole page below the tabs, direction-aware (the
 * outgoing page is focus-inert while it leaves, so focus always lands on the incoming one);
 * rows scroll like a carousel with a tile of look-ahead; the selected app's name under the
 * rows changes with a short slide; sections settle in when the layout first appears.
 */
@Composable
fun HandheldLayout(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val outerInert = LocalFocusInert.current
    val iconSize = state.iconSize(base = 68.dp)
    val showRecents = state.settings.historyEnabled && state.recents.isNotEmpty()
    val showFavourites = state.homeTiles.isNotEmpty()
    val mapping = state.activeMapping()
    val hints = showControllerHints(state, focus, LauncherMode.HANDHELD)
    val currentState by rememberUpdatedState(state)

    // Grid item keys, in display order; also used as logical scroll anchors.
    val headerKeys = buildList {
        if (showFavourites) addAll(listOf(KEY_FAV_HEADER, KEY_FAV_ROW))
        if (showRecents) addAll(listOf(KEY_RECENT_HEADER, KEY_RECENT_ROW))
        add(KEY_CAT_HEADER)
    }
    val gridKeys = headerKeys + if (state.categoryApps.isEmpty()) listOf(KEY_CAT_EMPTY) else state.categoryApps.map { catId(it.key) }
    val favKeys = state.homeTiles.map { it.id }
    val recentKeys = state.recents.map { it.key.encode() }

    // Every category page owns its scroll states (two pages overlap while sliding); the first
    // page starts at the persisted anchors. Anchors, focus order and restores use the active page.
    val anchoredGrid = rememberAnchoredGridState(SURFACE, state, gridKeys)
    val anchoredFav = rememberAnchoredListState(FAV_SURFACE, state, favKeys)
    val anchoredRecent = rememberAnchoredListState(RECENT_SURFACE, state, recentKeys)
    val pager = remember { HandheldPager(HandheldPageStates(anchoredGrid, anchoredFav, anchoredRecent)) }
    val page = pager.active
    ReportScrollAnchor(SURFACE, page.grid, state, vm, gridKeys)
    ReportListScrollAnchor(FAV_SURFACE, page.fav, vm)
    ReportListScrollAnchor(RECENT_SURFACE, page.recent, vm)

    // Ordered ids per row, so a vanished app hands focus to its neighbour in the same row.
    val currentHeaders by rememberUpdatedState(headerKeys)
    TrackFocusOrder(FOCUS_FAV, state.homeTiles.map(::favId)) { index ->
        val p = pager.active
        p.grid.scrollIfHidden(currentHeaders.indexOf(KEY_FAV_ROW))
        p.fav.scrollToItem(index)
    }
    TrackFocusOrder(FOCUS_RECENT, if (showRecents) state.recents.map { recentId(it.key) } else emptyList()) { index ->
        val p = pager.active
        p.grid.scrollIfHidden(currentHeaders.indexOf(KEY_RECENT_ROW))
        p.recent.scrollToItem(index)
    }
    TrackFocusOrder(FOCUS_CAT, state.categoryApps.map { catId(it.key) }) { index ->
        pager.active.grid.scrollToItem(currentHeaders.size + index)
    }

    val defaultId = state.categoryApps.firstOrNull()?.let { catId(it.key) }
        ?: state.activeCategory?.let { "$TAB_PREFIX:${it.id}" }
    SideEffect { focus.setDefaultFocus(defaultId) }

    // Remember what held focus at the moment the category changed, before the outgoing page
    // turns focus-inert (read without subscribing, so focus moves never recompose this).
    val categoryId = state.activeCategory?.id
    val switch = remember { CategorySwitchState(categoryId) }
    if (switch.categoryId != categoryId) {
        switch.categoryId = categoryId
        switch.focusedAtSwitch = Snapshot.withoutReadObservation { focus.focusedId }
    }

    HandheldSelectionEffects(state, focus, pager, headerKeys, switch)

    // Sections settle in, top to bottom, when the layout appears.
    val sections = rememberStagger(animate = true, stepMs = 45, maxSlots = 4)

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

            AnimatedContent(
                targetState = categoryId,
                transitionSpec = {
                    val categories = currentState.categories
                    val from = categories.indexOfFirst { it.id == initialState }
                    val to = categories.indexOfFirst { it.id == targetState }
                    categorySlide(if (to >= from) 1 else -1)
                },
                label = "handheldCategory",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { pageId ->
                val active = pageId == categoryId
                val states = remember { pager.newPage(catHeaderIndex = headerKeys.size - 1) }
                if (active) SideEffect { pager.active = states }
                // The outgoing page keeps showing its own category while it slides away.
                val frozenName = remember { arrayOf(state.activeCategory?.name) }
                val frozenApps = remember { arrayOf(state.categoryApps) }
                if (active) {
                    frozenName[0] = state.activeCategory?.name
                    frozenApps[0] = state.categoryApps
                }
                CompositionLocalProvider(LocalFocusInert provides (outerInert || !active)) {
                    HandheldPage(
                        state = state,
                        vm = vm,
                        states = states,
                        categoryName = frozenName[0],
                        categoryApps = frozenApps[0],
                        iconSize = iconSize,
                        showFavourites = showFavourites,
                        showRecents = showRecents,
                        sections = sections,
                    )
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
                    SelectedAppLabel(selectedLabel.orEmpty(), Modifier.weight(1f))
                    ButtonLegend(mapping, Legends.Handheld)
                }
            }
        }
    }
}

/** One category page: Favourites, Recent launches and the category's tiles, in one lazy grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HandheldPage(
    state: LauncherUiState,
    vm: LauncherViewModel,
    states: HandheldPageStates,
    categoryName: String?,
    categoryApps: List<AppEntry>,
    iconSize: Dp,
    showFavourites: Boolean,
    showRecents: Boolean,
    sections: Stagger,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = iconSize + 64.dp),
        state = states.grid,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showFavourites) {
            item(key = KEY_FAV_HEADER, span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Favourites", plutoItem().staggered(sections, 0))
            }
            item(key = KEY_FAV_ROW, span = { GridItemSpan(maxLineSpan) }) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides CarouselBringIntoViewSpec) {
                    LazyRow(
                        state = states.fav,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = plutoItem().staggered(sections, 0),
                    ) {
                        items(state.homeTiles, key = { it.id }) { tile ->
                            HomeTileView(tile, iconSize * 0.85f, favId(tile), vm, plutoItem().width(iconSize + 44.dp))
                        }
                    }
                }
            }
        }
        if (showRecents) {
            item(key = KEY_RECENT_HEADER, span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("Recent launches", plutoItem().staggered(sections, 1))
            }
            item(key = KEY_RECENT_ROW, span = { GridItemSpan(maxLineSpan) }) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides CarouselBringIntoViewSpec) {
                    LazyRow(
                        state = states.recent,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = plutoItem().staggered(sections, 1),
                    ) {
                        items(state.recents, key = { it.key.encode() }) { entry ->
                            HandheldTile(entry, iconSize * 0.85f, recentId(entry.key), plutoItem().width(iconSize + 44.dp), vm)
                        }
                    }
                }
            }
        }
        item(key = KEY_CAT_HEADER, span = { GridItemSpan(maxLineSpan) }) {
            val name = categoryName ?: "All apps"
            val count = categoryApps.size
            SectionHeader("$name · " + if (count == 1) "1 app" else "$count apps", plutoItem().staggered(sections, 2))
        }
        if (categoryApps.isEmpty()) {
            item(key = KEY_CAT_EMPTY, span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    title = "No apps in ${categoryName ?: "this category"} yet",
                    detail = "Open an app's actions (X, press and hold, or Actions in All apps) and choose " +
                        "Categories…, or manage categories in Settings.",
                    onWallpaper = true,
                    modifier = plutoItem().staggered(sections, 3),
                ) {
                    PlutoTextButton("hh:empty:categories", "Categories", { vm.openLayer(Layer.Categories) })
                    PlutoTextButton("hh:empty:allapps", "All apps", { vm.openDrawer() }, emphasized = true)
                }
            }
        } else {
            items(categoryApps, key = { catId(it.key) }) { entry ->
                HandheldTile(entry, iconSize, catId(entry.key), plutoItem().staggered(sections, 3), vm)
            }
        }
    }
}

/**
 * The selected app's name under the rows. Changes slide up quickly; while the D-pad is held
 * every new name simply retargets (AnimatedContent keeps no queue, only the latest wins).
 */
@Composable
private fun SelectedAppLabel(label: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = label,
        transitionSpec = {
            (fadeIn(tween(PlutoMotion.SHORT_MS, easing = PlutoMotion.EmphasizedDecelerate)) + slideInVertically(PlutoMotion.slideSpring) { it / 3 }) togetherWith
                fadeOut(tween(PlutoMotion.SHORT_MS / 2, easing = PlutoMotion.EmphasizedAccelerate)) using null
        },
        contentAlignment = Alignment.CenterStart,
        label = "selectedApp",
        modifier = modifier,
    ) { text ->
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold).overWallpaper(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Scroll states of one category page. */
@Stable
private class HandheldPageStates(val grid: LazyGridState, val fav: LazyListState, val recent: LazyListState)

/** Hands out page states; [active] belongs to the page being shown (not the one leaving). */
@Stable
private class HandheldPager(private val initial: HandheldPageStates) {
    private var initialUsed = false
    var active by mutableStateOf(initial)

    /**
     * The first page resumes at the persisted anchors. A later page continues from where the
     * active one is, so the rows do not jump; if the grid was scrolled into the category, the
     * new category starts at its header.
     */
    fun newPage(catHeaderIndex: Int): HandheldPageStates {
        if (!initialUsed) {
            initialUsed = true
            return initial
        }
        val from = Snapshot.withoutReadObservation { active }
        val grid = from.grid
        val gridState = if (grid.firstVisibleItemIndex > catHeaderIndex) {
            LazyGridState(catHeaderIndex.coerceAtLeast(0), 0)
        } else {
            LazyGridState(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        }
        return HandheldPageStates(
            gridState,
            LazyListState(from.fav.firstVisibleItemIndex, from.fav.firstVisibleItemScrollOffset),
            LazyListState(from.recent.firstVisibleItemIndex, from.recent.firstVisibleItemScrollOffset),
        )
    }
}

/** Composition-time bookkeeping of the last category switch (plain fields, never observed). */
private class CategorySwitchState(var categoryId: Long?) {
    var focusedAtSwitch: String? = null
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
private fun HandheldTile(entry: AppEntry, iconSize: Dp, focusId: String, modifier: Modifier, vm: LauncherViewModel) {
    val launcher = LocalAppLauncher.current
    AppTile(
        entry = entry,
        iconSize = iconSize,
        onLaunch = { launcher.launch(entry.key, focusId) },
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
    pager: HandheldPager,
    headerKeys: List<String>,
    switch: CategorySwitchState,
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
        val page = pager.active
        val gridState = page.grid
        val favState = page.fav
        val recentState = page.recent

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
    // selection if it is a member, else select the first app of the incoming page — unless
    // the user is elsewhere (tabs, a row), where focus stays, moving to the same control on
    // the incoming page if it was on the outgoing one.
    val categoryId = state.activeCategory?.id
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(categoryId) {
        if (firstRun[0]) {
            firstRun[0] = false
            return@LaunchedEffect
        }
        val focused = switch.focusedAtSwitch
        switch.focusedAtSwitch = null
        withFrameNanos { }
        if (currentState.session.layers.isNotEmpty()) return@LaunchedEffect
        if (focused == null || focused.startsWith("$FOCUS_CAT:")) {
            restore(preferCategory = true)
        } else if (focus.inputMode == InputMode.CONTROLLER) {
            if (focus.focusedId != focused) focus.requestFocusWhenReady(focused)
            pager.active.grid.scrollIfHidden(currentHeaders.indexOf(KEY_CAT_HEADER))
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
