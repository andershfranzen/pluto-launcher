package dev.pluto.launcher.ui.home

import androidx.compose.material.icons.outlined.Close
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import dev.pluto.launcher.ui.components.LocalLabelsOnWallpaper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppTile
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.CategoryTabs
import dev.pluto.launcher.ui.components.EmptyState
import dev.pluto.launcher.ui.components.FastScrollRail
import dev.pluto.launcher.ui.components.FastScrollRailWidth
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.ScrollSections
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.components.rememberEqual
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.controllerFocusTarget
import dev.pluto.launcher.ui.motion.DrawerRevealState
import dev.pluto.launcher.ui.motion.LocalAppLauncher
import dev.pluto.launcher.ui.motion.LocalDrawerReveal
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.theme.PlutoDimens
import dev.pluto.launcher.ui.theme.sheetColor
import dev.pluto.launcher.ui.theme.sheetRaisedColor
import dev.pluto.launcher.ui.theme.sheetRimBrush
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SURFACE = "drawer"

/** Keystrokes whose VM echo the search field still tolerates (see [SearchField]). */
private const val MAX_PENDING_ECHOES = 32

/** Focus id of the drawer's search field; LauncherRoot focuses it for Y / Search. */
const val DRAWER_SEARCH_ID = "drawer:search"
private const val DR_SEARCH_OPEN = "drawer:searchopen"
private const val DR_SEARCH_CLOSE = "drawer:searchclose"
private const val DR_CLOSE = "drawer:close"
private const val DR_CLEAR = "drawer:clear"
private const val CHIP_PREFIX = "drawer:cat"
private const val DR_ACTIONS = "drawer:actions"

/** Below this panel height, with the keyboard up, the drawer drops everything but search and results. */
private val CompactImeHeight = 300.dp

/** Gap between the status bar and the sheet's top edge (LauncherRoot clips home to match). */
private val SheetTopGap = 8.dp

/** The alphabet rail appears for lists at least this long (shorter ones scroll in a flick). */
private const val RAIL_MIN_APPS = 24
private const val RAIL_MIN_SECTIONS = 4

/** New result tiles composed per frame when a search change brings many apps back (Clear). */
private const val RESULT_CHUNK = 12

private val GridStartPadding = 16.dp
private val EdgeFadeTop = 20.dp
private val EdgeFadeBottom = 28.dp

private fun drawerAppId(key: AppKey) = "drawer:${key.encode()}"

