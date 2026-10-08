package dev.pluto.launcher.ui.motion

import androidx.compose.runtime.staticCompositionLocalOf
import dev.pluto.launcher.model.AppKey

/**
 * Launches an app with an opening animation that grows out of the control that was
 * activated. [originId] is that control's focus id (as reported to [OriginRegistry] by
 * AppTile); null launches without a source rectangle. UI code launches apps only through
 * this, never vm.launch directly.
 */
fun interface AppLauncher {
    fun launch(key: AppKey, originId: String?)
}

val LocalAppLauncher = staticCompositionLocalOf<AppLauncher> { error("AppLauncher not provided") }
