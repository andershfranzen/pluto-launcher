package dev.pluto.launcher.ui.home

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
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.ClockHeader
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
import dev.pluto.launcher.ui.console.ConsoleLegendCompact
import dev.pluto.launcher.ui.console.HeroEmphasis
import dev.pluto.launcher.ui.console.ConsoleShelves
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pluto.launcher.ui.console.ConsoleTitle
import dev.pluto.launcher.ui.theme.ConsoleTheme
import dev.pluto.launcher.ui.console.ConsoleTopBar
import dev.pluto.launcher.ui.console.ControllerBattery
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
private const val ID_HH_OPEN = "hh:act:open"
private const val ID_HH_OPTIONS = "hh:act:options"
private const val ID_EMPTY_ALL_APPS = "hh:empty:allapps"
private const val ID_EMPTY_CATEGORIES = "hh:empty:categories"

/** The selection is mirrored into the shared session only once the flow rests this long (no emission per D-pad step). */
private const val SELECTION_COMMIT_DELAY_MS = 400L

/** How far a switching flow slides, as a fraction of the stage width. */
private const val SHELF_SLIDE = 0.25f

/** Shelf switch progress at which the outgoing page has faded out and the incoming one begins. */
private const val SHELF_HANDOVER = 0.45f

/**
 * Controller-first landscape layout ("console mode") as a Coverflow.
 *
 * Shelves form the tab row: Recent launches · Favourites · the categories (L1/R1 wrap, or
 * touch). The selected app sits centre stage, large and front-facing with a reflection;
 * neighbours tilt away in 3D, smaller and dimmed. D-pad / stick LEFT/RIGHT move the
 * selection (the flow glides continuously while held), A opens the app out of its card, X
 * opens its actions, UP reaches the tabs and DOWN the Open / Options buttons. Touch: drag
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
    HideStatusBarWhileShown()
    ConsoleTheme { HandheldStage(state, vm) }
}

/**
 * Console mode is immersive: the status bar (a second clock above Pluto's own) is hidden
 * while it shows and comes back with a swipe from the edge; it returns when the mode ends.
 * The navigation bar is left alone so system gestures behave as everywhere else.
 */