/**
 * The app drawer (layer Drawer): a full-height, brand-tinted sheet with a pinned search pill,
 * category chips with a sliding indicator, and one alphabetical grid of state.drawerApps
 * (filtering and accent-insensitive matching are done by the view model), with an alphabet
 * fast-scroll rail, soft edge fades and a useful empty state.
 *
 * App actions: press and hold an app (touch), X (controller), or the screen reader's "App
 * actions" action; for touch without long press, the search pill's ⋮ turns on a mode in
 * which tapping an app opens its actions.
 * When the soft keyboard leaves little room (a phone in landscape), the chips, the rail
 * and the legend step aside and tiles shrink so at least one row of results stays visible.
 *
 * Performance: the screen is recomposed whenever the launcher state changes, so its pieces
 * take narrow inputs that stay the same instance while equal ([rememberEqual]): the search
 * bar reads only its own field, the grid only its list, and tiles skip. Categories share one
 * grid (apps present in both stay composed), large result changes add tiles over a few
 * frames, and nothing here composes inside a measure pass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerScreen(state: LauncherUiState, vm: LauncherViewModel) {
    // Opaque surface: tile labels skip the wallpaper text shadow (see TileLabel).
    CompositionLocalProvider(LocalLabelsOnWallpaper provides false) { DrawerScreenContent(state, vm) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawerScreenContent(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val keyboard = LocalSoftwareKeyboardController.current
    val reveal = LocalDrawerReveal.current
    val launcher = LocalAppLauncher.current
    val outerInert = LocalFocusInert.current
    val iconSize = state.iconSize()
    val session = state.session
    val currentState by rememberUpdatedState(state)
    val categories = rememberEqual(state.categories)
    val activeCategory = rememberEqual(state.activeCategory)
    val categoryId = activeCategory?.id

    // The drawer stays composed while closed ("parked" off screen by LauncherRoot, so opening
    // never has to compose it in its first frame), is composed before it is the open layer
    // (pulled up by a finger) and after it closed (animating away); only the open drawer
    // claims default focus.
    val isTop = session.topLayer == Layer.Drawer
    val parked = outerInert.value && Layer.Drawer !in session.layers
    val searchMode = session.searchActive || session.searchText.isNotEmpty()

    val feed = rememberResultsFeed(rememberEqual(state.drawerApps), spread = !parked, query = state.session.searchText)
    val apps = feed.shown
    val gridKeys = remember(apps) { apps.map { drawerAppId(it.key) } }
    val currentKeys by rememberUpdatedState(gridKeys)

    val gridState = rememberAnchoredGridState(SURFACE, state, gridKeys)
    TrackFocusOrder(SURFACE, gridKeys) { gridState.scrollToItem(it) }
    TrackGridNavigation(SURFACE, gridState, gridKeys)
    // A parked drawer may be showing only part of its results while it is composed in the
    // background: it must not move the logical scroll anchor.
    if (!parked) ReportScrollAnchor(SURFACE, gridState, state, vm, gridKeys)

    var actionsMode by rememberSaveable { mutableStateOf(false) }
    // The drawer stays composed while closed; per-visit modes still end when it closes.
    LaunchedEffect(parked) { if (parked) actionsMode = false }

    // Opened by a button or the controller: the first rows stagger in. Pulled up by a finger
    // the content is already on screen, so it stays put.
    val stagger = rememberStagger(animate = isTop && !reveal.isDragging, stepMs = 24, maxSlots = 5)
    val staggerFrame = remember { StaggerFrame() }
    val wasTop = remember { booleanArrayOf(isTop) }
    if (isTop != wasTop[0]) {
        wasTop[0] = isTop
        val hidden = Snapshot.withoutReadObservation { !reveal.isDragging && reveal.progress < 0.05f }
        if (isTop && hidden) {
            Snapshot.withoutReadObservation {
                staggerFrame.base = gridState.firstVisibleItemIndex
                staggerFrame.columns = gridState.layoutInfo.maxSpan.coerceAtLeast(1)
            }
            stagger.arm()
        }
    }
    LaunchedEffect(isTop) { if (isTop) stagger.replay() }

    // The field keeps its own value (cursor, selection); the VM holds the text itself.
    val field = remember { SearchField(session.searchText) }
    LaunchedEffect(session.searchText) { field.onVmText(session.searchText) }

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

    // New search text: show the best matches from the top. Requested during composition, so
    // the new results are laid out at the top in the same frame. Only when needed: a jump
    // drops the grid's item animations.
    val lastText = remember { arrayOf(session.searchText) }
    if (lastText[0] != session.searchText) {
        lastText[0] = session.searchText
        val atTop = Snapshot.withoutReadObservation { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
        if (!atTop) gridState.requestScrollToItem(0)
    }

    // Category switched (chip, L1/R1): one grid swaps its list (apps in both stay composed);
    // the incoming list slides in from the side it came from. A controller that was on an app
    // stays on it when it is in the new category, else goes to its first app.
    val pageShift = remember { Animatable(0f) }
    val switch = remember { CategorySwitch(categoryId) }
    if (switch.categoryId != categoryId) {
        val from = categories.indexOfFirst { it.id == switch.categoryId }
        val to = categories.indexOfFirst { it.id == categoryId }
        switch.categoryId = categoryId
        switch.direction = if (to >= from) 1 else -1
        switch.animate = !parked
        switch.pending = true
        val focused = Snapshot.withoutReadObservation { focus.focusedId }
        switch.focusedApp = focused?.takeIf { it in gridKeys }
        if (switch.focusedApp == null) gridState.requestScrollToItem(0)
    }
    LaunchedEffect(categoryId) {
        if (!switch.pending) return@LaunchedEffect
        switch.pending = false
        if (switch.animate) {
            launch {
                pageShift.snapTo(switch.direction.toFloat())
                pageShift.animateTo(0f, PlutoMotion.spatial())
            }
        } else {
            pageShift.snapTo(0f)
        }
        val kept = switch.focusedApp
        switch.focusedApp = null
        withFrameNanos { }
        if (focus.inputMode != InputMode.CONTROLLER || currentState.session.topLayer != Layer.Drawer) return@LaunchedEffect
        val keys = currentKeys
        val target = kept?.takeIf { it in keys } ?: keys.firstOrNull() ?: DR_CLOSE
        val index = keys.indexOf(target)
        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) gridState.scrollToItem(index)
        focus.requestFocusWhenReady(target)
    }

    // Swipe down (on the header, or pulling past the top of the list) closes the drawer.
    val closeDriver = rememberRevealDragDriver(opening = false) { open ->
        if (!open) {
            keyboard?.hide()
            if (currentState.session.searchActive) vm.setSearchActive(false)
            vm.back()
        }
    }

    // Keyboard up in a short window: keep only the search row and results. Derived, so the
    // keyboard's animation never recomposes the drawer, only the moment it flips.
    val imeVisible = WindowInsets.isImeVisible
    val shortWindow by rememberShortWindow()
    val compact = imeVisible && shortWindow
    val tileIcon = if (compact) iconSize * 0.75f else iconSize

    val onLaunchApp: (AppKey, String) -> Unit = remember(launcher) { { key, id -> launcher.launch(key, id) } }
    val onAppActions: (AppKey) -> Unit = remember(vm) { { key -> vm.openLayer(Layer.AppActions(key)) } }
    val onAppFocused: (AppKey, String) -> Unit = remember(vm) {
        { key, id ->
            vm.onAppSelected(key)
            vm.onControlFocused(id)
        }
    }

    val sheet = sheetColor()
    val sheetShape = remember { RoundedCornerShape(topStart = PlutoDimens.PanelCorner, topEnd = PlutoDimens.PanelCorner) }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = SheetTopGap),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(sheet, sheetShape)
                .border(1.dp, sheetRimBrush(), sheetShape)
                // The sheet runs edge to edge; cutout and side system bars pad its content.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .revealDrag(closeDriver, opening = false),
        ) {
            if (!compact) SheetHandle(Modifier.align(Alignment.CenterHorizontally))

            // One row: back, then either the category chips or (while searching) the search
            // field, which unfolds from the search button over the chips.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = if (compact) 2.dp else 4.dp, bottom = 4.dp)
                    .graphicsLayer { alpha = revealFade(reveal.progress, start = CONTENT_FADE_START) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlutoIconButton(DR_CLOSE, Icons.AutoMirrored.Outlined.ArrowBack, "Close all apps", { vm.back() })
                AnimatedContent(
                    targetState = searchMode,
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                    transitionSpec = {
                        if (targetState) {
                            // The field grows leftwards out of the search button; the chips give way.
                            (expandHorizontally(PlutoMotion.spatialFast(), expandFrom = Alignment.End) + fadeIn(PlutoMotion.fadeIn()))
                                .togetherWith(fadeOut(PlutoMotion.fadeOut()) + slideOutHorizontally(PlutoMotion.slideSpring) { -it / 6 })
                        } else {
                            (fadeIn(PlutoMotion.fadeIn()) + slideInHorizontally(PlutoMotion.slideSpring) { -it / 6 })
                                .togetherWith(shrinkHorizontally(PlutoMotion.spatialFast(), shrinkTowards = Alignment.End) + fadeOut(PlutoMotion.fadeOut()))
                        }.using(SizeTransform(clip = true))
                    },
                    label = "drawerHeader",
                ) { searching ->
                    if (searching) {
                        DrawerSearchField(
                            field = field,
                            compact = compact,
                            onTextChanged = vm::setSearchText,
                            onClear = { vm.clearSearch() },
                            onCollapse = {
                                keyboard?.hide()
                                vm.setSearchActive(false)
                            },
                            onSearchAction = {
                                keyboard?.hide()
                                if (focus.inputMode == InputMode.CONTROLLER) currentFirstApp?.let(focus::requestFocus)
                            },
                            onFieldActivated = { keyboard?.show() },
                            onFieldFocused = { if (!currentState.session.searchActive) vm.setSearchActive(true) },
                        )
                    } else if (categories.size > 1) {
                        CategoryTabs(
                            categories = categories,
                            selected = activeCategory,
                            onSelect = { vm.selectCategory(if (it.isAll) null else it.id) },
                            idPrefix = CHIP_PREFIX,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.fillMaxWidth())
                    }
                }
                if (!searchMode) {
                    PlutoIconButton(DR_SEARCH_OPEN, Icons.Outlined.Search, "Search apps", { vm.setSearchActive(true) })
                    if (!compact) {
                        PlutoIconButton(
                            id = DR_ACTIONS,
                            icon = if (actionsMode) Icons.Outlined.Check else Icons.Outlined.MoreVert,
                            label = if (actionsMode) DrawerText.ACTIONS_DONE else DrawerText.ACTIONS,
                            onClick = { actionsMode = !actionsMode },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = actionsMode && !compact, enter = ExpandIn, exit = ShrinkOut) {
                ActionsModeBanner(onDone = { actionsMode = false })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (apps.isEmpty()) {
                    DrawerEmptyState(
                        query = session.searchText,
                        category = activeCategory,
                        onClear = { vm.clearSearch() },
                        onShowAll = { vm.selectCategory(null) },
                        onHiddenApps = { vm.openLayer(Layer.HiddenApps) },
                        modifier = Modifier.align(Alignment.Center).fadeInOnAppear(),
                    )
                } else {
                    DrawerGrid(
                        apps = apps,
                        gridState = gridState,
                        iconSize = tileIcon,
                        compact = compact,
                        heavyChange = feed.heavy,
                        itemKey = feed::itemKey,
                        actionsMode = actionsMode,
                        stagger = stagger,
                        staggerFrame = staggerFrame,
                        pageShift = pageShift,
                        fadeColor = sheet.copy(alpha = 1f),
                        onLaunchApp = onLaunchApp,
                        onAppActions = onAppActions,
                        onAppFocused = onAppFocused,
                    )
                    if (!searchMode && !compact && apps.size >= RAIL_MIN_APPS) {
                        val sections = remember(apps) { ScrollSections.of(apps.map { it.label }) }
                        if (sections.size >= RAIL_MIN_SECTIONS) FastScrollRail(sections, gridState)
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

/** True while the window minus system bars, cutout and keyboard is shorter than [CompactImeHeight]. */
@Composable
private fun rememberShortWindow(): State<Boolean> {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val insets = WindowInsets.safeDrawing
    return remember(density, windowInfo, insets) {
        derivedStateOf {
            val available = windowInfo.containerSize.height - insets.getTop(density) - insets.getBottom(density)
            with(density) { available.toDp() } < CompactImeHeight
        }
    }
}

