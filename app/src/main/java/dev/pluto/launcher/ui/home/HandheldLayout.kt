package dev.pluto.launcher.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import dev.pluto.launcher.data.prefs.ButtonMapping
import dev.pluto.launcher.ui.console.consoleLegendFor
import dev.pluto.launcher.ui.console.CARD_ID_PREFIX
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import dev.pluto.launcher.ui.components.GlassPanel
import dev.pluto.launcher.ui.theme.LocalReducedMotion
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.console.ConsoleEntry
import dev.pluto.launcher.system.RomLauncher
import androidx.compose.ui.platform.LocalContext
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.ClockHeader
import dev.pluto.launcher.ui.components.symmetricHorizontalSafeDrawing
import dev.pluto.launcher.ui.components.EmptyState
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.components.promptFor
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.ui.console.ConsoleArtSize
import dev.pluto.launcher.ui.console.ConsoleBackdrop
import dev.pluto.launcher.ui.console.ConsoleFocusLinks
import dev.pluto.launcher.ui.console.ConsoleLegend
import dev.pluto.launcher.ui.console.HeroEmphasis
import dev.pluto.launcher.ui.console.ConsoleShelves
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pluto.launcher.ui.console.ConsoleTitle
import dev.pluto.launcher.ui.console.LocalCarouselStyle
import dev.pluto.launcher.ui.console.ConsoleCounter
import dev.pluto.launcher.ui.theme.ConsoleTheme
import dev.pluto.launcher.data.prefs.BackgroundChoice
import dev.pluto.launcher.ui.console.ConsoleTopBar
import dev.pluto.launcher.ui.console.ControllerBattery
import dev.pluto.launcher.ui.console.PhoneBattery
import dev.pluto.launcher.ui.console.CoverflowStage
import dev.pluto.launcher.ui.console.CoverflowState
import dev.pluto.launcher.ui.console.PageMotion
import dev.pluto.launcher.ui.console.Shelf
import dev.pluto.launcher.ui.console.ShelfId
import dev.pluto.launcher.ui.console.ShelfTabs
import dev.pluto.launcher.ui.console.cardFocusId
import dev.pluto.launcher.ui.console.shelfTabId
import dev.pluto.launcher.ui.console.TAB_ID_PREFIX
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.LocalShowTouchSelection
import dev.pluto.launcher.ui.focus.focusInert
import dev.pluto.launcher.ui.motion.LocalAppLauncher
import dev.pluto.launcher.ui.motion.LocalOriginRegistry
import dev.pluto.launcher.ui.motion.PlutoMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private const val FLOW_SURFACE = "hh:flow"
private const val ID_HH_SEARCH = "hh:search"
private const val ID_HH_ALL_APPS = "hh:allapps"
private const val ID_HH_SETTINGS = "hh:settings"
private const val ID_EMPTY_ALL_APPS = "hh:empty:allapps"
private const val ID_EMPTY_ADD = "hh:empty:add"
private const val ID_HH_OVERVIEW = "hh:overview"
private const val ID_HH_EDIT_SHELF = "hh:editshelf"

/** The category behind [shelfId] when its apps can be picked (any category except All apps). */
private fun pickableCategoryId(shelfId: ShelfId, state: LauncherUiState): Long? =
    (shelfId as? ShelfId.OfCategory)?.categoryId?.takeIf { id -> state.categories.any { it.id == id && !it.isAll } }

/** The selection is mirrored into the shared session only once the flow rests this long (no emission per D-pad step). */
private const val SELECTION_COMMIT_DELAY_MS = 400L

/** Shelf entrances settle from the same 108% scale as the drawer. */
private const val SHELF_ENTRANCE_SCALE = 0.08f

/**
 * Controller-first landscape layout ("console mode") as a Coverflow.
 *
 * Shelves form the tab row: Recent launches · Favourites · the categories (L1/R1 wrap, or
 * touch). The selected app sits centre stage, large and front-facing with a reflection;
 * neighbours tilt away in 3D, smaller and dimmed. D-pad / stick LEFT/RIGHT move the
 * selection (the flow glides continuously while held), A opens the app out of its card, X
 * opens its actions, UP reaches the tabs and DOWN the buttons at the bottom right. Touch: drag
 * scrubs the flow 1:1, a fling snaps to the nearest card, tapping a side card brings it to
 * the centre and tapping the centre card opens it.
 *
 * State: the active shelf lives in the session (rotation, process death); every shelf keeps
 * its own selected app by identity (view model, unobserved). The flow position, selection and
 * shelf slide are read only in layout/draw, so neither a held D-pad nor a glide recomposes
 * this layout; the shared selected app is committed once the flow rests.
 */
