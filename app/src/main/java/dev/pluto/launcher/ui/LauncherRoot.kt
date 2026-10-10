package dev.pluto.launcher.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.pluto.launcher.ui.widgets.WidgetActionsSheet
import dev.pluto.launcher.ui.widgets.WidgetPickerScreen
import dev.pluto.launcher.ui.home.HomeDragOverlay
import dev.pluto.launcher.ui.home.LocalHomeDrag
import dev.pluto.launcher.ui.home.HomeDragController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pluto.launcher.LauncherApplication
import dev.pluto.launcher.R
import dev.pluto.launcher.domain.ModeResolver
import dev.pluto.launcher.input.ControllerInputRouter
import dev.pluto.launcher.input.LauncherAction
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.components.MessageBar
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.focusInert
import dev.pluto.launcher.ui.focus.FocusRingOverlayHost
import dev.pluto.launcher.ui.focus.LocalFocusRingVisibility
import dev.pluto.launcher.ui.focus.ProvideControllerFocus
import dev.pluto.launcher.ui.home.DRAWER_SEARCH_ID
import dev.pluto.launcher.ui.home.DrawerScreen
import dev.pluto.launcher.ui.home.FolderOverlay
import dev.pluto.launcher.ui.home.HandheldLayout
import dev.pluto.launcher.ui.home.LandscapeLayout
import dev.pluto.launcher.ui.home.PhoneLayout
import dev.pluto.launcher.ui.home.iconSize
import dev.pluto.launcher.ui.motion.AppLauncher
import dev.pluto.launcher.ui.motion.DrawerRevealState
import dev.pluto.launcher.ui.motion.InertBackDispatcherOwner
import dev.pluto.launcher.ui.motion.LayerMotion
import dev.pluto.launcher.ui.motion.LayerStack
import dev.pluto.launcher.ui.motion.LocalAppLauncher
import dev.pluto.launcher.ui.motion.LocalDrawerReveal
import dev.pluto.launcher.ui.motion.LocalLaunchSourceFactory
import dev.pluto.launcher.ui.motion.LocalOriginRegistry
import dev.pluto.launcher.ui.motion.OriginRegistry
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.motion.StackSlot
import dev.pluto.launcher.ui.motion.blockPointerInput
import dev.pluto.launcher.ui.motion.drawMotion
import dev.pluto.launcher.ui.motion.LaunchMotion
import dev.pluto.launcher.ui.motion.LaunchVeil
import dev.pluto.launcher.ui.motion.LaunchVeilState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import dev.pluto.launcher.ui.overlay.AppActionsSheet
import dev.pluto.launcher.ui.overlay.CategoryMembershipDialog
import dev.pluto.launcher.ui.overlay.EditScreen
import dev.pluto.launcher.ui.overlay.ShelfPickerScreen
import dev.pluto.launcher.ui.overlay.MoveToFolderDialog
import dev.pluto.launcher.ui.settings.CategoriesScreen
import dev.pluto.launcher.ui.settings.ControllerSettingsScreen
import dev.pluto.launcher.ui.settings.HiddenAppsScreen
import dev.pluto.launcher.ui.settings.OnboardingScreen
import dev.pluto.launcher.ui.settings.SettingsScreen
import dev.pluto.launcher.ui.theme.LocalReducedMotion
import dev.pluto.launcher.ui.theme.PlutoTheme
import dev.pluto.launcher.ui.theme.WallpaperScrim
import dev.pluto.launcher.ui.theme.XmbBackground
import dev.pluto.launcher.ui.theme.PlutoBackground
import dev.pluto.launcher.data.prefs.BackgroundChoice
import dev.pluto.launcher.ui.theme.xmbBaseColor
import dev.pluto.launcher.ui.theme.backgroundFor
import android.os.Looper
import android.os.MessageQueue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The router owned by MainActivity; settings screens use it for raw-key capture and diagnostics. */
val LocalControllerRouter = staticCompositionLocalOf<ControllerInputRouter?> { null }

/**
 * Hides content beneath a modal layer or dialog from screen readers. Focus is already
 * blocked there through [LocalFocusInert]; this keeps TalkBack from reading or activating it.
 */
internal fun Modifier.hiddenFromAccessibility(hidden: Boolean): Modifier =
    if (hidden) clearAndSetSemantics { } else this

/**
 * Top-level composable. Applies theme + wallpaper scrim, measures the window
 * (calls vm.onWindowSizeChanged), picks Phone / Landscape / Handheld layout for the
 * measured window in the same frame (the view model's state.mode follows asynchronously,
 * which would otherwise show the previous mode's layout stretched for a frame or more),
 * renders the layer stack above it, the message snackbar, and routes [LauncherAction]s
 * collected from [actions] through the focus controller and VM.
 */
