package dev.pluto.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.pluto.launcher.model.AppKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Decodes app icons off the main thread (Dispatchers.IO / Default) via
 * LauncherActivityInfo.getIcon / getBadgedIcon, rasterised at [sizePx], kept in an LruCache
 * sized by memory. Invalidate a package when it changes; [versions] then tells composed
 * icons of that package to load again (their AppKey is unchanged by an update).
 */
class IconCache(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val myUser = Process.myUserHandle()
    private val mySerial: Long = runCatching {
        appContext.getSystemService(UserManager::class.java).getSerialNumberForUser(myUser)
    }.getOrDefault(0L)

    /** Sized in bytes: roughly 1/16 of the heap the VM may grow to. LruCache is internally synchronised. */
    private val cache = object : LruCache<String, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val _versions = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * Per-package invalidation counter. Icons key their loaded bitmap on it, so an update
     * (new icon) or a decode that failed mid-install is reloaded after the package event.
     */
    val versions: StateFlow<Map<String, Int>> = _versions.asStateFlow()

    /** Current invalidation count for [packageName] (0 until it is first invalidated). */
    fun versionOf(packageName: String): Int = _versions.value[packageName] ?: 0

    /**
     * Icons change with app updates, so the cache drops a package's bitmaps itself
     * whenever LauncherApps reports a change. Lives as long as the process (application context).
     */
    private val invalidator = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = invalidatePackage(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = invalidatePackage(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = invalidatePackage(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::invalidatePackage)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            packageNames.forEach(::invalidatePackage)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach(::invalidatePackage)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            packageNames.forEach(::invalidatePackage)
    }

    init {
        try {
            launcherApps.registerCallback(invalidator, Handler(Looper.getMainLooper()))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't register icon invalidation callback", e)
        }
    }

    /** Returns cached bitmap or null without decoding. Safe on main thread. */
    fun peek(key: AppKey, sizePx: Int): ImageBitmap? = cache.get(cacheKey(key, sizePx))

    /** Suspends while decoding on a background dispatcher. Returns null if the activity is gone. */
    suspend fun load(key: AppKey, sizePx: Int): ImageBitmap? {
        if (sizePx <= 0) return null
        val cacheKey = cacheKey(key, sizePx)
        cache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            decode(key, sizePx)?.also { cache.put(cacheKey, it) }
        }
    }

    fun invalidatePackage(packageName: String) {
        val prefix = "$packageName/"
        cache.snapshot().keys
            .filter { it.substringAfterLast('|').startsWith(prefix) }
            .forEach { cache.remove(it) }
        _versions.update { it + (packageName to ((it[packageName] ?: 0) + 1)) }
    }

    private fun decode(key: AppKey, sizePx: Int): ImageBitmap? {
        // Only the personal profile is listed in 0.1; never decode other profiles' icons.
        if (key.userSerial != mySerial) return null
        val component = ComponentName.unflattenFromString(key.component) ?: return null
        return try {
            val info = launcherApps.getActivityList(component.packageName, myUser)
                .firstOrNull { it.componentName == component } ?: return null
            info.getBadgedIcon(0).toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (e: Exception) {
            // Package vanished mid-decode, or a malformed drawable: show the placeholder instead.
            Log.w(TAG, "Icon decode failed for $key", e)
            null
        }
    }

    private fun cacheKey(key: AppKey, sizePx: Int) = "$sizePx|${key.encode()}"

    private companion object {
        const val TAG = "IconCache"

        fun cacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 16).coerceIn(4L * 1024 * 1024, Int.MAX_VALUE.toLong()).toInt()
    }
}
