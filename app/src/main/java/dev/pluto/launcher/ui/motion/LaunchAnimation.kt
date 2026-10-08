package dev.pluto.launcher.ui.motion

import android.os.Bundle
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * What an app launch needs to open out of an on-screen control: the control's bounds in
 * screen coordinates (LauncherApps sourceBounds, which the app may read) and the
 * ActivityOptions bundle carrying the window opening animation.
 */
class LaunchSource(val screenBounds: android.graphics.Rect, val options: Bundle?)

/**
 * Turns a control's window-space bounds into a [LaunchSource]. Implemented by the activity
 * (it owns the window and its decor view) and handed to Compose through
 * [LocalLaunchSourceFactory], so neither composables nor the ViewModel hold a View.
 */
fun interface LaunchSourceFactory {
    /** [boundsInWindow] in px, window coordinates. [animate] false: bounds only, default window animation. */
    fun create(boundsInWindow: Rect, animate: Boolean): LaunchSource?
}

/** Null (previews, tests): apps launch without a source rectangle or custom animation. */
val LocalLaunchSourceFactory = staticCompositionLocalOf<LaunchSourceFactory?> { null }