@Composable
fun LauncherRoot(vm: LauncherViewModel, actions: Flow<LauncherAction>) {
    // Snapshot state written together with the live session (see LauncherViewModel.uiState).
    val state by vm.uiState
    val focus = remember { ControllerFocusController() }
    val context = LocalContext.current
    val iconCache = remember(context) { (context.applicationContext as LauncherApplication).container.icons }

    // Controller attach / detach switches the presentation immediately (before the new mode's
    // layout restores focus): controller ring and hints on attach, touch presentation on
    // removal with the current control kept as the selection.
    val connectedBefore = remember { booleanArrayOf(false) }
    SideEffect {
        val connected = state.controllerConnected
        if (connected != connectedBefore[0]) {
            connectedBefore[0] = connected
            if (connected) focus.onControllerConnected() else focus.onControllerDisconnected()
        }
    }

    // Motion plumbing shared by every surface: the drawer's continuous position, where
    // controls were drawn, and app launches that open out of the activated tile.
    // Restored with the drawer open (process death): start open, no first-frame flash.
    val reveal = remember { DrawerRevealState(initiallyOpen = Layer.Drawer in state.session.layers) }
    val origins = remember { OriginRegistry() }
    val launchSources = LocalLaunchSourceFactory.current
    val reducedMotion by rememberUpdatedState(state.settings.reducedMotion)
    val veil = remember { LaunchVeilState() }
    val launchScope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val appLauncher = remember(vm, origins, launchSources, veil, lifecycle) {
        AppLauncher { key, originId ->
            val bounds = origins.boundsOf(originId)
            // Reduce motion keeps the source bounds but uses the default window transition.
            val source = if (bounds != null) launchSources?.create(bounds, animate = !reducedMotion) else null
            if (bounds == null || source == null || reducedMotion) {
                vm.launch(key, source?.screenBounds, source?.options)
            } else if (veil.progress.value == 0f) {
                // The tile visibly hands off to the app (see LaunchVeilState), then it starts.
                launchScope.launch {
                    veil.cover(bounds)
                    vm.launch(key, source.screenBounds, source.options)
                    delay(LaunchMotion.VEIL_TIMEOUT_MS)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) veil.reveal()
                }
            }
        }
    }
    DisposableEffect(lifecycle, veil) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) launchScope.launch { veil.clear() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Drag and drop on Home: started by a tile, followed here (the root sees every pointer first).
    val haptics = LocalHapticFeedback.current
    val drag = remember(vm) {
        HomeDragController(
            onStart = { source ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.beginHomeDrag(fromDrawer = source is DragSource.Drawer)
            },
            onDrop = { source, target -> vm.applyDrop(source, target) },
        )
    }

    CompositionLocalProvider(
        LocalIconCache provides iconCache,
        LocalDrawerReveal provides reveal,
        LocalOriginRegistry provides origins,
        LocalAppLauncher provides appLauncher,
        LocalHomeDrag provides drag,
    ) {
        PlutoTheme(state.settings) {
            ProvideControllerFocus(focus) {
                // One focus ring that glides between controls (instead of a ring per control
                // cross-fading), drawn above every layer of the launcher window.
                FocusRingOverlayHost {
                // The window size is known in composition (LocalWindowInfo, updated on the
                // configuration change before the rotation frame), so the layout for it is
                // composed in the composition phase, not inside a measure pass as a top-level
                // BoxWithConstraints subcomposition would.
                val density = LocalDensity.current
                val windowPx = LocalWindowInfo.current.containerSize
                val widthDp = (windowPx.width / density.density).toInt()
                val heightDp = (windowPx.height / density.density).toInt()
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(focus) {
                            // Any touch takes over from the controller immediately; never consumed.
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) focus.onTouch()
                                    // A drag in progress follows its finger wherever the tile that started it went.
                                    if (drag.isActive) {
                                        val change = event.changes.firstOrNull { it.id == drag.pointerId }
                                        when {
                                            change == null -> if (event.changes.none { it.pressed }) drag.cancel()
                                            change.pressed -> drag.move(change.position)
                                            else -> drag.release()
                                        }
                                    }
                                }
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            // Hardware keyboard navigation shows the focus outline again.
                            if (event.type == KeyEventType.KeyDown && event.key in KeyboardNavigationKeys) {
                                focus.onKeyboardNavigation()
                            }
                            false
                        },
                ) {
                    LaunchedEffect(widthDp, heightDp) {
                        if (widthDp > 0 && heightDp > 0) vm.onWindowSizeChanged(widthDp, heightDp)
                    }
                    // Same rule and inputs as the view model, resolved synchronously for this frame.
                    val mode = remember(widthDp, heightDp, state.controllerConnected, state.settings.handheldAppearance) {
                        ModeResolver.resolve(widthDp, heightDp, state.controllerConnected, state.settings.handheldAppearance)
                    }

                    when (state.settings.backgroundFor(console = mode == LauncherMode.HANDHELD)) {
                        BackgroundChoice.PLUTO -> PlutoBackground(state.settings.backgroundFrameRate, animate = !state.settings.reducedMotion)
                        BackgroundChoice.XMB -> XmbBackground(state.settings.xmbBaseColor(), animate = !state.settings.reducedMotion)
                        BackgroundChoice.WALLPAPER -> WallpaperScrim(state.settings)
                    }
                    val storageError = state.storageError
                    if (storageError != null) {
                        StorageErrorScreen(storageError, onRetry = vm::retryStorage)
                    } else if (state.loading) {
                        LoadingPlaceholder()
                    } else {
                        LauncherContent(state, mode, landscapeWindow = widthDp > heightDp, vm, focus)
                    }

                    MessageBar(
                        message = state.message,
                        onDismiss = vm::dismissMessage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .widthIn(max = 560.dp),
                    )
                    HomeDragOverlay(drag, state.iconSize())
                    LaunchVeil(veil, MaterialTheme.colorScheme.surface, Modifier.matchParentSize())
                }
                }
            }
        }
    }

    ActionRouting(state, vm, focus, actions)
    if (!state.loading) PrewarmIcons(state, iconCache)
}

/**
 * Decodes app icons ahead of need, in the background, at every size the current
 * presentation can show (home and the drawer, Handheld rows and tiles, onboarding and edit
 * lists), so no surface entering with motion (a folder growing open, a category page
 * sliding in, the drawer) ever shows placeholders that pop in. What is on home (grid and
 * dock) goes first, right away; the rest of the catalog follows once the launcher is idle.
 */
@Composable
private fun PrewarmIcons(state: LauncherUiState, cache: dev.pluto.launcher.apps.IconCache) {
    val density = LocalDensity.current
    val handheldPossible = state.mode == LauncherMode.HANDHELD || state.controllerConnected
    val baseSize = state.iconSize()
    val sizes = remember(baseSize, handheldPossible, density) {
        buildList {
            add(baseSize)
            if (handheldPossible) {
                val handheld = state.iconSize(base = 68.dp)
                add(handheld)
                add(handheld * 0.85f)
            }
            // Onboarding and Edit lists.
            add(36.dp)
        }.map { with(density) { it.roundToPx() } }.distinct()
    }
    val all = state.allApps
    val home = state.homeTiles
    val dock = state.dock
    LaunchedEffect(all, home, dock, sizes) {
        val first = buildList {
            dock.forEach { if (it != null) add(it.key) }
            home.forEach { tile ->
                when (tile) {
                    is HomeTile.App -> add(tile.entry.key)
                    is HomeTile.FolderTile -> tile.folder.apps.forEach { add(it.key) }
                    is HomeTile.Widget -> Unit
                }
            }
        }
        cache.prefetch(first, sizes.take(1))
        delay(ICON_PREWARM_DELAY_MS)
        cache.prefetch(all.map { it.key }, sizes)
    }
}