@Composable
private fun HideStatusBarWhileShown() {
    val view = LocalView.current
    if (view.isInEditMode) return
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
    val order = remember(state.categories, state.settings.historyEnabled) {
        ConsoleShelves.order(state.categories, state.settings.historyEnabled)
    }
    val tabs = remember(order, state.categories) { order.map { it to ConsoleShelves.title(it, state.categories) } }
    val favourites = rememberEqualList(remember(state.homeTiles) { ConsoleShelves.favouriteApps(state.homeTiles) })
    val active = vm.activeShelf(state)
    val shelf = remember(active, state.categories, state.categoryMembers, state.apps, favourites, state.recents) {
        ConsoleShelves.shelf(active, state.categories, state.categoryMembers, state.apps, favourites, state.recents)
    }
    val apps = rememberEqualList(shelf.apps)

    // Land on the default shelf once and keep it, so it doesn't change under the user when
    // their first recent launch or favourite appears.
    LaunchedEffect(Unit) {
        if (currentState.session.handheldShelf == null) vm.selectShelf(vm.activeShelf(currentState))
    }

    // --- Shelf switching: the flows slide across, direction-aware -------------------------
    val switch = remember {
        ShelfSwitch(active, CoverflowState(ConsoleShelves.initialIndex(apps, vm.shelfSelection(active), state.session.selectedApp), apps))
    }
    if (switch.shelf != active) {
        // Remember what held focus before the outgoing flow turns focus-inert (read without subscribing).
        switch.focusedAtSwitch = Snapshot.withoutReadObservation { focus.focusedId ?: focus.lastFocusedId }
        switch.direction = ConsoleShelves.direction(order, switch.shelf, active)
        switch.outgoingShelf = switch.shelf
        switch.outgoingStage = switch.stage
        switch.shelf = active
        switch.stage = CoverflowState(ConsoleShelves.initialIndex(apps, vm.shelfSelection(active), state.session.selectedApp), apps)
        switch.progress = 0f
    } else {
        switch.stage.sync(apps)
    }
    val stage = switch.stage
    val links = remember { ConsoleFocusLinks() }
    val hero = remember { HeroEmphasis() }
    HeroEmphasisEffect(focus, hero)

    LaunchedEffect(active) {
        if (switch.progress < 1f) {
            animate(switch.progress, 1f, animationSpec = PlutoMotion.spatial()) { value, _ -> switch.progress = value }
        }
        switch.progress = 1f
        switch.outgoingStage = null
        switch.outgoingShelf = null
    }
    // After a list change the selection may have shifted: let the flow settle on it.
    LaunchedEffect(stage, apps) { stage.select(stage.selectedIndex, scope) }

    // --- Selection, focus and launching -----------------------------------------------------
    fun selectedCardId(s: CoverflowState, shelfId: ShelfId): String? = s.selectedKey?.let { cardFocusId(shelfId.key, it) }

    fun noteSelection(s: CoverflowState, shelfId: ShelfId, index: Int) {
        val entry = s.apps.getOrNull(index) ?: return
        vm.rememberShelfSelection(shelfId, entry.key)
        val id = cardFocusId(shelfId.key, entry.key)
        focus.setDefaultFocus(id)
        // Touch moved the selection: the next controller input continues from here.
        if (focus.inputMode != InputMode.CONTROLLER) focus.rememberFocusTarget(id)
    }

    fun launch(s: CoverflowState, shelfId: ShelfId, entry: AppEntry) {
        val id = cardFocusId(shelfId.key, entry.key)
        s.selectedBounds()?.let { origins.report(id, it) }
        vm.onAppSelected(entry.key)
        launcher.launch(entry.key, id)
    }

    fun openActions(entry: AppEntry) {
        vm.onAppSelected(entry.key)
        vm.openLayer(Layer.AppActions(entry.key))
    }

    // Mirror the resting selection into the shared session (other layouts, selection by identity).
    LaunchedEffect(stage) {
        snapshotFlow { stage.selectedKey }.filterNotNull().distinctUntilChanged().collectLatest { key ->
            delay(SELECTION_COMMIT_DELAY_MS)
            if (currentState.session.selectedApp != key) vm.onAppSelected(key)
        }
    }

    val flowIds = remember(apps, active) { apps.map { cardFocusId(active.key, it.key) } }
    TrackFocusOrder(FLOW_SURFACE, flowIds) { index -> stage.select(index, scope) }

    val emptyDefault = if (apps.isEmpty()) ID_EMPTY_ALL_APPS else null
    SideEffect { focus.setDefaultFocus(selectedCardId(stage, active) ?: emptyDefault ?: shelfTabId(active)) }
    // Whenever touch takes over (or switches shelf), the next controller input returns to the
    // selected card, never to whatever top-bar button the controller last visited.
    LaunchedEffect(focus.inputMode, active, stage.selectedKey) {
        if (focus.inputMode == InputMode.TOUCH) {
            focus.rememberFocusTarget(selectedCardId(stage, active) ?: emptyDefault ?: shelfTabId(active))
        }
    }

    ConsoleFocusEffects(state, focus, switch, active, emptyDefault) { selectedCardId(stage, active) }

    // --- Layout -------------------------------------------------------------------------------
    val selectedCardFocus: () -> FocusRequester = { switch.stage.let { if (it.count > 0) it.selectedRequester() else FocusRequester.Default } }
    CompositionLocalProvider(LocalShowTouchSelection provides true) {
        Box(Modifier.fillMaxSize()) {
            ConsoleBackdrop(
                stage = { switch.stage },
                artPx = artPx,
                versions = { currentVersions },
                modifier = Modifier.matchParentSize(),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                ConsoleTopBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp),
                    tabs = {
                        ShelfTabs(
                            shelves = tabs,
                            active = active,
                            onSelect = { vm.selectShelf(it) },
                            links = links,
                            downTarget = selectedCardFocus,
                            prevKey = if (hints) mapping.promptFor(ControllerAction.PREV_CATEGORY) else null,
                            nextKey = if (hints) mapping.promptFor(ControllerAction.NEXT_CATEGORY) else null,
                        )
                    },
                    status = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ControllerBattery(state.controllers.firstOrNull()?.deviceId)
                            ClockHeader(compact = true, quiet = true)
                        }
                    },
                    buttons = {
                        val down = Modifier.focusProperties { down = selectedCardFocus() }
                        // Borderless on the dark stage: quieter than the shelf tabs.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlutoIconButton(ID_HH_SEARCH, Icons.Outlined.Search, "Search apps", { vm.openDrawer(withSearch = true) }, down)
                            PlutoIconButton(ID_HH_ALL_APPS, Icons.Outlined.Apps, "All apps", { vm.openDrawer() }, down)
                            PlutoIconButton(ID_HH_SETTINGS, Icons.Outlined.Settings, "Launcher settings", { vm.openLayer(Layer.Settings) }, down)
                        }
                    },
                )

                // The stage: the outgoing flow (while it slides away) under the active one.
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
                                emptyTitle = emptyTitle(outgoingShelf, ConsoleShelves.title(outgoingShelf, state.categories), actionsKey),
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
                            emptyTitle = emptyTitle(active, shelf.title, actionsKey),
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
                            downTarget = { links.openButton() },
                        )
                    }
                }

                // An empty shelf explains itself on the stage; no stray caption under it.
                if (apps.isNotEmpty()) {
                    ConsoleTitle(
                        stage = stage,
                        subtitle = shelf.subtitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
                // Open / Options centred under the title, the legend at the end: on one row
                // while they fit side by side (the stage keeps its height), else stacked.
                ConsoleBottomRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Clear of the navigation handle (not just its inset).
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = ConsoleBottomClearance),
                    actions = {
                        if (apps.isNotEmpty()) {
                            ConsoleActions(
                                links = links,
                                upTarget = selectedCardFocus,
                                hero = hero,
                                openKey = if (hints) mapping.promptFor(ControllerAction.CONFIRM) else null,
                                actionsKey = if (hints) mapping.promptFor(ControllerAction.ACTIONS) else null,
                                onOpen = { stage.selectedEntry?.let { launch(stage, active, it) } },
                                onOptions = { stage.selectedEntry?.let(::openActions) },
                            )
                        }
                    },
                    // Open and Actions carry their own button glyphs; the legend lists the rest.
                    legend = { if (hints) ButtonLegend(mapping, if (apps.isNotEmpty()) ConsoleLegendCompact else ConsoleLegend) },
                )
            }
        }
    }
}