@Composable
fun HandheldLayout(state: LauncherUiState, vm: LauncherViewModel) {
    ConsoleTheme { HandheldStage(state, vm) }
}

/**
 * Console mode is immersive: the status bar (a second clock above Pluto's own) is hidden
 * while [hidden] and comes back with a swipe from the edge; it returns when the mode ends.
 * Called by the root, so pages opened over the console keep it hidden too. The navigation
 * bar is left alone so system gestures behave as everywhere else.
 */
@Composable
internal fun HideStatusBarWhile(hidden: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode || !hidden) return
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
            ?: (view.context as? android.content.ContextWrapper)?.baseContext?.let { it as? android.app.Activity }?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

@Composable
private fun HandheldStage(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val libraries by vm.romLibraries.collectAsState()
    val focus = LocalControllerFocus.current
    val icons = LocalIconCache.current
    val launcher = LocalAppLauncher.current
    val origins = LocalOriginRegistry.current
    val scope = rememberCoroutineScope()
    val mapping = state.activeMapping()
    val hints = showControllerHints(state, focus, LauncherMode.HANDHELD)
    val actionsKey = if (state.controllerConnected) mapping.promptFor(ControllerAction.ACTIONS) else null
    val versions by icons.versions.collectAsState()
    val currentVersions by rememberUpdatedState(versions)
    val artPx = with(LocalDensity.current) { ConsoleArtSize.roundToPx() }
    val currentState by rememberUpdatedState(state)

    // Shelves and the active shelf's apps (list instances reused while their content is equal,
    // so an unrelated session change does not recompose the flow).
    val order = remember(state.categories, state.settings.historyEnabled, libraries, state.settings.consoleFavourites) {
        ConsoleShelves.order(state.categories, state.settings.historyEnabled, libraries, state.settings.consoleFavourites)
    }
    val tabs = remember(order, state.categories, libraries) { order.map { it to ConsoleShelves.title(it, state.categories, libraries) } }
    val favourites = rememberEqualList(remember(state.homeTiles) { ConsoleShelves.favouriteApps(state.homeTiles) })
    val active = vm.activeShelf(state)
    val shelf = remember(active, state.categories, state.categoryMembers, state.apps, favourites, state.recents) {
        ConsoleShelves.shelf(active, state.categories, state.categoryMembers, state.apps, favourites, state.recents)
    }
    val library = (active as? ShelfId.OfRomLibrary)?.let { id -> libraries.firstOrNull { it.id == id.libraryId } }
    val shelfTitle = library?.name ?: shelf.title
    val apps = rememberEqualList(remember(shelf.apps, library) {
        library?.games?.map { ConsoleEntry.Rom(library.id, it) } ?: shelf.apps.map(ConsoleEntry::App)
    })
    fun initialIndex(firstAppearance: Boolean): Int {
        if (library != null) return apps.indexOfFirst { (it as? ConsoleEntry.Rom)?.game?.uri == vm.romSelection(library.id) }.coerceAtLeast(0)
        return if (firstAppearance) ConsoleShelves.initialIndex(shelf.apps, state.session.selectedApp, vm.shelfSelection(active))
        else ConsoleShelves.initialIndex(shelf.apps, vm.shelfSelection(active), state.session.selectedApp)
    }

    // Land on the default shelf once and keep it, so it doesn't change under the user when
    // their first recent launch or favourite appears.
    LaunchedEffect(Unit) {
        if (currentState.session.handheldShelf == null) vm.selectShelf(vm.activeShelf(currentState))
    }

    // --- Shelf switching: crossfade and settle in place -------------------------
    // Appearing (a controller connected, rotation): continue on the app selected in the other
    // layout when this shelf has it, so the console picks up where the user was (C02).
    val switch = remember {
        ShelfSwitch(active, CoverflowState(initialIndex(true), apps))
    }
    if (switch.shelf != active) {
        // Remember what held focus before the outgoing flow turns focus-inert (read without subscribing).
        switch.focusedAtSwitch = Snapshot.withoutReadObservation { focus.focusedId ?: focus.lastFocusedId }
        switch.begin(
            next = active,
            nextStage = CoverflowState(initialIndex(false), apps),
        )
    } else {
        switch.stage.sync(apps)
    }
    val stage = switch.stage
    val links = remember { ConsoleFocusLinks() }
    val hero = remember { HeroEmphasis() }
    HeroEmphasisEffect(focus, hero)

    LaunchedEffect(active) {
        if (switch.progress < 1f) {
            animate(switch.progress, 1f, animationSpec = tween(PlutoMotion.MEDIUM_MS, easing = PlutoMotion.Standard)) { value, _ -> switch.progress = value }
        }
        switch.progress = 1f
        switch.outgoingStage = null
        switch.outgoingShelf = null
    }
    // A grid choice can arrive before this page has been disposed by its overlay.
    val appeared = remember(stage) { booleanArrayOf(false) }
    LaunchedEffect(state.session.layers.isEmpty(), stage) {
        if (!appeared[0]) {
            appeared[0] = true
        } else if (state.session.layers.isEmpty()) {
            stage.select(initialIndex(false), scope)
        }
    }
    // After a list change the selection may have shifted: let the flow settle on it.
    LaunchedEffect(stage, apps) { stage.select(stage.selectedIndex, scope) }

    // --- Selection, focus and launching -----------------------------------------------------
    fun selectedCardId(s: CoverflowState, shelfId: ShelfId): String? = s.selectedKey?.let { cardFocusId(shelfId.key, it) }

    fun noteSelection(s: CoverflowState, shelfId: ShelfId, index: Int) {
        val entry = s.apps.getOrNull(index) ?: return
        when (entry) {
            is ConsoleEntry.App -> vm.rememberShelfSelection(shelfId, entry.entry.key)
            is ConsoleEntry.Rom -> vm.rememberRomSelection(entry.libraryId, entry.game.uri)
        }
        val id = cardFocusId(shelfId.key, entry.key)
        focus.setDefaultFocus(id)
        // Touch moved the selection: the next controller input continues from here.
        if (focus.inputMode != InputMode.CONTROLLER) focus.rememberFocusTarget(id)
    }

    fun launch(s: CoverflowState, shelfId: ShelfId, entry: ConsoleEntry) {
        when (entry) {
            is ConsoleEntry.App -> {
                val id = cardFocusId(shelfId.key, entry.key)
                s.selectedBounds()?.let { origins.report(id, it) }
                vm.onAppSelected(entry.entry.key)
                launcher.launch(entry.entry.key, id)
            }
            is ConsoleEntry.Rom -> libraries.firstOrNull { it.id == entry.libraryId }?.let { lib ->
                RomLauncher.launch(context, lib, entry.game)?.let(vm::showMessage)
            }
        }
    }

    fun openActions(entry: ConsoleEntry) {
        when (entry) {
            is ConsoleEntry.App -> {
                vm.onAppSelected(entry.entry.key)
                vm.openLayer(Layer.AppActions(entry.entry.key))
            }
            is ConsoleEntry.Rom -> vm.openLayer(Layer.RomLibraries)
        }
    }

    LaunchedEffect(stage) {
        snapshotFlow { (stage.selectedEntry as? ConsoleEntry.App)?.entry?.key }
            .filterNotNull().distinctUntilChanged().collectLatest { key ->
                delay(SELECTION_COMMIT_DELAY_MS)
                if (currentState.session.selectedApp != key) vm.onAppSelected(key)
            }
    }

    val flowIds = remember(apps, active) { apps.map { cardFocusId(active.key, it.key) } }
    TrackFocusOrder(FLOW_SURFACE, flowIds) { index -> stage.select(index, scope) }

    val emptyDefault = when {
        apps.isNotEmpty() -> null
        active is ShelfId.OfRomLibrary || pickableCategoryId(active, state) != null -> ID_EMPTY_ADD
        else -> ID_EMPTY_ALL_APPS
    }
    SideEffect { focus.setDefaultFocus(selectedCardId(stage, active) ?: emptyDefault ?: shelfTabId(active)) }
    // Whenever touch takes over (or switches shelf), the next controller input returns to the
    // selected card, never to whatever top-bar button the controller last visited.
    // The selection is observed in a snapshot flow, never read in composition: a D-pad step
    // must not recompose the console.
    val currentEmptyDefault by rememberUpdatedState(emptyDefault)
    LaunchedEffect(focus.inputMode, active, stage) {
        if (focus.inputMode != InputMode.TOUCH) return@LaunchedEffect
        snapshotFlow { stage.selectedKey }.collect {
            focus.rememberFocusTarget(selectedCardId(stage, active) ?: currentEmptyDefault ?: shelfTabId(active))
        }
    }

    val editable = pickableCategoryId(active, state)
    val canEdit = editable != null || active is ShelfId.OfRomLibrary
    ConsoleFocusEffects(state, focus, switch, active, emptyDefault, hasEditButton = canEdit && apps.isNotEmpty()) {
        selectedCardId(stage, active)
    }

    // --- Layout -------------------------------------------------------------------------------
    val selectedCardFocus: () -> FocusRequester = { switch.stage.let { if (it.count > 0) it.selectedRequester() else FocusRequester.Default } }
    CompositionLocalProvider(LocalShowTouchSelection provides true, LocalCarouselStyle provides state.settings.carouselStyle) {
        Box(Modifier.fillMaxSize()) {
            // Wallpaper is dimmed here; built-in backgrounds keep their own colour beneath the glow.
            ConsoleBackdrop(
                stage = { switch.stage },
                artPx = artPx,
                versions = { currentVersions },
                modifier = Modifier.matchParentSize(),
                dimWallpaper = state.settings.consoleBackground == BackgroundChoice.WALLPAPER,
            )
            // Only vertical insets here: the stage and title are centred on the whole screen
            // (a landscape camera cutout would otherwise push them off-centre); the top bar and
            // legend take the horizontal insets themselves, equal on both sides so their corners
            // match.
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
            ) {
                ConsoleTopBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .symmetricHorizontalSafeDrawing()
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp),
                    tabs = {
                        ShelfTabs(
                            shelves = tabs,
                            active = active,
                            onSelect = { vm.selectShelf(it) },
                            links = links,
                            downTarget = selectedCardFocus,
                            prevKey = null,
                            nextKey = null,
                        )
                    },
                    // Top right: controller and phone battery, then the time and date.
                    status = {
                        Row(
                            Modifier.padding(end = 8.dp).graphicsLayer { alpha = 0.65f },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // One reading per connected controller (two players, a spare pad).
                            state.controllers.forEachIndexed { i, controller ->
                                key(controller.deviceId) {
                                    ControllerBattery(controller.deviceId, number = if (state.controllers.size > 1) i + 1 else null)
                                }
                            }
                            PhoneBattery()
                            ClockHeader(compact = true, quiet = true)
                        }
                    },
                    // The launcher buttons live at the bottom right, beside the legend.
                    buttons = {},
                )

                // The outgoing flow fades beneath the incoming shelf.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val outgoing = switch.outgoingStage
                    val outgoingShelf = switch.outgoingShelf
                    if (outgoing != null && outgoingShelf != null) {
                        key(outgoingShelf.key) {
                            ShelfPage(
                                shelfId = outgoingShelf,
                                stage = outgoing,
                                leaving = true,
                                page = switch.outgoingMotion,
                                hero = HeroEmphasis.Static,
                                emptyTitle = emptyTitle(outgoingShelf, ConsoleShelves.title(outgoingShelf, state.categories, libraries), actionsKey),
                                pickable = pickableCategoryId(outgoingShelf, state),
                                vm = vm,
                                onActivate = { _, _ -> },
                                onActions = {},
                                onFocused = { _, _ -> },
                                onTouchSelect = {},
                                upTarget = { FocusRequester.Default },
                                downTarget = { FocusRequester.Default },
                            )
                        }
                    }
                    key(active.key) {
                        ShelfPage(
                            shelfId = active,
                            stage = stage,
                            leaving = false,
                            page = switch.incomingMotion,
                            hero = hero,
                            emptyTitle = emptyTitle(active, shelfTitle, actionsKey),
                            pickable = pickableCategoryId(active, state),
                            vm = vm,
                            onActivate = { index, entry ->
                                if (index == stage.selectedIndex) {
                                    launch(stage, active, entry)
                                } else {
                                    // A side card: bring it to the centre (tap it again to open).
                                    stage.select(index, scope)
                                    noteSelection(stage, active, index)
                                }
                            },
                            onActions = { entry -> openActions(entry) },
                            onFocused = { index, _ ->
                                stage.select(index, scope)
                                noteSelection(stage, active, index)
                            },
                            onTouchSelect = { index -> noteSelection(stage, active, index) },
                            upTarget = { links.activeTab() },
                            downTarget = { FocusRequester.Default },
                        )
                    }
                }

                // An empty shelf explains itself on the stage; no stray caption under it.
                if (apps.isNotEmpty()) {
                    ConsoleTitle(
                        stage = stage,
                        subtitle = library?.let { lib ->
                            val emulator = state.allApps.firstOrNull { it.key.packageName == lib.emulatorPackage }?.label
                            listOfNotNull(shelfTitle, emulator).joinToString(" · ")
                        } ?: shelfTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
                // Open and Actions are part of the button legend (bottom right), not separate
                // buttons: touch opens the centre card with a tap, actions with a long press.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .symmetricHorizontalSafeDrawing()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = ConsoleBottomClearance),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Bottom left, opposite the buttons: the position in the shelf, then the hints.
                    // It takes the room the buttons leave, so the two never overlap (large text).
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (apps.isNotEmpty()) ConsoleCounter(stage)
                        if (hints) {
                            ConsoleButtonLegend(mapping, hasApps = apps.isNotEmpty())
                        } else if (apps.isNotEmpty()) {
                            // Touch has no legend: say once how to reach an app's options.
                            Text(
                                ConsoleText.TOUCH_HINT,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // Up from these returns to the selected card.
                    val up = Modifier.focusProperties { up = selectedCardFocus() }
                    GlassPanel(shape = RoundedCornerShape(28.dp),
                        fill = Brush.verticalGradient(listOf(Color(0xFF202227).copy(alpha = 0.72f), Color(0xFF14161A).copy(alpha = 0.55f)))) {
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (apps.isNotEmpty()) {
                                PlutoIconButton(ID_HH_OVERVIEW, Icons.Outlined.GridView, "Browse $shelfTitle", {
                                    noteSelection(stage, active, stage.selectedIndex)
                                    vm.openLayer(Layer.ConsoleOverview)
                                }, up)
                            }
                            if (canEdit && apps.isNotEmpty()) {
                                PlutoIconButton(ID_HH_EDIT_SHELF, Icons.Outlined.Edit, "Edit $shelfTitle", { vm.openLayer(editable?.let(Layer::ShelfPicker) ?: Layer.RomLibraries) }, up)
                            }
                            PlutoIconButton(ID_HH_SEARCH, Icons.Outlined.Search, "Search apps", { vm.openDrawer(withSearch = true) }, up)
                            PlutoIconButton(ID_HH_ALL_APPS, Icons.Outlined.Apps, "All apps", { vm.openDrawer() }, up)
                            PlutoIconButton(ID_HH_SETTINGS, Icons.Outlined.Settings, "Launcher settings", { vm.openLayer(Layer.Settings) }, up)
                        }
                    }
                }
            }
        }
    }
}

