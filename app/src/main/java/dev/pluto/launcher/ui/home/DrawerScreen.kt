package dev.pluto.launcher.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppTile
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.CategoryTabs
import dev.pluto.launcher.ui.components.EmptyState
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.components.PlutoPanel
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.focusInert
import dev.pluto.launcher.ui.focus.controllerFocusTarget
import dev.pluto.launcher.ui.motion.LocalAppLauncher
import dev.pluto.launcher.ui.motion.LocalDrawerReveal
import dev.pluto.launcher.ui.motion.PlutoMotion

private const val SURFACE = "drawer"

/** Keystrokes whose VM echo the search field still tolerates (see DrawerScreen). */
private const val MAX_PENDING_ECHOES = 32

/** Focus id of the drawer's search field; LauncherRoot focuses it for Y / Search. */
const val DRAWER_SEARCH_ID = "drawer:search"
private const val DR_CLOSE = "drawer:close"
private const val DR_CLEAR = "drawer:clear"
private const val CHIP_PREFIX = "drawer:cat"
private const val DR_ACTIONS = "drawer:actions"

/** Below this panel height, with the keyboard up, the drawer drops everything but search and results. */
private val CompactImeHeight = 300.dp

private fun drawerAppId(key: AppKey) = "drawer:${key.encode()}"

