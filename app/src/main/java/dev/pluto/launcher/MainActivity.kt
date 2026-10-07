package dev.pluto.launcher

import androidx.activity.ComponentActivity

/**
 * The Home activity.
 *
 * - Edge-to-edge, transparent over the system wallpaper (theme windowShowWallpaper).
 * - Creates the LauncherViewModel (factory from LauncherApplication.container) and a
 *   ControllerInputRouter whose actions feed a flow passed to LauncherRoot.
 * - dispatchKeyEvent / dispatchGenericMotionEvent: give gamepad events to the router first.
 *   Touch events (dispatchTouchEvent) notify the focus layer that touch took over.
 * - onNewIntent with ACTION_MAIN + CATEGORY_HOME while already resumed -> vm.onHomePressed().
 * - Back (OnBackPressedDispatcher) -> vm.back(); at home Back does nothing (launcher stays).
 * - Applies settings.rotation to requestedOrientation: FOLLOW_SYSTEM -> SCREEN_ORIENTATION_USER,
 *   PORTRAIT -> USER_PORTRAIT, LANDSCAPE -> USER_LANDSCAPE (both directions),
 *   LANDSCAPE_WHEN_CONTROLLER -> USER_LANDSCAPE while a controller is connected, else USER.
 *   Only affects the launcher window, never the foreground app.
 * - onResume: catalog.refresh(), controllers.refresh(), vm.onResume(HomeRole.isDefaultHome()).
 * - onPause / onWindowFocusChanged(false): router.reset().
 */
class MainActivity : ComponentActivity()