/** One shelf's flow (or its empty state). The outgoing one is focus-inert and hidden from screen readers. */
@Composable
private fun ShelfPage(
    shelfId: ShelfId,
    stage: CoverflowState,
    leaving: Boolean,
    page: PageMotion,
    hero: HeroEmphasis,
    emptyTitle: Pair<String, String>,
    /** Category whose apps the empty state's "Add apps" picks, or null (no picker). */
    pickable: Long?,
    vm: LauncherViewModel,
    onActivate: (Int, ConsoleEntry) -> Unit,
    onActions: (ConsoleEntry) -> Unit,
    onFocused: (Int, ConsoleEntry) -> Unit,
    onTouchSelect: (Int) -> Unit,
    upTarget: () -> FocusRequester,
    downTarget: () -> FocusRequester,
) {
    val icons = LocalIconCache.current
    val versions by icons.versions.collectAsState()
    CompositionLocalProvider(LocalFocusInert provides focusInert(leaving)) {
        val reducedMotion = LocalReducedMotion.current
        val base = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = if (reducedMotion) 1f else page.scale()
                scaleX = scale
                scaleY = scale
            }
            .then(if (leaving) Modifier.clearAndSetSemantics { } else Modifier)
        if (stage.count == 0) {
            BoxWithConstraints(
                base.graphicsLayer {
                    translationX = page.offset() * size.width
                    alpha = page.alpha().coerceIn(0f, 1f)
                },
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = emptyTitle.first,
                    detail = emptyTitle.second,
                    onWallpaper = true,
                    // The illustration only where the stage has room for it: on a phone in
                    // landscape it pushed the buttons off the bottom of the stage.
                    icon = if (maxHeight >= EmptyIllustrationMinStage) Icons.Outlined.Apps else null,
                    // A readable measure, centred; scrolls rather than squashing its buttons
                    // when the stage is short (large text, small landscape windows).
                    modifier = Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()),
                ) {
                    if (shelfId is ShelfId.OfRomLibrary) {
                        PlutoTextButton(ID_EMPTY_ADD, "Manage ROM tab", { vm.openLayer(Layer.RomLibraries) }, emphasized = true)
                    } else if (pickable != null) {
                        PlutoTextButton(ID_EMPTY_ADD, "Add apps", { vm.openLayer(Layer.ShelfPicker(pickable)) }, emphasized = true)
                        PlutoTextButton(
                            ID_EMPTY_ALL_APPS, "All apps", { vm.openDrawer() },
                            outline = Color.White.copy(alpha = 0.3f),
                        )
                    } else {
                        PlutoTextButton(ID_EMPTY_ALL_APPS, "All apps", { vm.openDrawer() }, emphasized = true)
                    }
                }
            }
        } else {
            CoverflowStage(
                stage = stage,
                shelfKey = shelfId.key,
                icons = icons,
                versions = versions,
                page = page,
                hero = hero,
                onActivate = onActivate,
                onActions = onActions,
                onFocused = onFocused,
                onTouchSelect = onTouchSelect,
                upTarget = upTarget,
                downTarget = downTarget,
                modifier = base,
            )
        }
    }
}

