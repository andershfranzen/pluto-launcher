package dev.pluto.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pluto.launcher.LauncherApplication
import dev.pluto.launcher.R
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
 * Top-level composable. Applies theme + wallpaper scrim, measures the window
 * (calls vm.onWindowSizeChanged), picks Phone / Landscape / Handheld layout from
 * state.mode, renders the layer stack above it, the message snackbar, and routes
 * [LauncherAction]s collected from [actions] through the focus controller and VM.
 */
@Composable
fun LauncherRoot(vm: LauncherViewModel, actions: Flow<LauncherAction>) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { ControllerFocusController() }
    val context = LocalContext.current
    val iconCache = remember(context) { (context.applicationContext as LauncherApplication).container.icons }

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

                    WallpaperScrim(state.settings)
                    if (state.loading) {
                        LoadingPlaceholder()
                    } else {
                        LauncherContent(state, vm, focus)
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

/** Routes controller actions: the focus layer first, then launcher-level shortcuts. */
@Composable
private fun ActionRouting(
    state: LauncherUiState,
    vm: LauncherViewModel,
    focus: ControllerFocusController,
    actions: Flow<LauncherAction>,
) {
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(actions, vm, focus) {
        actions.collect { action ->
            if (focus.handle(action)) return@collect
            val top = currentState.session.topLayer
            when (action) {
                LauncherAction.Back -> vm.back()
                LauncherAction.Search -> {
                    if (top == Layer.Drawer) {
                        vm.setSearchActive(true)
                        focus.requestFocus(DRAWER_SEARCH_ID)
                    } else if (top == null || top is Layer.FolderLayer) {
                        vm.openDrawer(withSearch = true)
                    }
                }
                LauncherAction.Settings -> if (top != Layer.Settings) vm.openLayer(Layer.Settings)
                // Category switching only means something while browsing (home or drawer).
                LauncherAction.PrevCategory -> if (top == null || top == Layer.Drawer) vm.previousCategory()
                LauncherAction.NextCategory -> if (top == null || top == Layer.Drawer) vm.nextCategory()
                else -> Unit
            }
        }
    }
}

/** Base layout plus the visible part of the layer stack, with focus bookkeeping for layers. */
@Composable
private fun LauncherContent(state: LauncherUiState, vm: LauncherViewModel, focus: ControllerFocusController) {
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
                scope.launch { focus.requestFocusWhenReady(id) }
            }
        }
        previousCount[0] = count
    }

    // Compose from the topmost full-screen layer upwards, but never more than the top two.
    val fullScreenIndex = layers.indexOfLast { it.isFullScreen }
    val firstComposed = maxOf(fullScreenIndex, layers.size - 2, 0)
    val showBase = fullScreenIndex < 0 && layers.size <= 2

    Box(Modifier.fillMaxSize()) {
        if (showBase) {
            CompositionLocalProvider(LocalFocusInert provides layers.isNotEmpty()) {
                when (state.mode) {
                    LauncherMode.PHONE -> PhoneLayout(state, vm)
                    LauncherMode.LANDSCAPE -> LandscapeLayout(state, vm)
                    LauncherMode.HANDHELD -> HandheldLayout(state, vm)
                }
            }
        }
        for (index in firstComposed until layers.size) {
            val layer = layers[index]
            val isTop = index == layers.lastIndex
            key(Layer.encode(layer)) {
                CompositionLocalProvider(LocalFocusInert provides !isTop) {
                    if (!layer.isFullScreen) DismissScrim(onDismiss = { if (isTop) vm.back() })
                    LayerContent(layer, state, vm)
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
