package dev.pluto.launcher.ui.theme

import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import dev.pluto.launcher.data.prefs.BackgroundChoice
import dev.pluto.launcher.data.prefs.LauncherSettings

/**
 * Glass fill for the portrait dock, tinted with the colour behind it: the wallpaper's
 * primary colour (following wallpaper changes) or the XMB colour. Lightened slightly and
 * translucent so icons stay the subject. Null keeps the neutral glass: over the black Pluto
 * sky, and whenever the wallpaper's colour is unknown.
 */
@Composable
fun rememberDockFill(settings: LauncherSettings): Brush? {
    val wallpaper = if (settings.normalBackground == BackgroundChoice.WALLPAPER) rememberWallpaperPrimary() else null
    val base = when (settings.normalBackground) {
        BackgroundChoice.WALLPAPER -> wallpaper
        BackgroundChoice.XMB -> settings.xmbBaseColor()
        BackgroundChoice.PLUTO -> null
    } ?: return null
    return remember(base) {
        // Visible as colour, not as a grey sheen: a fair share of the hue, lifted a little
        // so dark wallpapers still read as a surface.
        val tint = lerp(base, Color.White, 0.2f)
        Brush.verticalGradient(listOf(tint.copy(alpha = 0.38f), tint.copy(alpha = 0.24f)))
    }
}

/** The system wallpaper's primary colour, updated when it changes; null when unknown. */
@Composable
private fun rememberWallpaperPrimary(): Color? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(WallpaperManager::class.java) }
    var color by remember { mutableStateOf(primaryOf(manager)) }
    DisposableEffect(manager) {
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) color = colors?.primaryColor?.toArgb()?.let(::Color)
        }
        runCatching { manager?.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { manager?.removeOnColorsChangedListener(listener) } }
    }
    return color
}

private fun primaryOf(manager: WallpaperManager?): Color? =
    runCatching { manager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()?.let(::Color) }.getOrNull()