/** Stage height from which an empty shelf also shows its illustration (else just text and buttons). */
private val EmptyIllustrationMinStage = 360.dp

/** Space between the bottom row and the navigation handle, on top of the system inset. */
private val ConsoleBottomClearance = 20.dp

/**
 * Drives [hero]: the centre card recedes while controller focus is in the header (tabs,
 * search, all apps, settings) and lifts briefly when focus comes back down. Reads focus in
 * a snapshot flow, so nothing recomposes.
 */
@Composable
private fun HeroEmphasisEffect(focus: ControllerFocusController, hero: HeroEmphasis) {
    LaunchedEffect(focus, hero) {
        // The centre card glows while it holds controller focus.
        launch {
            snapshotFlow { focus.inputMode == InputMode.CONTROLLER && focus.focusedId?.startsWith(CARD_ID_PREFIX) == true }
                .distinctUntilChanged()
                .collectLatest { onCard -> animate(hero.focus, if (onCard) 1f else 0f, animationSpec = PlutoMotion.fadeIn()) { v, _ -> hero.focus = v } }
        }
        snapshotFlow {
            val id = focus.focusedId
            focus.inputMode == InputMode.CONTROLLER && id != null &&
                (id.startsWith(TAB_ID_PREFIX) || id == ID_HH_SEARCH || id == ID_HH_ALL_APPS || id == ID_HH_SETTINGS || id == ID_HH_EDIT_SHELF || id == ID_HH_OVERVIEW)
        }.distinctUntilChanged().collectLatest { inHeader ->
            if (inHeader) {
                hero.lift = 0f
                animate(hero.emphasis, 0f, animationSpec = PlutoMotion.spatialFast()) { v, _ -> hero.emphasis = v }
            } else {
                val wasIdle = hero.emphasis < 0.5f
                coroutineScope {
                    launch { animate(hero.emphasis, 1f, animationSpec = PlutoMotion.spatialFast()) { v, _ -> hero.emphasis = v } }
                    if (wasIdle) {
                        // A short "lift" as focus lands on the card (scale 1.0 -> 1.04 -> 1.0).
                        animate(0f, 1f, animationSpec = tween(HERO_LIFT_MS, easing = LinearEasing)) { v, _ ->
                            hero.lift = HERO_LIFT * sin(v * PI.toFloat())
                        }
                        hero.lift = 0f
                    }
                }
            }
        }
    }
}