private const val ICON_PREWARM_DELAY_MS = 300L

/**
 * Routes controller actions: the focus layer first, then launcher-level shortcuts.
 * Back closes the soft keyboard first (like system Back does), then search, dialogs and layers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRouting(
    state: LauncherUiState,
    vm: LauncherViewModel,
    focus: ControllerFocusController,
    actions: Flow<LauncherAction>,
) {
    val currentState by rememberUpdatedState(state)
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)
    // Back goes through the Activity's dispatcher so nested dialogs inside a layer (BackHandler)
    // close first; MainActivity's own callback falls back to vm.back().
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(actions, vm, focus, backDispatcher) {
        actions.collect { action ->
            if (focus.handle(action)) return@collect
            val top = currentState.session.topLayer
            // Onboarding sits above everything; launcher shortcuts would open layers hidden beneath it.
            if (top == Layer.Onboarding && action != LauncherAction.Back) return@collect
            when (action) {
                LauncherAction.Back -> when {
                    // The controller's Back never reaches the IME, so close the keyboard here first.
                    imeVisible && keyboard != null -> keyboard?.hide()
                    backDispatcher != null -> backDispatcher.onBackPressed()
                    else -> vm.back()
                }
                LauncherAction.Search -> {
                    if (top == Layer.Drawer) {
                        vm.setSearchActive(true)
                        focus.requestFocus(DRAWER_SEARCH_ID)
                    } else if (top == null || top is Layer.FolderLayer) {
                        vm.openDrawer(withSearch = true)
                    }
                }
                LauncherAction.Settings -> if (top != Layer.Settings) vm.openLayer(Layer.Settings)
                // Category switching only where categories are visible: Handheld home and the drawer.
                // Phone/Landscape home shows no tabs, so a press there would filter the drawer unseen.
                LauncherAction.PrevCategory -> if (currentState.browsingCategories()) vm.previousCategory()
                LauncherAction.NextCategory -> if (currentState.browsingCategories()) vm.nextCategory()
                else -> Unit
            }
        }
    }
}

private fun LauncherUiState.browsingCategories(): Boolean {
    val top = session.topLayer
    return top == Layer.Drawer || (top == null && mode == LauncherMode.HANDHELD)
}

/** How a layer moves in and out, and what its motion does to the layers beneath it. */
private enum class LayerKind { DRAWER, FOLDER, SHEET, PAGE, ONBOARDING }

private val Layer.kind: LayerKind
    get() = when (this) {
        Layer.Drawer -> LayerKind.DRAWER
        is Layer.FolderLayer -> LayerKind.FOLDER
        // Bottom-sheet/dialog style; they draw their own ModalPanel scrim.
        is Layer.AppActions, is Layer.CategoryMembership, is Layer.MoveToFolder, is Layer.WidgetActions -> LayerKind.SHEET
        Layer.Edit, Layer.Settings, Layer.HiddenApps, Layer.Categories, Layer.ControllerSettings, is Layer.ShelfPicker, Layer.WidgetPicker -> LayerKind.PAGE
        Layer.Onboarding -> LayerKind.ONBOARDING
    }

/**
 * Full-screen layers whose arrival makes what is beneath them shift and fade out. Once such
 * a layer has fully entered, everything beneath it is invisible and is not composed.
 * The drawer is full-screen too, but home stays composed beneath it (receded, visible
 * through the drawer panel), so it is not one of these.
 */
private val LayerKind.coversBelow: Boolean get() = this == LayerKind.PAGE || this == LayerKind.ONBOARDING

/** One rendered layer: its identity, its entry/exit progress and where it opened from. */
@Stable
private class LayerEntry(val key: String, val layer: Layer, shown: Boolean, val originId: String?) {
    val kind: LayerKind = layer.kind

    /** 0 = absent .. 1 = fully shown. The drawer ignores it and follows [DrawerRevealState]. */
    val progress = Animatable(if (shown) 1f else 0f)

    /** Fully in (read in composition; changes only twice per transition, not per frame). */
    val settledIn: State<Boolean> = derivedStateOf { progress.value >= 1f }
}

/** What the motion of the layers above does to one layer (or to the home base). */
@Immutable
private class Beneath(
    /** A drawer is above: recede with the reveal progress. */
    val drawer: Boolean,
    /** Pages / onboarding above: shift toward the start and fade out as they come in. */
    val covers: List<LayerEntry>,
)

private val NothingAbove = Beneath(drawer = false, covers = emptyList())

/**
 * Layer bookkeeping that outlives the navigation stack: layers removed by the ViewModel stay
 * rendered (focus-inert, hidden from accessibility) until their exit animation finishes.
 * Pure ordering logic lives in [LayerStack]; this adds the per-layer animation state.
 */
@Stable
private class LayerHostState {
    val entries = HashMap<String, LayerEntry>()
    private var slots: List<StackSlot> = emptyList()
    private val finished = HashSet<String>()

    /** Bumped when a leaving layer finished animating out, so the host recomposes and drops it. */
    val version = mutableIntStateOf(0)

    /** The host's window position, for transform origins (plain field, read in the draw phase). */
    var windowOffset: Offset = Offset.Zero

    fun finish(key: String) {
        finished += key
        version.intValue++
    }

    fun covers(slot: StackSlot): Boolean {
        val entry = entries[slot.key] ?: return false
        return entry.kind.coversBelow && entry.settledIn.value
    }