/** Places [actions] centred and [legend] at the end on one row when they don't overlap; otherwise on two rows. */
@Composable
private fun ConsoleBottomRow(
    modifier: Modifier,
    actions: @Composable () -> Unit,
    legend: @Composable () -> Unit,
) {
    Layout(contents = listOf(actions, legend), modifier = modifier) { (actionsM, legendM), constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val legendP = legendM.firstOrNull()?.measure(loose)
        val actionsP = actionsM.firstOrNull()?.measure(loose)
        val gap = 12.dp.roundToPx()
        val legendW = legendP?.width ?: 0
        val actionsW = actionsP?.width ?: 0
        val limit = width - legendW - if (legendW > 0) gap else 0
        val centred = (width - actionsW) / 2
        val actionsX = if (centred + actionsW > limit) limit - actionsW else centred
        val legendH = legendP?.height ?: 0
        val actionsH = actionsP?.height ?: 0
        if (actionsX >= 0) {
            val height = maxOf(legendH, actionsH)
            layout(width, height) {
                actionsP?.place(actionsX, (height - actionsH) / 2)
                legendP?.place(width - legendW, (height - legendH) / 2)
            }
        } else {
            layout(width, actionsH + legendH) {
                actionsP?.place(centred.coerceAtLeast(0), 0)
                legendP?.place(width - legendW, actionsH)
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
    vm: LauncherViewModel,
    onActivate: (Int, AppEntry) -> Unit,
    onActions: (AppEntry) -> Unit,
    onFocused: (Int, AppEntry) -> Unit,
    onTouchSelect: (Int) -> Unit,
    upTarget: () -> FocusRequester,
    downTarget: () -> FocusRequester,
) {
    val icons = LocalIconCache.current
    val versions by icons.versions.collectAsState()
    CompositionLocalProvider(LocalFocusInert provides focusInert(leaving)) {
        val base = Modifier
            .fillMaxSize()
            .then(if (leaving) Modifier.clearAndSetSemantics { } else Modifier)
        if (stage.count == 0) {
            Box(
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
                    icon = Icons.Outlined.Apps,
                    // A readable measure, centred (not one line across the whole stage).
                    modifier = Modifier.widthIn(max = 560.dp),
                ) {
                    if (shelfId is ShelfId.OfCategory) {
                        PlutoTextButton(
                            ID_EMPTY_CATEGORIES, "Categories", { vm.openLayer(Layer.Categories) },
                            outline = Color.White.copy(alpha = 0.3f),
                        )
                    }
                    PlutoTextButton(ID_EMPTY_ALL_APPS, "All apps", { vm.openDrawer() }, emphasized = true)
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

/**
 * Open / Actions under the title (DOWN from the selected card). With a controller they show
 * its buttons ([openKey], [actionsKey]) instead of icons; they soften while focus is up in
 * the header, like the hero card.
 */
@Composable
private fun ConsoleActions(
    links: ConsoleFocusLinks,
    upTarget: () -> FocusRequester,
    hero: HeroEmphasis,
    openKey: String?,
    actionsKey: String?,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(links) {
        links.openAttached = true
        onDispose { links.openAttached = false }
    }
    val up = Modifier.focusProperties { up = upTarget() }
    Row(
        modifier.graphicsLayer { alpha = ACTIONS_IDLE_ALPHA + (1f - ACTIONS_IDLE_ALPHA) * hero.emphasis },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlutoTextButton(
            ID_HH_OPEN, "Open", onOpen, up.focusRequester(links.open),
            icon = Icons.Outlined.PlayArrow, emphasized = true, keyHint = openKey,
        )
        PlutoTextButton(ID_HH_OPTIONS, "Actions", onOptions, up, icon = Icons.Outlined.MoreHoriz, keyHint = actionsKey)
    }
}

/** Open / Actions while focus is up in the header. */
private const val ACTIONS_IDLE_ALPHA = 0.6f

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
        snapshotFlow {
            val id = focus.focusedId
            focus.inputMode == InputMode.CONTROLLER && id != null &&
                (id.startsWith(TAB_ID_PREFIX) || id == ID_HH_SEARCH || id == ID_HH_ALL_APPS || id == ID_HH_SETTINGS)
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
        is ShelfId.OfCategory -> "No apps in $name yet" to
            "$openActions in All apps and choose Categories…, or manage categories here."
    }
}

/**
 * The active shelf and its flow, plus the one sliding away. Plain fields are only written
 * during composition when the shelf changes; the slide progress is read in layout/draw only.
 */
@Stable
private class ShelfSwitch(initialShelf: ShelfId, initialStage: CoverflowState) {
    var shelf: ShelfId = initialShelf
    var stage: CoverflowState by mutableStateOf(initialStage)
    var outgoingShelf: ShelfId? = null
    var outgoingStage: CoverflowState? by mutableStateOf(null)
    var direction = 1
    var progress by mutableFloatStateOf(1f)
    var focusedAtSwitch: String? = null

    val incomingMotion = PageMotion(
        offset = { direction * (1f - progress) * SHELF_SLIDE },
        // Sequenced, never overlapping: the outgoing page is gone (progress 0.45) before
        // the incoming one starts to show.
        alpha = { (progress - SHELF_HANDOVER) / (1f - SHELF_HANDOVER) * 1.6f },
    )
    val outgoingMotion = PageMotion(
        offset = { -direction * progress * SHELF_SLIDE },
        alpha = { 1f - progress / SHELF_HANDOVER },
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
    selectedCardId: () -> String?,
) {
    val currentState by rememberUpdatedState(state)
    val currentSelected by rememberUpdatedState(selectedCardId)
    val currentEmpty by rememberUpdatedState(emptyDefault)

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
            was == null || was.startsWith("hh:card:") || was.startsWith("hh:empty:") || was.startsWith("hh:act:") ->
                currentSelected() ?: currentEmpty ?: shelfTabId(active)
            else -> was
        }
        if (focus.inputMode == InputMode.CONTROLLER) {
            if (focus.focusedId != target) focus.requestFocusWhenReady(target)
        } else {
            focus.rememberFocusTarget(target)
        }
    }
}

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