private const val HERO_LIFT = 0.04f
private const val HERO_LIFT_MS = 260

/**
 * Title and guidance for an empty shelf, in the words of the current input: controller
 * buttons ([actionsKey], e.g. "X") while a controller is connected, touch otherwise.
 */
internal fun emptyTitle(id: ShelfId, name: String, actionsKey: String?): Pair<String, String> {
    val openActions = if (actionsKey != null) "Press $actionsKey on an app" else "Press and hold an app"
    return when (id) {
        ShelfId.Recent -> "No recent launches yet" to "Apps you open from Pluto show up here, most recent first."
        ShelfId.Favourites -> "No favourites yet" to "$openActions and choose Pin to home to add it here."
        is ShelfId.OfRomLibrary -> "No games in $name yet" to "Add games to this tab’s folder, or choose another folder in ROM tabs."
        is ShelfId.OfCategory -> "No apps in $name yet" to "Choose Add apps to pick what goes here."
    }
}

/**
 * The active shelf and the fading outgoing flow. Progress is read in layout/draw only;
 * interrupted switches preserve the most visible page and reverse continuously.
 */
@Stable
internal class ShelfSwitch(initialShelf: ShelfId, initialStage: CoverflowState) {
    var shelf: ShelfId = initialShelf
    var stage: CoverflowState by mutableStateOf(initialStage)
    var outgoingShelf: ShelfId? = null
    var outgoingStage: CoverflowState? by mutableStateOf(null)
    var progress by mutableFloatStateOf(1f)
    var focusedAtSwitch: String? = null
    private var incomingStartAlpha = 0f
    private var incomingStartScale = 1.08f
    private var outgoingStartAlpha = 1f
    private var outgoingStartScale = 1f