    /**
     * The slots to render for [layers]. [drawerVisible]: the drawer is on screen (open,
     * animating or being dragged) even if it is not in the stack. [shown]: newly seen layers
     * start fully shown (first composition: restored state must not animate in).
     */
    fun update(
        layers: List<Layer>,
        drawerVisible: Boolean,
        parkDrawer: Boolean,
        shown: Boolean,
        openerId: () -> String?,
    ): List<StackSlot> {
        var next = LayerStack.reconcile(slots, layers.map(Layer::encode))
        next = LayerStack.withoutFinished(next, finished)
        finished.clear()

        val drawerIndex = next.indexOfFirst { it.key == DRAWER_KEY }
        val covered = next.any { it.present && entries[it.key]?.kind?.coversBelow == true }
        if (drawerIndex < 0 && drawerVisible && !covered) {
            // Dragged up from home before the ViewModel knows: render it, not yet focusable.
            next = next + StackSlot(DRAWER_KEY, present = false)
        } else if (drawerIndex < 0 && parkDrawer && !covered) {
            // Closed: keep it composed off screen (inert, invisible) just above home, so the
            // next open only moves a layer instead of composing the whole drawer in one frame.
            next = listOf(StackSlot(DRAWER_KEY, present = false)) + next
        } else if (drawerIndex >= 0 && !next[drawerIndex].present && !drawerVisible && !parkDrawer) {
            next = next.filterIndexed { i, _ -> i != drawerIndex }
        }

        for (slot in next) {
            if (slot.key !in entries) {
                val layer = layers.firstOrNull { Layer.encode(it) == slot.key } ?: Layer.decode(slot.key) ?: Layer.Drawer
                entries[slot.key] = LayerEntry(slot.key, layer, shown, openerId())
            }
        }
        // Leaving layers hidden beneath a settled page would never finish; drop them.
        next = LayerStack.withoutHiddenLeaving(next, LayerStack.firstComposed(next, ::covers))
        if (entries.size != next.size) {
            val keep = next.mapTo(HashSet()) { it.key }
            entries.keys.retainAll(keep)
        }
        slots = next
        return next
    }

    fun beneathOf(slots: List<StackSlot>, index: Int): Beneath {
        var drawer = false
        var covers: MutableList<LayerEntry>? = null
        for (i in index + 1 until slots.size) {
            val entry = entries[slots[i].key] ?: continue
            if (entry.kind == LayerKind.DRAWER) {
                drawer = true
            } else if (entry.kind.coversBelow) {
                val list = covers ?: ArrayList<LayerEntry>(2).also { covers = it }
                list.add(entry)
            }
        }
        return if (!drawer && covers == null) NothingAbove else Beneath(drawer, covers ?: emptyList())
    }

    private companion object {
        val DRAWER_KEY = Layer.encode(Layer.Drawer)
    }
}

/**
 * Base layout plus the layer stack, animated: layers enter and leave with motion that fits
 * their kind, a leaving layer stays composed (inert) until its exit finishes, and what is
 * beneath an entering/leaving full-screen layer stays composed while it can be seen.
 * Also does the focus bookkeeping for layers (opener remembered, restored on close).
 */
@Composable
private fun LauncherContent(
    state: LauncherUiState,
    mode: LauncherMode,
    landscapeWindow: Boolean,
    vm: LauncherViewModel,
    focus: ControllerFocusController,
) {
    val layers = state.session.layers
    val scope = rememberCoroutineScope()

    // Record the opener when layers are added; restore focus to it when they close.
    val previousCount = remember { intArrayOf(layers.size) }
    // The opener is read during composition, before this frame's changes are applied: once
    // they are, what lies beneath turns focus-inert, Compose clears the opener's focus and (in
    // keyboard mode) moves it to the layer's first control, so reading it in the SideEffect
    // recorded e.g. the drawer's close button and focus never returned to the opener.
    val opener = remember { arrayOfNulls<String>(1) }
    if (layers.size > previousCount[0]) {
        opener[0] = Snapshot.withoutReadObservation { focus.openerId }
    }
    SideEffect {
        val count = layers.size
        val before = previousCount[0]
        if (count > before) {
            val openerId = opener[0] ?: focus.openerId
            opener[0] = null
            repeat(count - before) { i -> focus.pushLayer(if (i == 0) openerId else null) }
        } else if (count < before) {
            var restoreId: String? = null
            repeat(before - count) { focus.popLayer()?.let { restoreId = it } }
            val id = restoreId
            if (id != null && focus.inputMode == InputMode.CONTROLLER) {
                // The opener may have vanished meanwhile (hidden, unpinned, moved into a folder):
                // then its nearest surviving neighbour takes focus, not the top of the screen.
                scope.launch { focus.focusOrNeighbour(id) }
            }
        }
        previousCount[0] = count
    }

    val reveal = LocalDrawerReveal.current
    DrawerRevealSync(reveal, drawerOpen = Layer.Drawer in layers)
    val drawerVisible by remember(reveal) { derivedStateOf { reveal.isVisible } }

    // The closed drawer is kept composed ("parked") once things are idle: after the first
    // frames, and again a moment after a full-screen page closed, so parking never adds to
    // those transitions' own work. A window size change (rotation) keeps it composed: it is
    // only laid out again, later, at idle (see ParkedSizeHold).
    val parkKey = ParkKey(layers.any { it.kind.coversBelow })
    var parkedFor by remember { mutableStateOf<ParkKey?>(null) }
    LaunchedEffect(parkKey) {
        if (parkKey.covered) return@LaunchedEffect
        delay(DRAWER_PARK_DELAY_MS)
        awaitMainIdle()
        withFrameNanos { }
        parkedFor = parkKey
    }
    val parkDrawer = parkedFor == parkKey

    val host = remember { LayerHostState() }
    val firstComposition = remember { booleanArrayOf(true) }
    host.version.intValue // recompose when a leaving layer finished
    val slots = host.update(layers, drawerVisible, parkDrawer, shown = firstComposition[0]) { focus.openerId }
    SideEffect { firstComposition[0] = false }

    // Forget a layer's saved state once it is gone from the stack and the screen.
    val layerStates = rememberSaveableStateHolder()
    val knownKeys = remember { HashSet<String>() }
    SideEffect {
        val live = slots.mapTo(HashSet()) { it.key }
        layers.mapTo(live) { Layer.encode(it) }
        knownKeys.filter { it !in live }.forEach { layerStates.removeState(it); knownKeys.remove(it) }
        knownKeys.addAll(live)
    }

    val firstComposed = LayerStack.firstComposed(slots, host::covers)
    val showBase = LayerStack.baseComposed(slots, host::covers)
    val topIndex = LayerStack.topPresent(slots)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LayerPredictiveBack(
        top = slots.getOrNull(topIndex)?.let { host.entries[it.key] },
        drawerSearchActive = state.session.searchActive || state.session.searchText.isNotEmpty(),
        reveal = reveal,
        vm = vm,
    )

    Box(Modifier.fillMaxSize().onPlaced { host.windowOffset = it.positionInWindow() }) {
        if (showBase) {
            val covered = layers.isNotEmpty()
            val beneath = host.beneathOf(slots, -1)
            CompositionLocalProvider(LocalFocusInert provides focusInert(covered)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .hiddenFromAccessibility(covered)
                        // Home has no text input: the keyboard (drawer search, rename dialogs)
                        // must not reflow the layout beneath the layer that raised it.
                        .consumeWindowInsets(HomeImeInsets)
                        .beneathMotion(beneath, reveal, rtl),
                ) {
                    ModeCrossfade(mode, landscapeWindow, rememberHomeView(state), vm)
                }
            }
        }
        for (index in firstComposed until slots.size) {
            val slot = slots[index]
            key(slot.key) {
                val entry = host.entries.getValue(slot.key)
                // Saved per layer: a layer covered by a settled page leaves composition, and its
                // scroll position (and other saveable UI state) must be there when it returns.
                layerStates.SaveableStateProvider(slot.key) {
                LayerFrame(
                    entry = entry,
                    present = slot.present,
                    parked = !slot.present && !drawerVisible,
                    isTop = index == topIndex,
                    beneath = host.beneathOf(slots, index),
                    reveal = reveal,
                    rtl = rtl,
                    host = host,
                    state = state,
                    vm = vm,
                )
                }
            }
        }
    }
}

