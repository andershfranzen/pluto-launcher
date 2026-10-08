package dev.pluto.launcher.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pluto.launcher.LauncherApplication
import dev.pluto.launcher.R
import dev.pluto.launcher.domain.ModeResolver
import dev.pluto.launcher.input.ControllerInputRouter
import dev.pluto.launcher.input.LauncherAction
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.components.MessageBar
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.ProvideControllerFocus
import dev.pluto.launcher.ui.home.DRAWER_SEARCH_ID
import dev.pluto.launcher.ui.home.DrawerScreen
import dev.pluto.launcher.ui.home.FolderOverlay
import dev.pluto.launcher.ui.home.HandheldLayout
import dev.pluto.launcher.ui.home.LandscapeLayout
import dev.pluto.launcher.ui.home.PhoneLayout
import dev.pluto.launcher.ui.overlay.AppActionsSheet
import dev.pluto.launcher.ui.overlay.CategoryMembershipDialog
import dev.pluto.launcher.ui.overlay.EditScreen
import dev.pluto.launcher.ui.overlay.MoveToFolderDialog
import dev.pluto.launcher.ui.settings.CategoriesScreen
import dev.pluto.launcher.ui.settings.ControllerSettingsScreen
import dev.pluto.launcher.ui.settings.HiddenAppsScreen
import dev.pluto.launcher.ui.settings.OnboardingScreen
import dev.pluto.launcher.ui.settings.SettingsScreen
import dev.pluto.launcher.ui.theme.PlutoTheme
import dev.pluto.launcher.ui.theme.WallpaperScrim
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The router owned by MainActivity; settings screens use it for raw-key capture and diagnostics. */
val LocalControllerRouter = staticCompositionLocalOf<ControllerInputRouter?> { null }

/** Layers that cover the whole window; anything beneath them is not composed. */
private val Layer.isFullScreen: Boolean
    get() = when (this) {
        Layer.Drawer, Layer.Edit, Layer.Settings, Layer.HiddenApps, Layer.Categories,
        Layer.ControllerSettings, Layer.Onboarding -> true
        is Layer.FolderLayer, is Layer.AppActions, is Layer.CategoryMembership, is Layer.MoveToFolder -> false
    }

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
    val state by vm.state.collectAsStateWithLifecycle()
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

    CompositionLocalProvider(LocalIconCache provides iconCache) {
        PlutoTheme(state.settings) {
            ProvideControllerFocus(focus) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(focus) {
                            // Any touch takes over from the controller immediately; never consumed.
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) focus.onTouch()
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
                    val widthDp = maxWidth.value.toInt()
                    val heightDp = maxHeight.value.toInt()
                    LaunchedEffect(widthDp, heightDp) { vm.onWindowSizeChanged(widthDp, heightDp) }
                    // Same rule and inputs as the view model, resolved synchronously for this frame.
                    val mode = remember(widthDp, heightDp, state.controllerConnected, state.settings.handheldAppearance) {
                        ModeResolver.resolve(widthDp, heightDp, state.controllerConnected, state.settings.handheldAppearance)
                    }

                    WallpaperScrim(state.settings)
                    if (state.loading) {
                        LoadingPlaceholder()
                    } else {
                        LauncherContent(state, mode, vm, focus)
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
                }
            }
        }
    }

    ActionRouting(state, vm, focus, actions)
}

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

/** Base layout plus the visible part of the layer stack, with focus bookkeeping for layers. */
@Composable
private fun LauncherContent(
    state: LauncherUiState,
    mode: LauncherMode,
    vm: LauncherViewModel,
    focus: ControllerFocusController,
) {
    val layers = state.session.layers
    val scope = rememberCoroutineScope()

    // Record the opener when layers are added; restore focus to it when they close.
    val previousCount = remember { intArrayOf(layers.size) }
    SideEffect {
        val count = layers.size
        val before = previousCount[0]
        if (count > before) {
            repeat(count - before) { i -> focus.pushLayer(if (i == 0) focus.openerId else null) }
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

    // Compose from the topmost full-screen layer upwards; anything beneath it is hidden anyway.
    val fullScreenIndex = layers.indexOfLast { it.isFullScreen }
    val firstComposed = maxOf(fullScreenIndex, 0)
    val showBase = fullScreenIndex < 0

    Box(Modifier.fillMaxSize()) {
        if (showBase) {
            val covered = layers.isNotEmpty()
            CompositionLocalProvider(LocalFocusInert provides covered) {
                Box(Modifier.fillMaxSize().hiddenFromAccessibility(covered)) {
                    when (mode) {
                        LauncherMode.PHONE -> PhoneLayout(state, vm)
                        LauncherMode.LANDSCAPE -> LandscapeLayout(state, vm)
                        LauncherMode.HANDHELD -> HandheldLayout(state, vm)
                    }
                }
            }
        }
        for (index in firstComposed until layers.size) {
            val layer = layers[index]
            val isTop = index == layers.lastIndex
            key(Layer.encode(layer)) {
                CompositionLocalProvider(LocalFocusInert provides !isTop) {
                    Box(Modifier.fillMaxSize().hiddenFromAccessibility(!isTop)) {
                        // The overlay sheets (AppActions, CategoryMembership, MoveToFolder) draw their own
                        // ModalPanel scrim; only the folder view relies on the root's scrim.
                        if (layer is Layer.FolderLayer) DismissScrim(onDismiss = { if (isTop) vm.back() })
                        LayerContent(layer, state, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerContent(layer: Layer, state: LauncherUiState, vm: LauncherViewModel) {
    when (layer) {
        Layer.Drawer -> DrawerScreen(state, vm)
        is Layer.FolderLayer -> FolderOverlay(state, vm, layer.folderId)
        is Layer.AppActions -> AppActionsSheet(layer.key, state, vm)
        is Layer.CategoryMembership -> CategoryMembershipDialog(layer.key, state, vm)
        is Layer.MoveToFolder -> MoveToFolderDialog(layer.key, state, vm)
        Layer.Edit -> EditScreen(state, vm)
        Layer.Settings -> SettingsScreen(state, vm)
        Layer.HiddenApps -> HiddenAppsScreen(state, vm)
        Layer.Categories -> CategoriesScreen(state, vm)
        Layer.ControllerSettings -> ControllerSettingsScreen(state, vm)
        Layer.Onboarding -> OnboardingScreen(state, vm)
    }
}

/** Dims what is beneath a dialog-style layer and closes the layer on an outside tap. */
@Composable
private fun DismissScrim(onDismiss: () -> Unit) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
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