    fun begin(next: ShelfId, nextStage: CoverflowState) {
        val inAlpha = incomingMotion.alpha()
        val inScale = incomingMotion.scale()
        val outAlpha = outgoingMotion.alpha()
        val outScale = outgoingMotion.scale()
        if (next == outgoingShelf && outgoingStage != null) {
            // Reversing returns to the actual visible page, preserving its opacity and size.
            val returningStage = outgoingStage!!
            outgoingStage = stage
            outgoingShelf = shelf
            stage = returningStage
            incomingStartAlpha = outAlpha
            incomingStartScale = outScale
            outgoingStartAlpha = inAlpha
            outgoingStartScale = inScale
        } else {
            // Rapid shoulder presses retain the more visible page instead of flashing a
            // barely arrived shelf. The newly requested shelf always starts immediately.
            if (outgoingStage == null || inAlpha >= outAlpha) {
                outgoingStage = stage
                outgoingShelf = shelf
                outgoingStartAlpha = inAlpha
                outgoingStartScale = inScale
            } else {
                outgoingStartAlpha = outAlpha
                outgoingStartScale = outScale
            }
            stage = nextStage
            incomingStartAlpha = 0f
            incomingStartScale = 1f + SHELF_ENTRANCE_SCALE
        }
        shelf = next
        progress = 0f
    }