/**
 * Keeps the drawer's continuous position in step with the layer stack: settles open when
 * Layer.Drawer is pushed, closed when it goes, and after every drag release. A release
 * heads straight for the gesture's outcome (carrying its fling) even before the
 * ViewModel's stack reflects it; if the stack never follows, it returns to the stack's state.
 */
@Composable
private fun DrawerRevealSync(reveal: DrawerRevealState, drawerOpen: Boolean) {
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(reveal, drawerOpen, reveal.isDragging) {
        if (firstRun[0]) {
            firstRun[0] = false
            // Started or restored with the drawer open: no opening animation.
            if (drawerOpen) {
                reveal.snap(true)
                return@LaunchedEffect
            }
        }
        if (reveal.isDragging) return@LaunchedEffect
        val pending = reveal.releaseTarget
        if (pending != null && pending != drawerOpen) {
            reveal.settle(pending)
            delay(VM_SYNC_GRACE_MS)
        }
        reveal.settle(drawerOpen)
    }
}

/** How long a released drag's outcome may wait for the ViewModel before the stack wins. */
private const val VM_SYNC_GRACE_MS = 600L

/** The keyboard's share of the insets beyond the system bars and cutout (consumed for home). */
private val HomeImeInsets: WindowInsets
    @Composable get() = WindowInsets.ime.exclude(WindowInsets.systemBars.union(WindowInsets.displayCutout))

/** What decides whether the closed drawer may stay composed. */
private data class ParkKey(val covered: Boolean)

/** Idle time before the closed drawer is (re)composed off screen. */
private const val DRAWER_PARK_DELAY_MS = 600L

/**
 * Suspends until the main thread's message queue is idle (no pending input, frames or
 * messages), so background composition steps start between bursts of work, not inside one.
 */
private suspend fun awaitMainIdle() = suspendCancellableCoroutine { cont ->
    val queue = Looper.getMainLooper().queue
    val handler = MessageQueue.IdleHandler {
        if (cont.isActive) cont.resume(Unit)
        false
    }
    queue.addIdleHandler(handler)
    cont.invokeOnCancellation { queue.removeIdleHandler(handler) }
}

/**
 * Home's view of [state]: the same instance until something home can show changed. The
 * drawer's search results are not part of home, so typing in the drawer never recomposes
 * home beneath it (the session is live in both, so home's effects always read it current).
 */
@Composable
private fun rememberHomeView(state: LauncherUiState): LauncherUiState {
    val held = remember { arrayOf(state) }
    val prev = held[0]
    if (prev !== state && (prev.drawerApps === state.drawerApps || prev.copy(drawerApps = state.drawerApps) != state)) {
        held[0] = state
    }
    return held[0]
}

/**
 * While the drawer is parked (closed, off screen) it is given a frozen copy of the state
 * that follows the live one only once it has stopped changing for a while
 * ([PARKED_CATCH_UP_MS] and an idle main thread), so browsing home (selection, L1/R1
 * categories, layers) never recomposes the hidden drawer in the same frames; a newer change
 * restarts the wait (input aborts a pending catch-up). Unparking hands it the live state.
 *
 * Catching up is spread over frames: the first step applies everything except new tiles
 * (tiles it already shows are kept), then the results grow by [PARK_CHUNK] tiles every
 * other frame, so the parked drawer never costs one long frame.
 */
@Composable
private fun rememberParkedState(state: LauncherUiState, parked: Boolean): LauncherUiState {
    val held = remember { arrayOf(if (parked) state.frozen().withDrawerApps(0) else state) }
    val target = remember { arrayOf(if (parked) state.frozen() else null) }
    val growing = remember { booleanArrayOf(parked) }
    val catchUp = remember { mutableIntStateOf(0) }
    catchUp.intValue // recompose when a parked update lands
    if (!parked) {
        held[0] = state
        target[0] = null
        growing[0] = false
        return state
    }
    // Just parked (the drawer finished closing): stop following the live session.
    if (held[0].sessionSource !is FixedSession) held[0] = held[0].frozen()
    val pending = target[0]
    if (pending == null || !parkedEquivalent(pending, state)) target[0] = state.frozen()
    val goal = target[0]!!
    LaunchedEffect(goal) {
        val current = held[0]
        if (!growing[0] && parkedEquivalent(current, goal) && current.drawerApps.size == goal.drawerApps.size) {
            return@LaunchedEffect
        }
        if (!growing[0]) {
            delay(PARKED_CATCH_UP_MS)
            awaitMainIdle()
        }
        growing[0] = true
        val apps = goal.drawerApps
        val shown = held[0].drawerApps
        var n = 0
        while (n < shown.size && n < apps.size && shown[n].key == apps[n].key) n++
        while (true) {
            held[0] = if (n >= apps.size) goal else goal.withDrawerApps(n)
            catchUp.intValue++
            if (n >= apps.size) break
            withFrameNanos { }
            withFrameNanos { }
            n += PARK_CHUNK
        }
        growing[0] = false
    }
    return held[0]
}

