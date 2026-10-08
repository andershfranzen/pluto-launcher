package dev.pluto.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.pluto.launcher.model.AppKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * App icons, rasterised at the size they are shown and kept in a memory-sized LruCache.
 *
 * - All Drawable work (getBadgedIcon, rasterising) runs on a bounded pool of background
 *   threads ([DECODE_PARALLELISM]), never on the main thread; concurrent requests for the
 *   same icon share one decode.
 * - The activity info comes from the catalog's last load ([LauncherActivityInfos]), so a
 *   decode does not cost a binder query per icon.
 * - Decoded bitmaps are handed to the RenderThread for upload right away (prepareToDraw),
 *   so the first frame that shows them does not pay for the texture upload.
 * - [peek] is a synchronous, allocation-light memory lookup for composition: an icon that
 *   is cached never shows a placeholder. [prefetch] fills the cache ahead of need.
 *
 * Invalidate a package when it changes; [versions] / [versionOf] then tell composed icons
 * of that package to load again (their AppKey is unchanged by an update).
 */
class IconCache(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val myUser = Process.myUserHandle()
    private val mySerial: Long = runCatching {
        appContext.getSystemService(UserManager::class.java).getSerialNumberForUser(myUser)
    }.getOrDefault(0L)

    /** Sized in bytes: roughly 1/16 of the heap the VM may grow to. LruCache is internally synchronised. */
    private val cache = object : LruCache<IconKey, ImageBitmap>(cacheBytes()) {
        override fun sizeOf(key: IconKey, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** Decodes in flight, shared by everyone asking for the same icon. */
    private val inFlight = ConcurrentHashMap<IconKey, Deferred<ImageBitmap?>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(DECODE_PARALLELISM)
    private val decodeScope = CoroutineScope(SupervisorJob() + decodeDispatcher)

    private val _versions = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Snapshot mirror of [_versions] (main thread writes): reading [versionOf] in composition observes it. */
    private val versionState = mutableStateMapOf<String, Int>()

    /**
     * Per-package invalidation counter. Icons key their loaded bitmap on it, so an update
     * (new icon) or a decode that failed mid-install is reloaded after the package event.
     */
    val versions: StateFlow<Map<String, Int>> = _versions.asStateFlow()

    /**
     * Current invalidation count for [packageName] (0 until it is first invalidated). Snapshot
     * state: a composable that reads it recomposes when that package is invalidated, without
     * a per-icon flow collector.
     */
    fun versionOf(packageName: String): Int = versionState[packageName] ?: 0

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

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            launcherApps.registerCallback(invalidator, mainHandler)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't register icon invalidation callback", e)
        }
    }

    /** Returns the cached bitmap or null without decoding. Cheap; safe (and meant) for composition. */
    fun peek(key: AppKey, sizePx: Int): ImageBitmap? = cache.get(IconKey(key, sizePx))

    /** Suspends while decoding on the background pool. Returns null if the activity is gone. */
    suspend fun load(key: AppKey, sizePx: Int): ImageBitmap? {
        if (sizePx <= 0) return null
        val iconKey = IconKey(key, sizePx)
        cache.get(iconKey)?.let { return it }
        return decodeShared(iconKey).await()
    }

    /**
     * Decodes every icon of [keys] at every size of [sizes] that is not cached yet, in order
     * (callers put what is on screen first), on the bounded background pool. Suspends until
     * done; cancelling stops the remaining work.
     */
    suspend fun prefetch(keys: List<AppKey>, sizes: List<Int>) {
        if (keys.isEmpty() || sizes.isEmpty()) return
        coroutineScope {
            // A window of at most DECODE_PARALLELISM * 2 requests in flight keeps the pool busy
            // without queueing the whole catalog at once (cancellation stays prompt).
            val window = Semaphore(DECODE_PARALLELISM * 2)
            for (size in sizes) {
                if (size <= 0) continue
                for (key in keys) {
                    val iconKey = IconKey(key, size)
                    if (cache.get(iconKey) != null) continue
                    window.acquire()
                    launch {
                        try {
                            decodeShared(iconKey).await()
                        } finally {
                            window.release()
                        }
                    }
                }
            }
        }
    }

    fun invalidatePackage(packageName: String) {
        LauncherActivityInfos.forget(packageName)
        cache.snapshot().keys
            .filter { it.app.packageName == packageName }
            .forEach { cache.remove(it) }
        _versions.update { it + (packageName to ((it[packageName] ?: 0) + 1)) }
        val bump = { versionState[packageName] = (versionState[packageName] ?: 0) + 1 }
        if (Looper.myLooper() == Looper.getMainLooper()) bump() else mainHandler.post(bump)
    }

    private fun decodeShared(iconKey: IconKey): Deferred<ImageBitmap?> {
        inFlight[iconKey]?.let { return it }
        val placeholder = CompletableDeferred<ImageBitmap?>()
        val existing = inFlight.putIfAbsent(iconKey, placeholder)
        if (existing != null) return existing
        decodeScope.launch {
            val result = try {
                cache.get(iconKey) ?: decode(iconKey.app, iconKey.sizePx)?.also { cache.put(iconKey, it) }
            } catch (e: Throwable) {
                Log.w(TAG, "Icon decode failed for ${iconKey.app}", e)
                null
            } finally {
                inFlight.remove(iconKey, placeholder)
            }
            placeholder.complete(result)
        }
        return placeholder
    }

    private fun decode(key: AppKey, sizePx: Int): ImageBitmap? {
        // Only the personal profile is listed in 0.1; never decode other profiles' icons.
        if (key.userSerial != mySerial) return null
        return try {
            val info = LauncherActivityInfos.get(key) ?: query(key) ?: return null
            val bitmap = info.getBadgedIcon(0).toBitmap(sizePx, sizePx)
            // Start the GPU upload on the RenderThread now rather than in the first frame that draws it.
            bitmap.prepareToDraw()
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            // Package vanished mid-decode, or a malformed drawable: show the placeholder instead.
            Log.w(TAG, "Icon decode failed for $key", e)
            null
        }
    }

    /** Fallback when the catalog has not (yet) seen the activity: one binder query. */
    private fun query(key: AppKey): LauncherActivityInfo? {
        val component = ComponentName.unflattenFromString(key.component) ?: return null
        return launcherApps.getActivityList(component.packageName, myUser).firstOrNull { it.componentName == component }
    }

    /** Cache key: an app at a pixel size (no string building per lookup). */
    private data class IconKey(val app: AppKey, val sizePx: Int)

    private companion object {
        const val TAG = "IconCache"

        /** Background decodes at once: enough to fill a screen quickly, few enough not to starve the UI. */
        const val DECODE_PARALLELISM = 3

        fun cacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 16).coerceIn(4L * 1024 * 1024, Int.MAX_VALUE.toLong()).toInt()
    }
}

/**
 * The LauncherActivityInfo of every activity in the catalog's latest load, so icon decoding
 * needs no binder query per icon. Filled by [AppCatalog]; read by [IconCache].
 */
internal object LauncherActivityInfos {
    @Volatile private var byKey: Map<AppKey, LauncherActivityInfo> = emptyMap()

    fun replace(infos: Map<AppKey, LauncherActivityInfo>) {
        byKey = infos
    }

    fun get(key: AppKey): LauncherActivityInfo? = byKey[key]

    /** A package changed: its infos may be stale until the next catalog load. */
    fun forget(packageName: String) {
        val current = byKey
        if (current.keys.any { it.packageName == packageName }) byKey = current.filterKeys { it.packageName != packageName }
    }
}
