package dev.pluto.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.pluto.launcher.input.ControllerInputRouter

/** The router owned by MainActivity; settings screens use it for raw-key capture and diagnostics. */
val LocalControllerRouter = staticCompositionLocalOf<ControllerInputRouter?> { null }

/**
 * Top-level composable. Applies theme + wallpaper scrim, measures the window
 * (calls vm.onWindowSizeChanged), picks Phone / Landscape / Handheld layout from
 * state.mode, renders the layer stack above it, the message snackbar, and routes
 * [LauncherAction]s collected from [actions] through the focus controller and VM.
 */
@Composable
fun LauncherRoot(vm: LauncherViewModel, actions: kotlinx.coroutines.flow.Flow<dev.pluto.launcher.input.LauncherAction>): Unit = TODO()