/**
 * True when the parked drawer would show the same for [a] and [b]: equal lists and settings,
 * same search and category. Layer changes elsewhere (a folder, a page) don't matter to it.
 */
private fun parkedEquivalent(a: LauncherUiState, b: LauncherUiState): Boolean {
    if (a === b) return true
    val sa = a.session
    val sb = b.session
    return sa.searchText == sb.searchText &&
        sa.searchActive == sb.searchActive &&
        sa.activeCategoryId == sb.activeCategoryId &&
        a.copy(sessionSource = b.sessionSource) == b
}

private fun LauncherUiState.withDrawerApps(count: Int): LauncherUiState =
    if (drawerApps.size <= count) this else copy(drawerApps = drawerApps.take(count))

private const val PARKED_CATCH_UP_MS = 1200L

/** Tiles the parked drawer adds per step while it is composed in the background. */
private const val PARK_CHUNK = 3

/**
 * Keeps the parked drawer laid out at its previous size after the window changed size
 * (rotation) until the launcher is idle again: it is invisible, and re-measuring it (and
 * re-composing its size-dependent parts) inside the rotation frame would only add to that
 * frame. Opening the drawer releases it at once.
 */
@Stable
private class ParkedSizeHold {
    private var held: Constraints? = null
    private var releasing: Job? = null

    /** Bumped when the hold ends, so the layout runs again with the real constraints. */
    val released = mutableIntStateOf(0)

    fun constraintsFor(incoming: Constraints, parked: Boolean, scope: CoroutineScope): Constraints {
        val h = held
        if (!parked || h == null || h == incoming) {
            held = incoming
            return incoming
        }
        if (releasing?.isActive != true) {
            releasing = scope.launch {
                delay(DRAWER_PARK_DELAY_MS)
                awaitMainIdle()
                withFrameNanos { }
                held = null
                released.intValue++
            }
        }
        return h
    }
}