    val incomingMotion = PageMotion(
        offset = { 0f },
        alpha = { incomingStartAlpha + (1f - incomingStartAlpha) * progress },
        scale = { incomingStartScale + (1f - incomingStartScale) * progress },
    )
    val outgoingMotion = PageMotion(
        offset = { 0f },
        alpha = { outgoingStartAlpha * (1f - progress) },
        scale = { outgoingStartScale + (1.02f - outgoingStartScale) * progress },
    )
}

/**
 * Keeps the selected card focused across appearance, rotation, controller connection and
 * shelf switches. While touch drives, the target is only remembered (and outlined), so the
 * next controller input continues from the selected card.
 */
@Composable
private fun ConsoleFocusEffects(
    state: LauncherUiState,
    focus: ControllerFocusController,
    switch: ShelfSwitch,
    active: ShelfId,
    emptyDefault: String?,
    hasEditButton: Boolean,
    selectedCardId: () -> String?,
) {
    val currentState by rememberUpdatedState(state)
    val currentSelected by rememberUpdatedState(selectedCardId)
    val currentEmpty by rememberUpdatedState(emptyDefault)
    val currentHasEdit by rememberUpdatedState(hasEditButton)

    // Back on the console after a layer closed: the layer restores focus to the control that
    // opened it, but that card may be gone (unpinned or hidden the last app of a shelf). If
    // focus found nowhere to land, put it on the selected card or the empty state's button.
    val atHome = state.session.layers.isEmpty()
    LaunchedEffect(atHome) {
        if (!atHome) return@LaunchedEffect
        repeat(RESTORE_GRACE_FRAMES) { withFrameNanos { } }
        if (focus.inputMode != InputMode.CONTROLLER || focus.focusedId != null) return@LaunchedEffect
        if (currentState.session.layers.isNotEmpty()) return@LaunchedEffect
        focus.requestFocusWhenReady(currentSelected() ?: currentEmpty ?: shelfTabId(active))
    }

    // Layout (re)appears or a controller connects: after the first frame, unless focus was
    // already placed (a layer restore).
    LaunchedEffect(state.controllerConnected) {
        withFrameNanos { }
        if (currentState.session.layers.isNotEmpty() || focus.focusedId != null) return@LaunchedEffect
        val target = currentSelected() ?: currentEmpty ?: shelfTabId(active)
        if (focus.inputMode == InputMode.CONTROLLER) focus.requestFocusWhenReady(target) else focus.rememberFocusTarget(target)
    }

    // Shelf switched (L1/R1 or a tab): focus follows to the incoming flow's selected card,
    // or stays on the tabs (moving to the new active tab) when that is where the user was.
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(active) {
        if (firstRun[0]) {
            firstRun[0] = false
            return@LaunchedEffect
        }
        val was = switch.focusedAtSwitch
        switch.focusedAtSwitch = null
        withFrameNanos { }
        if (currentState.session.layers.isNotEmpty()) return@LaunchedEffect
        val target = when {
            was != null && was.startsWith(TAB_ID_PREFIX) -> shelfTabId(active)
            was == null || was.startsWith(CARD_ID_PREFIX) || was.startsWith("hh:empty:") ->
                currentSelected() ?: currentEmpty ?: shelfTabId(active)
            // Only user categories with apps have an Edit button: stay in the button row.
            was == ID_HH_EDIT_SHELF && !currentHasEdit -> ID_HH_SEARCH
            else -> was
        }
        if (focus.inputMode == InputMode.CONTROLLER) {
            if (focus.focusedId != target) focus.requestFocusWhenReady(target)
        } else {
            // Switched by touch: the next controller input starts on the new shelf's card,
            // not on a top-bar button the controller happened to visit before.
            focus.rememberFocusTarget(currentSelected() ?: currentEmpty ?: shelfTabId(active))
        }
    }
}

/**
 * The button legend for where controller focus is ([consoleLegendFor]). Reads focus itself,
 * so moving focus recomposes only the legend.
 */
@Composable
private fun ConsoleButtonLegend(mapping: ButtonMapping, hasApps: Boolean) {
    val focus = LocalControllerFocus.current
    ButtonLegend(mapping, consoleLegendFor(focus.focusedId, hasApps), quiet = true)
}

/** Console copy (inline for 0.1). */
internal object ConsoleText {
    const val TOUCH_HINT = "Tap to open · Press and hold for options"
}

/** Frames a closing layer gets to restore focus itself before the console steps in. */
private const val RESTORE_GRACE_FRAMES = 12

/** Returns the previously returned list while [value] is equal to it (content), so skipping works. */
@Composable
private fun <T> rememberEqualList(value: List<T>): List<T> {
    val holder = remember { ListHolder<T>() }
    val previous = holder.value
    if (previous != null && (previous === value || previous == value)) return previous
    holder.value = value
    return value
}

private class ListHolder<T> {
    var value: List<T>? = null
}