/** The sheet never shows without its header: content starts fading in almost at once. */
private const val CONTENT_FADE_START = 0.05f

/** 0 until the drawer is [start] of the way open, then rising to 1 by the time it is fully open. */
private fun revealFade(progress: Float, start: Float): Float = ((progress - start) / (1f - start)).coerceIn(0f, 1f)

/** The sheet's grab handle: decorative, it says "this pulls down". */
@Composable
private fun SheetHandle(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier
            .padding(top = 8.dp, bottom = 2.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(color, CircleShape),
    )
}

/**
 * The search field, shown in the header row while searching: a pill with the search icon,
 * the text and one trailing control (Clear while there is text, else Close, which folds the
 * field back into the search button and brings the category chips back). It reads only its
 * own [field], so typing recomposes this row, not the grid.
 */
@Composable
private fun DrawerSearchField(
    field: SearchField,
    compact: Boolean,
    onTextChanged: (String) -> Unit,
    onClear: () -> Unit,
    onCollapse: () -> Unit,
    onSearchAction: () -> Unit,
    onFieldActivated: () -> Unit,
    onFieldFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val pillShape = CircleShape
    val value = field.value
    BasicTextField(
        value = value,
        onValueChange = { new -> field.onUserEdit(new)?.let(onTextChanged) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .controllerFocusTarget(
                id = DRAWER_SEARCH_ID,
                onActivate = onFieldActivated,
                onFocused = onFieldFocused,
            ),
        decorationBox = { inner ->
            Row(
                Modifier
                    .heightIn(min = if (compact) 48.dp else 52.dp)
                    .background(sheetRaisedColor(), pillShape)
                    .border(
                        width = 1.dp,
                        color = if (focused) scheme.primary.copy(alpha = 0.55f) else scheme.outlineVariant.copy(alpha = 0.6f),
                        shape = pillShape,
                    )
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = if (focused) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            "Search apps",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.text.isNotEmpty()) {
                    PlutoIconButton(DR_CLEAR, Icons.Outlined.Clear, "Clear search", onClear)
                } else {
                    PlutoIconButton(DR_SEARCH_CLOSE, Icons.Outlined.Close, "Close search", onCollapse)
                }
            }
        },
    )
}