@Composable
private fun LayerFrame(
    entry: LayerEntry,
    present: Boolean,
    parked: Boolean,
    isTop: Boolean,
    beneath: Beneath,
    reveal: DrawerRevealState,
    rtl: Boolean,
    host: LayerHostState,
    state: LauncherUiState,
    vm: LauncherViewModel,
) {
    if (entry.kind != LayerKind.DRAWER) {
        LaunchedEffect(entry, present) {
            val spec: AnimationSpec<Float> = when {
                present -> PlutoMotion.spatial()
                // Pages pop symmetrically; transient surfaces leave quicker than they came.
                entry.kind == LayerKind.PAGE -> PlutoMotion.spatial()
                else -> PlutoMotion.spatialFast()
            }
            entry.progress.animateTo(if (present) 1f else 0f, spec)
            if (!present) host.finish(entry.key)
        }
    }

    val active = present && isTop
    // A leaving layer keeps showing the state it had when it left (e.g. Edit keeps its
    // selection expanded while it fades out instead of collapsing mid-exit).
    // The session is live, so the session it had is remembered too (read while present, so
    // this frame follows every session change until it leaves) and frozen into its state.
    val lastPresentState = remember { arrayOf(state) }
    val lastPresentSession = remember { arrayOf(state.session) }
    val leavingState = remember { arrayOfNulls<LauncherUiState>(1) }
    if (present) {
        lastPresentState[0] = state
        lastPresentSession[0] = state.session
        leavingState[0] = null
    }
    val layerState = if (present || entry.kind == LayerKind.DRAWER) {
        state
    } else {
        leavingState[0] ?: lastPresentState[0].withSession(lastPresentSession[0]).also { leavingState[0] = it }
    }
    // A leaving layer's BackHandlers must not intercept Back while it animates out.
    val lifecycleOwner = LocalLifecycleOwner.current
    val inertBack = remember(lifecycleOwner) { InertBackDispatcherOwner(lifecycleOwner) }
    val realBack = LocalOnBackPressedDispatcherOwner.current
    val backOwner = if (present && realBack != null) realBack else inertBack
    val origins = LocalOriginRegistry.current

    // Draw-only transforms don't move focus geometry. Show the ring only at rest,
    // rather than drawing it detached from the control as the layer slides in.
    val interactionReady by remember(entry, reveal) {
        derivedStateOf {
            if (entry.kind == LayerKind.DRAWER) reveal.progress >= 0.995f && !reveal.isDragging
            else entry.progress.value >= 0.995f
        }
    }
    val ringVisibility: () -> Float = remember(entry, reveal) {
        if (entry.kind == LayerKind.DRAWER) {
            { if (reveal.progress >= 0.995f && !reveal.isDragging) 1f else 0f }
        } else {
            { if (entry.progress.value >= 0.995f) 1f else 0f }
        }
    }
    CompositionLocalProvider(
        LocalFocusInert provides focusInert(!active),
        LocalOnBackPressedDispatcherOwner provides backOwner,
        LocalFocusRingVisibility provides ringVisibility,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .hiddenFromAccessibility(!active)
                .beneathMotion(beneath, reveal, rtl),
        ) {
            // Hit targets are at rest while draw-only entry motion is still moving.
            // Don't launch a different tile by tapping a visual tile mid-transition.
            // A drawer drag that already owns the pointer stream remains uninterrupted.
            val draggingDrawer = entry.kind == LayerKind.DRAWER && reveal.isDragging
            val leavingBlock = if (present && (interactionReady || draggingDrawer)) Modifier else Modifier.blockPointerInput()
            when (entry.kind) {
                LayerKind.DRAWER -> {
                    val drawerState = rememberParkedState(state, parked)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val d = reveal.progress.coerceIn(0f, 1f)
                                if (d > 0f) drawRect(Color.Black, alpha = LayerMotion.DRAWER_DIM * d)
                            },
                    )
                    val hidden = remember(reveal) { derivedStateOf { reveal.progress <= 0f && !reveal.isDragging } }
                    val sizeHold = remember { ParkedSizeHold() }
                    val holdScope = rememberCoroutineScope()
                    Box(
                        Modifier
                            .fillMaxSize()
                            // Fully closed: laid out just below the window, so the parked drawer
                            // never takes touches meant for home (changes twice per open/close).
                            .layout { measurable, constraints ->
                                sizeHold.released.intValue // re-measure when a size hold ends
                                val isHidden = hidden.value
                                val placeable = measurable.measure(sizeHold.constraintsFor(constraints, parked && isHidden, holdScope))
                                val width = constraints.constrainWidth(placeable.width)
                                val height = constraints.constrainHeight(placeable.height)
                                layout(width, height) {
                                    placeable.place(0, if (isHidden) maxOf(height, placeable.height) else 0)
                                }
                            }
                            .drawMotion {
                                val d = reveal.progress
                                translationY = LayerMotion.drawerOffset(d, height)
                                alpha = LayerMotion.fadeEarly(d, 0.2f)
                            }
                            .then(leavingBlock),
                    ) {
                        LayerContent(entry.layer, drawerState, vm)
                    }
                }
                LayerKind.FOLDER -> {
                    val folderTileId = (entry.layer as? Layer.FolderLayer)?.let { HomeItem.FolderRef(it.folderId).id }
                    DismissScrim(progress = { entry.progress.value }, onDismiss = { if (active) vm.back() })
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawMotion {
                                val p = entry.progress.value
                                val s = LayerMotion.folderScale(p)
                                scaleX = s
                                scaleY = s
                                alpha = LayerMotion.fadeEarly(p, 0.6f)
                                transformOrigin = LayerMotion.originOf(
                                    origins.boundsOf(entry.originId) ?: origins.boundsOf(folderTileId),
                                    host.windowOffset,
                                    Size(width, height),
                                )
                            }
                            .then(leavingBlock),
                    ) {
                        LayerContent(entry.layer, layerState, vm)
                    }
                }
                LayerKind.SHEET -> Box(
                    Modifier
                        .fillMaxSize()
                        .drawMotion {
                            val p = entry.progress.value
                            // Settles from slightly larger, toward the tile that opened it; the
                            // panel's own full-screen scrim never shrinks to expose an edge.
                            val s = LayerMotion.sheetScale(p, entering = present)
                            scaleX = s
                            scaleY = s
                            alpha = LayerMotion.fadeEarly(p, 1f)
                            transformOrigin = LayerMotion.originOf(origins.boundsOf(entry.originId), host.windowOffset, Size(width, height))
                        }
                        .then(leavingBlock),
                ) {
                    LayerContent(entry.layer, layerState, vm)
                }
                LayerKind.PAGE -> Box(
                    Modifier
                        .fillMaxSize()
                        .drawMotion {
                            // Shared axis X: in from the end edge, out back to it.
                            val p = entry.progress.value
                            val travel = LayerMotion.pageTravel(width, density)
                            translationX = (if (rtl) -1f else 1f) * travel * (1f - p)
                            alpha = LayerMotion.fadeLate(p, 0.15f)
                        }
                        .then(leavingBlock),
                ) {
                    LayerContent(entry.layer, layerState, vm)
                }
                LayerKind.ONBOARDING -> Box(
                    Modifier
                        .fillMaxSize()
                        .drawMotion {
                            val p = entry.progress.value
                            val s = LayerMotion.onboardingScale(p)
                            scaleX = s
                            scaleY = s
                            alpha = LayerMotion.fadeEarly(p, 1f)
                        }
                        .then(leavingBlock),
                ) {
                    LayerContent(entry.layer, layerState, vm)
                }
            }
        }
    }
}

/**
 * The effect of the layers above on this one, in the draw phase: home recedes under the
 * drawer; anything under an arriving page shifts toward the start and fades out (shared
 * axis), so dropping it once the page has settled is invisible.
 */
private fun Modifier.beneathMotion(beneath: Beneath, reveal: DrawerRevealState, rtl: Boolean): Modifier =
    if (beneath === NothingAbove) {
        this
    } else {
        drawMotion {
            var a = 1f
            if (beneath.drawer) {
                val d = reveal.progress
                val s = LayerMotion.recedeScale(d)
                scaleX = s
                scaleY = s
                a *= LayerMotion.recedeAlpha(d)
                // The full-screen drawer hides everything below its top edge; only the strip
                // above it is drawn.
                if (d > 0f) clipBottom = LayerMotion.drawerOffset(d, height)
                // Receded home stays translucent for as long as the drawer is open: no
                // full-window offscreen buffer for it (only the strip above the panel shows).
                modulateAlpha = beneath.covers.isEmpty()
            }
            val covers = beneath.covers
            if (covers.isNotEmpty()) {
                var c = 0f
                var shift = 0f
                for (i in covers.indices) {
                    val p = covers[i].progress.value.coerceIn(0f, 1f)
                    if (p > c) c = p
                    if (covers[i].kind == LayerKind.PAGE && p > shift) shift = p
                }
                translationX = (if (rtl) 1f else -1f) * LayerMotion.beneathShift(width, density) * shift
                a *= LayerMotion.beneathPageAlpha(c)
            }
            alpha = a
        }
    }

private data class ModeTarget(val mode: LauncherMode, val landscape: Boolean)

/**
 * The home layout for [mode]. Switching mode in place (controller attached/removed while
 * landscape, Handheld appearance changed) cross-fades with a subtle scale. Rotation snaps:
 * the mode is resolved for the new window in the same frame and the system's rotation
 * animation covers it, so the old layout is never drawn stretched across the new window.
 */