/**
 * The app drawer (layer Drawer): search field with Clear, category filter, and an
 * alphabetical grid of state.drawerApps (filtering and accent-insensitive matching are
 * done by the view model). Shows a useful empty state.
 *
 * Touch reaches App actions through the visible "Actions" toggle (tap it, then tap an
 * app) as well as by pressing and holding an app; controllers use X.
 * When the soft keyboard leaves little room (a phone in landscape), the category tabs and
 * legend step aside and tiles shrink so at least one row of results stays visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val keyboard = LocalSoftwareKeyboardController.current
    val reveal = LocalDrawerReveal.current
    val launcher = LocalAppLauncher.current
    val outerInert = LocalFocusInert.current
    val iconSize = state.iconSize()
    val session = state.session
    val currentState by rememberUpdatedState(state)
    val categoryId = state.activeCategory?.id
    val gridKeys = state.drawerApps.map { drawerAppId(it.key) }
    val currentKeys by rememberUpdatedState(gridKeys)

    // Each category page has its own grid state (pages overlap while sliding); the first
    // one starts at the persisted anchor. Anchors, focus order and scrolling follow the
    // active page.
    val anchoredState = rememberAnchoredGridState(SURFACE, state, gridKeys)
    val pages = remember { DrawerPages(anchoredState) }
    val gridState = pages.active
    val currentGrid by rememberUpdatedState(gridState)
    TrackFocusOrder(SURFACE, gridKeys) { currentGrid.scrollToItem(it) }
    TrackGridNavigation(SURFACE, gridState, gridKeys)
    var actionsMode by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    // The drawer stays composed while closed ("parked" off screen by LauncherRoot, so opening
    // never has to compose it in its first frame), is composed before it is the open layer
    // (pulled up by a finger) and after it closed (animating away); only the open drawer
    // claims default focus.
    val isTop = session.topLayer == Layer.Drawer
    val parked = outerInert.value && Layer.Drawer !in session.layers
    // A parked drawer may be showing only part of its results while it is composed in the
    // background: it must not move the logical scroll anchor.
    if (!parked) ReportScrollAnchor(SURFACE, gridState, state, vm, gridKeys)
    // The drawer stays composed while closed; per-visit modes still end when it closes.
    LaunchedEffect(parked) { if (parked) actionsMode = false }

    // Opened by a button or the controller: the first rows stagger in. Pulled up by a finger
    // the content is already on screen, so it stays put.
    val stagger = rememberStagger(animate = isTop && !reveal.isDragging, stepMs = 30, maxSlots = 5)
    val wasTop = remember { booleanArrayOf(isTop) }
    if (isTop != wasTop[0]) {
        wasTop[0] = isTop
        val hidden = Snapshot.withoutReadObservation { !reveal.isDragging && reveal.progress < 0.05f }
        if (isTop && hidden) stagger.arm()
    }
    LaunchedEffect(isTop) { if (isTop) stagger.replay() }

    // Local field value keeps the cursor/selection stable; the VM holds the text itself.
    var field by remember { mutableStateOf(TextFieldValue(session.searchText, TextRange(session.searchText.length))) }
    // Texts we sent to the VM that it may still echo back, oldest first. Only *other* changes
    // (Clear, Back) overwrite the field: an echo of an older keystroke arriving after newer
    // ones (fast typing, conflated state) must never rewind the field.
    val sent = remember { ArrayDeque<String>().apply { add(session.searchText) } }
    LaunchedEffect(session.searchText) {
        val text = session.searchText
        val echo = sent.indexOf(text)
        if (echo >= 0) {
            // Our own keystroke: drop it and everything older; the field is already ahead.
            repeat(echo) { sent.removeFirst() }
        } else {
            sent.clear()
            sent.add(text)
            field = TextFieldValue(text, TextRange(text.length))
        }
    }

    val firstAppId = gridKeys.firstOrNull()
    val defaultId = if (session.searchActive) DRAWER_SEARCH_ID else firstAppId ?: DRAWER_SEARCH_ID
    val currentDefault by rememberUpdatedState(defaultId)
    SideEffect { if (isTop) focus.setDefaultFocus(defaultId) }

    // On open: Search (button or Y) focuses the field and raises the keyboard; a controller
    // otherwise lands on the first app. Runs once per opening, not when a layer above closes.
    val opened = remember { booleanArrayOf(false) }
    if (Layer.Drawer !in session.layers) opened[0] = false
    LaunchedEffect(isTop) {
        if (!isTop || opened[0]) return@LaunchedEffect
        opened[0] = true
        withFrameNanos { }
        when {
            currentState.session.searchActive -> if (focus.requestFocusWhenReady(DRAWER_SEARCH_ID)) keyboard?.show()
            focus.inputMode == InputMode.CONTROLLER -> focus.requestFocusWhenReady(currentDefault)
        }
    }

    // Back closed the search: drop the keyboard and move focus off the field. Only on a real
    // change: the parked drawer is composed while another layer may own the keyboard.
    val currentFirstApp by rememberUpdatedState(firstAppId)
    val searchWasActive = remember { booleanArrayOf(session.searchActive) }
    LaunchedEffect(session.searchActive) {
        val was = searchWasActive[0]
        searchWasActive[0] = session.searchActive
        if (!session.searchActive && was) {
            keyboard?.hide()
            if (focus.focusedId == DRAWER_SEARCH_ID) {
                currentFirstApp?.let { focus.requestFocus(it) } ?: focus.requestFocus(DR_CLOSE)
            }
        }
    }

    // Search open: results swap in place (see searchResultItem), typing and Clear alike, so
    // Clear fades the full list in instead of swapping it in one frame.
    val searchMode = session.searchActive || session.searchText.isNotEmpty()

    // New search text: show the best matches from the top.
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(session.searchText) {
        if (firstRun[0]) {
            firstRun[0] = false
        } else if (state.drawerApps.isNotEmpty() && (currentGrid.firstVisibleItemIndex != 0 || currentGrid.firstVisibleItemScrollOffset != 0)) {
            // Only when needed: a programmatic scroll drops the grid's item animations, so
            // Clear (or typing) at the top would otherwise swap the results in one frame.
            currentGrid.scrollToItem(0)
        }
    }

    // Category switched (chip, L1/R1): the outgoing page becomes focus-inert, so a controller
    // that was on an app follows to the same app on the incoming page, else its first app.
    val switch = remember { CategorySwitch(categoryId) }
    if (switch.categoryId != categoryId) {
        switch.categoryId = categoryId
        val focused = Snapshot.withoutReadObservation { focus.focusedId }
        switch.focusedApp = focused?.takeIf { it in switch.keys }
    }
    switch.keys = gridKeys
    LaunchedEffect(categoryId) {
        val was = switch.focusedApp ?: return@LaunchedEffect
        switch.focusedApp = null
        withFrameNanos { }
        if (focus.inputMode != InputMode.CONTROLLER || currentState.session.topLayer != Layer.Drawer) return@LaunchedEffect
        val keys = currentKeys
        val target = was.takeIf { it in keys } ?: keys.firstOrNull() ?: DR_CLOSE
        val index = keys.indexOf(target)
        if (index >= 0 && currentGrid.layoutInfo.visibleItemsInfo.none { it.index == index }) currentGrid.scrollToItem(index)
        focus.requestFocusWhenReady(target)
    }

    // Swipe down (on the header, or at the top of the list) pulls the drawer closed.
    val closeDriver = rememberRevealDragDriver(opening = false) { open ->
        if (!open) {
            keyboard?.hide()
            if (currentState.session.searchActive) vm.setSearchActive(false)
            vm.back()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Keyboard up in a short window: keep only the search row and results.
        val compact = imeVisible && maxHeight < CompactImeHeight
        val gap = if (compact) 4.dp else 8.dp
        val tileIcon = if (compact) iconSize * 0.75f else iconSize
        // Same arithmetic as GridCells.Adaptive, for the per-row entrance stagger.
        val columns = ((maxWidth - gap * 2 - 24.dp + 4.dp) / (tileIcon + 36.dp + 4.dp)).toInt().coerceAtLeast(1)
        PlutoPanel(
            Modifier
                .fillMaxSize()
                .padding(gap),
        ) {
            Column(Modifier.fillMaxSize().revealDrag(closeDriver, opening = false)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = if (compact) 4.dp else 12.dp, top = if (compact) 2.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlutoIconButton(DR_CLOSE, Icons.AutoMirrored.Outlined.ArrowBack, "Close all apps", { vm.back() })
                    OutlinedTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            if (it.text != sent.lastOrNull()) {
                                sent.addLast(it.text)
                                if (sent.size > MAX_PENDING_ECHOES) sent.removeFirst()
                                vm.setSearchText(it.text)
                            }
                        },
                        singleLine = true,
                        placeholder = { Text("Search apps") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = if (field.text.isNotEmpty()) {
                            {
                                PlutoIconButton(DR_CLEAR, Icons.Outlined.Clear, "Clear search", { vm.clearSearch() })
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            if (focus.inputMode == InputMode.CONTROLLER) firstAppId?.let(focus::requestFocus)
                        }),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .controllerFocusTarget(
                                id = DRAWER_SEARCH_ID,
                                onActivate = { keyboard?.show() },
                                onFocused = { if (!currentState.session.searchActive) vm.setSearchActive(true) },
                            ),
                    )
                    PlutoIconButton(
                        id = DR_ACTIONS,
                        icon = if (actionsMode) Icons.Outlined.Check else Icons.Outlined.MoreVert,
                        label = if (actionsMode) DrawerText.ACTIONS_DONE else DrawerText.ACTIONS,
                        onClick = { actionsMode = !actionsMode },
                        showLabel = !compact,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                AnimatedVisibility(
                    visible = actionsMode && !compact,
                    enter = ExpandIn,
                    exit = ShrinkOut,
                ) {
                    Text(
                        DrawerText.ACTIONS_HINT,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // The keyboard rising in a short window folds the chips away; they unfold with it.
                AnimatedVisibility(
                    visible = state.categories.size > 1 && !compact,
                    enter = ExpandIn,
                    exit = ShrinkOut,
                ) {
                    CategoryTabs(
                        categories = state.categories,
                        selected = state.activeCategory,
                        onSelect = { vm.selectCategory(if (it.isAll) null else it.id) },
                        idPrefix = CHIP_PREFIX,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Category switches slide the results page (direction-aware); typing only
                // filters inside the page, where items fade and glide (animateItem).
                AnimatedContent(
                    targetState = categoryId,
                    transitionSpec = {
                        val categories = currentState.categories
                        val from = categories.indexOfFirst { it.id == initialState }
                        val to = categories.indexOfFirst { it.id == targetState }
                        // Parked (off screen): follow L1/R1 on Handheld home without animating.
                        if (parked) EnterTransition.None togetherWith ExitTransition.None else categorySlide(if (to >= from) 1 else -1)
                    },
                    label = "drawerCategory",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { pageId ->
                    val active = pageId == categoryId
                    val pageGrid = remember { pages.newPageState() }
                    if (active) SideEffect { pages.active = pageGrid }
                    // The outgoing page keeps showing the apps it had.
                    val frozen = remember { arrayOf(state.drawerApps) }
                    if (active) frozen[0] = state.drawerApps
                    val apps = frozen[0]
                    val firstAtOpen = remember { pageGrid.firstVisibleItemIndex }
                    CompositionLocalProvider(LocalFocusInert provides focusInert(!active)) {
                        Box(Modifier.fillMaxSize()) {
                            if (apps.isEmpty()) {
                                DrawerEmptyState(state, vm, Modifier.align(Alignment.Center).fadeInOnAppear())
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = tileIcon + 36.dp),
                                    state = pageGrid,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 4.dp else 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    itemsIndexed(apps, key = { _, entry -> drawerAppId(entry.key) }) { index, entry ->
                                        val id = drawerAppId(entry.key)
                                        val openActions = { vm.openLayer(Layer.AppActions(entry.key)) }
                                        val row = ((index - firstAtOpen) / columns).coerceAtLeast(0)
                                        AppTile(
                                            entry = entry,
                                            iconSize = tileIcon,
                                            onLaunch = if (actionsMode) openActions else { { launcher.launch(entry.key, id) } },
                                            onActions = openActions,
                                            actionsOnTap = actionsMode,
                                            modifier = (if (searchMode) searchResultItem() else plutoItem()).staggered(stagger, row),
                                            // Keyboard-compact rows show icons only so no tile is cut off; names stay in semantics.
                                            showLabel = !compact,
                                            focusId = id,
                                            onFocused = {
                                                vm.onAppSelected(entry.key)
                                                vm.onControlFocused(id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showControllerHints(state, focus) && !compact) {
                    ButtonLegend(
                        state.activeMapping(),
                        Legends.Drawer,
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

private val ExpandIn = expandVertically(PlutoMotion.spatialFast(), expandFrom = Alignment.Top) + fadeIn(PlutoMotion.fadeIn())
private val ShrinkOut = shrinkVertically(PlutoMotion.spatialFast(), shrinkTowards = Alignment.Top) + fadeOut(PlutoMotion.fadeOut())

/** Grid states of the drawer's category pages; [active] is the page being shown. */
@Stable
private class DrawerPages(private val initial: LazyGridState) {
    private var initialUsed = false
    var active by mutableStateOf(initial)