/** Shown while tapping an app opens its actions instead of launching it. */
@Composable
private fun ActionsModeBanner(onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            DrawerText.ACTIONS_HINT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PlutoTextButton("drawer:actions:done", "Done", onDone)
    }
}

/**
 * The results grid. Takes only what it shows, so recomposing the drawer for unrelated
 * state (selection, anchors, search text) skips it entirely.
 */
@Composable
private fun DrawerGrid(
    apps: List<AppEntry>,
    gridState: LazyGridState,
    iconSize: Dp,
    compact: Boolean,
    heavyChange: Boolean,
    itemKey: (AppKey) -> Any,
    actionsMode: Boolean,
    stagger: Stagger,
    staggerFrame: StaggerFrame,
    pageShift: Animatable<Float, *>,
    fadeColor: Color,
    onLaunchApp: (AppKey, String) -> Unit,
    onAppActions: (AppKey) -> Unit,
    onAppFocused: (AppKey, String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = iconSize + 36.dp),
        state = gridState,
        // The end keeps room for the alphabet rail whether or not it is showing, so the
        // columns never reflow when search starts or ends.
        contentPadding = PaddingValues(
            start = if (compact) 12.dp else GridStartPadding,
            end = if (compact) 12.dp else FastScrollRailWidth,
            top = if (compact) 4.dp else 8.dp,
            bottom = if (compact) 4.dp else 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Category switch: the new list arrives from the side it came from.
                val shift = pageShift.value
                if (shift != 0f) {
                    translationX = shift * size.width * 0.12f
                    alpha = 1f - abs(shift) * 0.6f
                }
            }
            .edgeFades(gridState, fadeColor),
    ) {
        itemsIndexed(apps, key = { _, entry -> itemKey(entry.key) }, contentType = { _, _ -> "app" }) { index, entry ->
            val id = drawerAppId(entry.key)
            AppTile(
                entry = entry,
                iconSize = iconSize,
                onLaunch = if (actionsMode) {
                    { onAppActions(entry.key) }
                } else {
                    { onLaunchApp(entry.key, id) }
                },
                onActions = { onAppActions(entry.key) },
                actionsOnTap = actionsMode,
                modifier = resultItem(heavyChange).then(DrawerStaggerElement(stagger, staggerFrame, index)),
                // Keyboard-compact rows show icons only so no tile is cut off; names stay in semantics.
                showLabel = !compact,
                focusId = id,
                onFocused = { onAppFocused(entry.key, id) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Result motion
// ---------------------------------------------------------------------------------------

private val ResultFadeIn: FiniteAnimationSpec<Float> = PlutoMotion.fadeIn()
private val ResultFadeOut: FiniteAnimationSpec<Float> = PlutoMotion.fadeOut()
private val ResultPlacement: FiniteAnimationSpec<IntOffset> = PlutoMotion.spatialFast()

/** After a heavy change the old results are gone (SWAP_MS) before the new ones fade in. */
private val SwapFadeIn: FiniteAnimationSpec<Float> =
    tween(PlutoMotion.MEDIUM_MS, delayMillis = PlutoMotion.SWAP_MS, easing = PlutoMotion.EmphasizedDecelerate)
private val SwapFadeOut: FiniteAnimationSpec<Float> = tween(PlutoMotion.SWAP_MS, easing = PlutoMotion.EmphasizedAccelerate)

/**
 * Item motion for the drawer: appearing/disappearing tiles fade; when most of the list
 * stayed (refining a search, an install) the rest glide to their new cells with a short,
 * fast spring. When most of it changed (first character, Clear, another category) tiles
 * would fly on long diagonals across each other, so they cross-fade in place instead.
 */
private fun LazyGridItemScope.resultItem(heavy: Boolean): Modifier =
    if (heavy) {
        Modifier.animateItem(fadeInSpec = SwapFadeIn, placementSpec = null, fadeOutSpec = SwapFadeOut)
    } else {
        Modifier.animateItem(fadeInSpec = ResultFadeIn, placementSpec = ResultPlacement, fadeOutSpec = ResultFadeOut)
    }

/**
 * What the grid shows of the view model's results. Equal to them, except when a change
 * brings back many apps at once (deleting the last character, Clear): then the tiles that
 * were already showing stay, and the new ones join [RESULT_CHUNK] per frame, top first, so
 * no single frame composes most of the grid. [heavy] tells whether the last change replaced
 * most of the list.
 */
@Stable
private class ResultsFeed(initial: List<AppEntry>) {
    var target: List<AppEntry> = initial
    private var current: List<AppEntry> = initial
    private val version = mutableIntStateOf(0)
    var heavy = false
    var keep: Set<AppKey>? = null

    /**
     * Grid-key generation per app. On a heavy change tiles do not glide (placementSpec is
     * null), so a kept tile that has to move to another cell gets a fresh key: its old
     * cell fades out with the departing tiles and it fades in at its new cell only after
     * SWAP_MS, so no cell ever shows two tiles at once.
     */
    private val generations = HashMap<AppKey, Int>()
    private var generation = 0

    fun itemKey(key: AppKey): Any {
        val g = generations[key] ?: return drawerAppId(key)
        return "${drawerAppId(key)}#$g"
    }

    /** Called on a heavy change, before [set], with the list shown so far and the new lists. */
    fun rekeyMoved(previous: List<AppEntry>, first: List<AppEntry>, final: List<AppEntry>) {
        val moved = movedKeys(previous, first, final)
        if (moved.isEmpty()) return
        generation++
        for (key in moved) generations[key] = generation
    }

    /** Read in composition: subscribes to the frame-by-frame growth. */
    val shown: List<AppEntry>
        get() {
            version.intValue
            return current
        }

    fun peek(): List<AppEntry> = current

    fun set(list: List<AppEntry>, notify: Boolean) {
        current = list
        if (notify) version.intValue++
    }
}

@Composable
private fun rememberResultsFeed(results: List<AppEntry>, spread: Boolean, query: String): ResultsFeed {
    val feed = remember { ResultsFeed(results) }
    val lastQuery = remember { arrayOf(query) }
    if (feed.target !== results) {
        feed.target = results
        val previous = feed.peek()
        val before = previous.mapTo(HashSet(previous.size)) { it.key }
        val kept = results.count { it.key in before }
        // A keystroke always swaps in place: kept tiles gliding to new cells crossed the
        // fading ones, and one frame showed tiles mid-flight at mixed positions. Gliding is
        // kept for list changes under an unchanged query (an install, a hidden app).
        val typed = query != lastQuery[0]
        feed.heavy = typed || kept * 2 < maxOf(previous.size, results.size)
        val first = if (!spread || results.size - kept <= RESULT_CHUNK) {
            feed.keep = null
            results
        } else {
            feed.keep = before
            partialResults(results, before, RESULT_CHUNK)
        }
        if (feed.heavy) feed.rekeyMoved(previous, first, results)
        feed.set(first, notify = false)
        // Compared at the next change of results (they may arrive a frame after the query).
        lastQuery[0] = query
    }
    val target = feed.target
    LaunchedEffect(target) {
        val keep = feed.keep ?: return@LaunchedEffect
        var budget = RESULT_CHUNK
        while (feed.peek() !== target) {
            withFrameNanos { }
            budget += RESULT_CHUNK
            val next = partialResults(target, keep, budget)
            feed.set(if (next.size == target.size) target else next, notify = true)
        }
        feed.keep = null
    }
    return feed
}

/**
 * Apps present before and after a change whose cell differs from their previous one, either
 * in the first list shown ([first], possibly partial) or in the [final] one. Between those
 * two an app's index only grows monotonically, so an app equal in both never moves.
 */
internal fun movedKeys(previous: List<AppEntry>, first: List<AppEntry>, final: List<AppEntry>): Set<AppKey> {
    if (previous.isEmpty()) return emptySet()
    val oldIndex = HashMap<AppKey, Int>(previous.size * 2)
    previous.forEachIndexed { i, e -> oldIndex[e.key] = i }
    val moved = HashSet<AppKey>()
    first.forEachIndexed { i, e -> oldIndex[e.key]?.let { if (it != i) moved.add(e.key) } }
    if (final !== first) final.forEachIndexed { i, e -> oldIndex[e.key]?.let { if (it != i) moved.add(e.key) } }
    return moved
}

/** [results] with every app in [keep] and the first [budget] others, in order. */
internal fun partialResults(results: List<AppEntry>, keep: Set<AppKey>, budget: Int): List<AppEntry> {
    var added = 0
    return results.filter { entry ->
        if (entry.key in keep) {
            true
        } else {
            added++
            added <= budget
        }
    }
}

/** Draws the sheet colour over the grid's top and bottom edges where it can scroll further. */
private fun Modifier.edgeFades(gridState: LazyGridState, color: Color): Modifier = drawWithContent {
    drawContent()
    val top = EdgeFadeTop.toPx()
    // Grows in with the first few pixels of scroll instead of popping in.
    val topAmount = if (gridState.firstVisibleItemIndex > 0) 1f else (gridState.firstVisibleItemScrollOffset / top).coerceIn(0f, 1f)
    if (topAmount > 0f) {
        drawRect(
            Brush.verticalGradient(0f to color, 1f to Color.Transparent, startY = 0f, endY = top),
            size = Size(size.width, top),
            alpha = topAmount,
        )
    }
    if (gridState.canScrollForward) {
        val bottom = EdgeFadeBottom.toPx()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to color, startY = size.height - bottom, endY = size.height),
            topLeft = Offset(0f, size.height - bottom),
            size = Size(size.width, bottom),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Opening stagger
// ---------------------------------------------------------------------------------------

/** Where the opening stagger counts rows from; plain fields, read only in layout and draw. */
private class StaggerFrame {
    var base = 0
    var columns = 1
}

/**
 * Staggered entrance per row for drawer tiles. The row is worked out at draw time from the
 * tile's index and [StaggerFrame], so opening the drawer never recomposes a tile.
 */
private data class DrawerStaggerElement(val stagger: Stagger, val frame: StaggerFrame, val index: Int) :
    ModifierNodeElement<DrawerStaggerNode>() {
    override fun create() = DrawerStaggerNode(stagger, frame, index)
    override fun update(node: DrawerStaggerNode) {
        node.stagger = stagger
        node.frame = frame
        node.index = index
        node.invalidatePlacement()
        node.invalidateDraw()
    }
}

private class DrawerStaggerNode(var stagger: Stagger, var frame: StaggerFrame, var index: Int) :
    Modifier.Node(), LayoutModifierNode, DrawModifierNode {
    private fun progress(): Float = stagger.progress(((index - frame.base) / frame.columns).coerceAtLeast(0))

    private val layer: GraphicsLayerScope.() -> Unit = { alpha = progress() }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layer) }
    }

    override fun ContentDrawScope.draw() {
        val p = progress()
        if (p >= 1f) {
            drawContent()
        } else {
            translate(top = (1f - p) * RISE_DP * density) { this@draw.drawContent() }
        }
    }

    private companion object {
        const val RISE_DP = 12f
    }
}

// ---------------------------------------------------------------------------------------
// Search field
// ---------------------------------------------------------------------------------------

/**
 * The search field's own value (cursor and selection) and the echo bookkeeping with the
 * view model, which holds the text. Texts sent to the VM that it may still echo back are
 * kept oldest first; only *other* changes (Clear, Back) overwrite the field, so an echo of
 * an older keystroke arriving after newer ones (fast typing, conflated state) never rewinds it.
 */
@Stable
private class SearchField(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set
    private val sent = ArrayDeque<String>().apply { add(initial) }

    /** The user edited the field; returns the text to send to the VM, or null if unchanged. */
    fun onUserEdit(new: TextFieldValue): String? {
        value = new
        if (new.text == sent.lastOrNull()) return null
        sent.addLast(new.text)
        if (sent.size > MAX_PENDING_ECHOES) sent.removeFirst()
        return new.text
    }

    fun onVmText(text: String) {
        val echo = sent.indexOf(text)
        if (echo >= 0) {
            // Our own keystroke: drop it and everything older; the field is already ahead.
            repeat(echo) { sent.removeFirst() }
        } else {
            sent.clear()
            sent.add(text)
            value = TextFieldValue(text, TextRange(text.length))
        }
    }
}

private val ExpandIn = expandVertically(PlutoMotion.spatialFast(), expandFrom = Alignment.Top) + fadeIn(PlutoMotion.fadeIn())
private val ShrinkOut = shrinkVertically(PlutoMotion.spatialFast(), shrinkTowards = Alignment.Top) + fadeOut(PlutoMotion.fadeOut())

/** Bookkeeping for a category switch, updated during composition (no state reads). */
private class CategorySwitch(var categoryId: Long?) {
    var direction = 1
    var animate = false
    var pending = false
    var focusedApp: String? = null
}

/** Drawer copy (inline for 0.1). */
internal object DrawerText {
    const val ACTIONS = "Choose an app to see its actions"
    const val ACTIONS_DONE = "Done choosing apps"
    const val ACTIONS_HINT = "Tap an app to see its actions: pin, dock, folder, categories, hide, app info."
}

@Composable
private fun DrawerEmptyState(
    query: String,
    category: Category?,
    onClear: () -> Unit,
    onShowAll: () -> Unit,
    onHiddenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            query.isNotBlank() -> EmptyState(
                title = "No apps match “$query”",
                icon = Icons.Outlined.SearchOff,
                detail = if (category != null && !category.isAll) {
                    "Searching in ${category.name}. Try All apps or a shorter name."
                } else {
                    "Search looks at app names and package names."
                },
            ) {
                PlutoTextButton("drawer:empty:clear", "Clear search", onClear, emphasized = true)
                if (category != null && !category.isAll) {
                    PlutoTextButton("drawer:empty:all", "Search all apps", onShowAll)
                }
            }
            category != null && !category.isAll -> EmptyState(
                title = "No apps in ${category.name} yet",
                icon = Icons.Outlined.Category,
                detail = "Press and hold an app (or use ⋮ beside search), then Categories… to add it here.",
            ) {
                PlutoTextButton("drawer:empty:all", "Show all apps", onShowAll, emphasized = true)
            }
            else -> EmptyState(
                title = "No apps to show",
                icon = Icons.Outlined.Apps,
                detail = "Hidden apps can be restored from Settings → Hidden apps.",
            ) {
                PlutoTextButton("drawer:empty:hidden", "Hidden apps", onHiddenApps)
            }
        }
    }
}
