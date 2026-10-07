package dev.pluto.launcher.apps

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import dev.pluto.launcher.model.AppKey

/**
 * Decodes app icons off the main thread (Dispatchers.IO / Default) via
 * LauncherActivityInfo.getIcon / getBadgedIcon, rasterised at [sizePx], kept in an LruCache
 * sized by memory. Invalidate a package when it changes.
 */
class IconCache(context: Context) {
    /** Returns cached bitmap or null without decoding. Safe on main thread. */
    fun peek(key: AppKey, sizePx: Int): ImageBitmap? = TODO()

    /** Suspends while decoding on a background dispatcher. Returns null if the activity is gone. */
    suspend fun load(key: AppKey, sizePx: Int): ImageBitmap? = TODO()

    fun invalidatePackage(packageName: String): Unit = TODO()
}