@Composable
private fun ModeCrossfade(mode: LauncherMode, landscapeWindow: Boolean, state: LauncherUiState, vm: LauncherViewModel) {
    val target = ModeTarget(mode, landscapeWindow)
    AnimatedContent(
        targetState = target,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (initialState.landscape != targetState.landscape) {
                EnterTransition.None togetherWith ExitTransition.None using null
            } else {
                (fadeIn(PlutoMotion.fadeIn()) + scaleIn(PlutoMotion.spatial(), initialScale = MODE_ENTER_SCALE)) togetherWith
                    fadeOut(PlutoMotion.fadeOut()) using null
            }
        },
        contentKey = { it.mode },
        label = "mode",
    ) { shown ->
        val leaving = transition.targetState == EnterExitState.PostExit
        // Rotation: the outgoing layout was built for the other orientation; never measure or
        // draw it. It stays composed until AnimatedContent drops it a frame later, so disposing
        // its whole tree is not added to the rotation frame itself.
        val stale = leaving && shown.landscape != target.landscape
        // The outgoing layout is visual only: no focus, not read by screen readers.
        CompositionLocalProvider(LocalFocusInert provides focusInert(leaving)) {
            Box(
                Modifier
                    .then(if (stale) SkipLayout else Modifier)
                    .fillMaxSize()
                    .hiddenFromAccessibility(leaving),
            ) {
                when (shown.mode) {
                    LauncherMode.PHONE -> PhoneLayout(state, vm)
                    LauncherMode.LANDSCAPE -> LandscapeLayout(state, vm)
                    LauncherMode.HANDHELD -> HandheldLayout(state, vm)
                }
            }
        }
    }
}

/** Takes the minimum size and neither measures, places nor draws its content. */
private val SkipLayout = Modifier.layout { _, constraints -> layout(constraints.minWidth, constraints.minHeight) {} }

private const val MODE_ENTER_SCALE = 0.97f

/**
 * Predictive Back (Android 14+): the system Back swipe scrubs the top layer's exit (the
 * drawer slides down part way, a page slides toward the end edge, ...); releasing commits
 * Back through the ViewModel, cancelling springs the layer back. Registered before the
 * layers' own BackHandlers, so those (rename, search, nested dialogs) keep priority.
 * Disabled where Back doesn't close the layer: onboarding, and the drawer while a query is
 * typed (Back clears the query first); MainActivity's callback handles those as before.
 */
@Composable
private fun LayerPredictiveBack(
    top: LayerEntry?,
    drawerSearchActive: Boolean,
    reveal: DrawerRevealState,
    vm: LauncherViewModel,
) {
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val restoration = remember { arrayOfNulls<Job>(1) }
    DisposableEffect(top) { onDispose { restoration[0]?.cancel() } }
    val enabled = top != null && top.kind != LayerKind.ONBOARDING &&
        !(top.kind == LayerKind.DRAWER && drawerSearchActive)
    PredictiveBackHandler(enabled = enabled) { events ->
        val target = top
        restoration[0]?.cancel()
        // Starting Back halfway through entry must not snap the layer fully open.
        val initialProgress = if (target?.kind == LayerKind.DRAWER) reveal.progress else target?.progress?.value ?: 1f
        var dragging = false
        try {
            events.collect { event ->
                when {
                    target == null || reducedMotion -> Unit
                    target.kind == LayerKind.DRAWER -> {
                        if (!dragging) {
                            reveal.beginDrag()
                            dragging = true
                        }
                        reveal.dragBy(initialProgress * LayerMotion.backScrub(event.progress) - reveal.progress, 1f)
                    }
                    else -> target.progress.snapTo(initialProgress * LayerMotion.backScrub(event.progress))
                }
            }
            if (dragging) reveal.endDrag(open = false)
            vm.back()
        } catch (e: CancellationException) {
            if (dragging) {
                reveal.endDrag(open = true)
            } else if (target != null && target.kind != LayerKind.DRAWER) {
                restoration[0] = scope.launch { target.progress.animateTo(1f, PlutoMotion.spatial()) }
            }
            throw e
        }
    }
}

@Composable
private fun LayerContent(layer: Layer, state: LauncherUiState, vm: LauncherViewModel) {
    when (layer) {
        Layer.Drawer -> DrawerScreen(state, vm)
        is Layer.FolderLayer -> FolderOverlay(state, vm, layer.folderId)
        is Layer.AppActions -> AppActionsSheet(layer.key, state, vm)
        Layer.WidgetPicker -> WidgetPickerScreen(state, vm)
        is Layer.WidgetActions -> WidgetActionsSheet(layer.appWidgetId, state, vm)
        is Layer.CategoryMembership -> CategoryMembershipDialog(layer.key, state, vm)
        is Layer.MoveToFolder -> MoveToFolderDialog(layer.key, state, vm)
        Layer.Edit -> EditScreen(state, vm)
        Layer.Settings -> SettingsScreen(state, vm)
        Layer.HiddenApps -> HiddenAppsScreen(state, vm)
        Layer.Categories -> CategoriesScreen(state, vm)
        is Layer.ShelfPicker -> ShelfPickerScreen(layer.categoryId, state, vm)
        Layer.ControllerSettings -> ControllerSettingsScreen(state, vm)
        Layer.Onboarding -> OnboardingScreen(state, vm)
    }
}

private const val FOLDER_SCRIM_ALPHA = 0.32f

/** Dims what is beneath a dialog-style layer and closes the layer on an outside tap. */
@Composable
private fun DismissScrim(progress: () -> Float, onDismiss: () -> Unit) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            // Fades with the folder; drawn in the draw phase only.
            .drawBehind { drawRect(Color.Black, alpha = FOLDER_SCRIM_ALPHA * progress().coerceIn(0f, 1f)) }
            .clearAndSetSemantics { }
            .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } },
    )
}

/** Minimal, non-blank first-load placeholder: the Pluto mark and a progress indicator. */
@Composable
private fun LoadingPlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Text("Pluto", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(
            "Loading apps…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private val KeyboardNavigationKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Tab,
)