    /** The first page resumes at the persisted anchor; later categories start at the top. */
    fun newPageState(): LazyGridState {
        if (!initialUsed) {
            initialUsed = true
            return initial
        }
        return LazyGridState()
    }
}

/** Bookkeeping for a category switch, updated during composition (no state reads). */
private class CategorySwitch(var categoryId: Long?) {
    var keys: List<String> = emptyList()
    var focusedApp: String? = null
}

/** Drawer copy (inline for 0.1). */
internal object DrawerText {
    const val ACTIONS = "Actions"
    const val ACTIONS_DONE = "Done"
    const val ACTIONS_HINT = "Tap an app to see its actions: pin, dock, folder, categories, hide, app info."
}

@Composable
private fun DrawerEmptyState(state: LauncherUiState, vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val query = state.session.searchText
    val category = state.activeCategory
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            query.isNotBlank() -> EmptyState(
                title = "No apps match “$query”",
                detail = if (category != null && !category.isAll) {
                    "Searching in ${category.name}. Try All apps or a shorter name."
                } else {
                    "Search looks at app names and package names."
                },
            ) {
                PlutoTextButton("drawer:empty:clear", "Clear search", { vm.clearSearch() }, emphasized = true)
                if (category != null && !category.isAll) {
                    PlutoTextButton("drawer:empty:all", "Search all apps", { vm.selectCategory(null) })
                }
            }
            category != null && !category.isAll -> EmptyState(
                title = "No apps in ${category.name} yet",
                detail = "Tap Actions, choose an app, then Categories… to add it here.",
            ) {
                PlutoTextButton("drawer:empty:all", "Show all apps", { vm.selectCategory(null) }, emphasized = true)
            }
            else -> EmptyState(
                title = "No apps to show",
                detail = "Hidden apps can be restored from Settings → Hidden apps.",
            ) {
                PlutoTextButton("drawer:empty:hidden", "Hidden apps", { vm.openLayer(Layer.HiddenApps) })
            }
        }
    }
}
