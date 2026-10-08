package dev.pluto.launcher.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

sealed interface LaunchResult {
    data object Success : LaunchResult
    /**
     * Human-readable reason, e.g. "Calculator is not available right now."
     * [targetMissing] is true when the activity no longer resolves, so the caller should
     * reconcile persisted references to it (see [AppCatalog.findStale]).
     */
    data class Failure(val message: String, val targetMissing: Boolean = false) : LaunchResult
}

/** One successful catalog query. [generation] increases with every successful load. */
data class CatalogLoad(val generation: Long, val apps: List<AppEntry>)

sealed interface PackageEvent {
    /** Package fully removed (not an update). Persisted references may be forgotten. */
    data class Removed(val packageName: String, val userSerial: Long) : PackageEvent
}

/**
 * App library backed by LauncherApps. Release 0.1 lists the personal profile only
 * (Process.myUserHandle()); other profiles (work, Private Space) are never listed but
 * their presence is reported via [otherProfilesPresent] so the UI can disclose it.
 *
 * Loads on a background dispatcher. Registers a LauncherApps.Callback and reloads
 * the affected package on add/change/remove/available/unavailable/suspended/unsuspended.
 * While that callback stays registered the list is kept current, so [refreshIfStale]
 * (called on every resume) only reloads when something may have been missed.
 */
class AppCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val packageManager: PackageManager = appContext.packageManager
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val myUser: UserHandle = Process.myUserHandle()
    private val mySerial: Long = runCatching { userManager.getSerialNumberForUser(myUser) }.getOrDefault(0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadMutex = Mutex()
    private var pendingReload: Job? = null
    private var started = false
    /** True when the list may be out of date: before the first load, after a failed query or callback registration, or after stop(). */
    @Volatile private var dirty = true
    /** Locale the labels of the last successful load were read in. */
    @Volatile private var loadedLocale: Locale? = null
    private var generation = 0L

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    private val _otherProfilesPresent = MutableStateFlow(false)
    private val _packageEvents = MutableSharedFlow<PackageEvent>(extraBufferCapacity = 16)
    private val _loads = MutableStateFlow<CatalogLoad?>(null)

    /** All launchable activities in the personal profile, unsorted. Null until first load. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()
    val otherProfilesPresent: StateFlow<Boolean> = _otherProfilesPresent.asStateFlow()
    val packageEvents: SharedFlow<PackageEvent> = _packageEvents.asSharedFlow()

    /**
     * The latest *successful* query only (a failed query keeps [apps] unchanged but is never
     * published here), so reconciliation against it cannot mistake a failure for uninstalls.
     */
    val loads: StateFlow<CatalogLoad?> = _loads.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            if (user != myUser) return
            scope.launch { confirmRemoval(packageName) }
            scheduleReload()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = onChanged(user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = onChanged(user)

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            onChanged(user)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            onChanged(user)

        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = onChanged(user)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = onChanged(user)

        private fun onChanged(user: UserHandle) {
            // Other profiles are not listed in 0.1; their changes only matter for the disclosure flag.
            if (user == myUser) scheduleReload() else scope.launch { updateOtherProfiles() }
        }
    }

    fun start() {
        if (started) return
        started = true
        callbackRegistered = try {
            launcherApps.registerCallback(callback, mainHandler)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't register LauncherApps callback", e)
            false
        }
        // Changes made while stopped were not observed.
        dirty = true
        refresh()
    }

    @Volatile private var callbackRegistered = false

    fun stop() {
        if (!started) return
        started = false
        dirty = true
        callbackRegistered = false
        synchronized(this) { pendingReload?.cancel() }
        try {
            launcherApps.unregisterCallback(callback)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't unregister LauncherApps callback", e)
        }
    }

    /** Full reload now (e.g. after a launch failure). */
    fun refresh(): Unit = synchronized(this) {
        dirty = true
        pendingReload?.cancel()
        pendingReload = scope.launch { reload() }
    }

    /**
     * Called on every resume. A full reload reads every app's label (package resources), so it
     * only runs when the list may be stale: no live callback, a failed/missing load, or a
     * locale change since the labels were read. Otherwise the callback already kept it current.
     */
    fun refreshIfStale(locale: Locale = Locale.getDefault()) {
        if (dirty || !callbackRegistered || loadedLocale != locale || _apps.value == null) refresh()
    }

    /** Coalesces bursts of package callbacks (installs often send several) into one reload. */
    private fun scheduleReload(): Unit = synchronized(this) {
        pendingReload?.cancel()
        pendingReload = scope.launch {
            delay(RELOAD_DEBOUNCE_MS)
            reload()
        }
    }

    private suspend fun reload() = loadMutex.withLock {
        val locale = Locale.getDefault()
        val entries = try {
            launcherApps.getActivityList(null, myUser).map { it.toEntry() }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't query launchable activities", e)
            null
        }
        if (entries != null) {
            _apps.value = entries
            generation += 1
            _loads.value = CatalogLoad(generation, entries)
            loadedLocale = locale
            dirty = false
        } else {
            // Keep what we had rather than blanking the launcher; still mark loaded. Stays dirty
            // so the next resume retries, and nothing is reconciled against this.
            _apps.value = _apps.value ?: emptyList()
            dirty = true
        }
        updateOtherProfiles()
    }

    private fun updateOtherProfiles() {
        _otherProfilesPresent.value = try {
            launcherApps.profiles.any { it != myUser }
        } catch (e: RuntimeException) {
            runCatching { userManager.userProfiles.any { it != myUser } }.getOrDefault(false)
        }
    }

    /** Emits [PackageEvent.Removed] only when the package is really gone (updates send remove + add). */
    private suspend fun confirmRemoval(packageName: String) {
        delay(RELOAD_DEBOUNCE_MS)
        if (packageState(packageName) == PackageState.UNINSTALLED) {
            _packageEvents.emit(PackageEvent.Removed(packageName, mySerial))
        }
    }

    private enum class PackageState {
        /** Has launcher activities right now (they are listed in [PackageInfoResult.components]). */
        LAUNCHABLE,
        /** Installed, but nothing launchable now: disabled, unavailable (SD card), mid-update... Keep references. */
        INSTALLED_NOT_LAUNCHABLE,
        /** Definitely not installed for this user. */
        UNINSTALLED,
        /** The system could not be asked. Keep references. */
        UNKNOWN,
    }

    private fun packageState(packageName: String): PackageState = packageInfo(packageName).state

    private class PackageInfoResult(val state: PackageState, val components: Set<ComponentName> = emptySet())

    private fun packageInfo(packageName: String): PackageInfoResult {
        val activities = try {
            launcherApps.getActivityList(packageName, myUser)
        } catch (e: RuntimeException) {
            return PackageInfoResult(PackageState.UNKNOWN)
        }
        if (activities.isNotEmpty()) {
            return PackageInfoResult(PackageState.LAUNCHABLE, activities.mapTo(HashSet()) { it.componentName })
        }
        return try {
            // Without MATCH_UNINSTALLED_PACKAGES: a package uninstalled with "keep data" counts as gone.
            // A disabled or suspended package still resolves here and is kept.
            packageManager.getPackageInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            PackageInfoResult(PackageState.INSTALLED_NOT_LAUNCHABLE)
        } catch (e: PackageManager.NameNotFoundException) {
            PackageInfoResult(PackageState.UNINSTALLED)
        } catch (e: RuntimeException) {
            PackageInfoResult(PackageState.UNKNOWN)
        }
    }

    /**
     * Which of [keys] (missing from the catalog) are definitely stale and may be forgotten:
     * the package is installed and launchable but that activity no longer exists (an update
     * removed or renamed it), or the package is uninstalled for this user. Keys from other
     * profiles, packages that are disabled, suspended, unavailable or mid-update, and anything
     * the system could not answer are kept. Blocking; call off the main thread.
     */
    fun findStale(keys: Collection<AppKey>): Set<AppKey> {
        val personal = keys.filter { it.userSerial == mySerial }
        if (personal.isEmpty()) return emptySet()
        val stale = HashSet<AppKey>()
        for ((pkg, pkgKeys) in personal.groupBy { it.packageName }) {
            val info = packageInfo(pkg)
            when (info.state) {
                PackageState.LAUNCHABLE -> pkgKeys.filterTo(stale) { key ->
                    val component = ComponentName.unflattenFromString(key.component)
                    component == null || component !in info.components
                }
                PackageState.UNINSTALLED -> stale.addAll(pkgKeys)
                PackageState.INSTALLED_NOT_LAUNCHABLE, PackageState.UNKNOWN -> Unit
            }
        }
        return stale
    }

    private fun LauncherActivityInfo.toEntry(): AppEntry {
        val label = runCatching { label?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: componentName.packageName
        val suspended = (applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        return AppEntry(
            key = AppKey(componentName.flattenToString(), mySerial),
            label = label,
            isEnabled = !suspended && applicationInfo.enabled,
        )
    }

    /** Resolves [key] to a current personal-profile activity, or null if it's gone or belongs to another profile. */
    private fun resolve(key: AppKey): LauncherActivityInfo? {
        if (key.userSerial != mySerial) return null
        val component = ComponentName.unflattenFromString(key.component) ?: return null
        return try {
            launcherApps.getActivityList(component.packageName, myUser)
                .firstOrNull { it.componentName == component }
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun labelFor(key: AppKey): String =
        _apps.value?.firstOrNull { it.key == key }?.label ?: key.packageName

    /** Launches via LauncherApps.startMainActivity. Never throws; on failure triggers a refresh of that package. */
    fun launch(key: AppKey, sourceBounds: Rect? = null): LaunchResult {
        val label = labelFor(key)
        val info = resolve(key)
        if (info == null) {
            refresh()
            return LaunchResult.Failure("$label is no longer installed.", targetMissing = true)
        }
        if ((info.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0) {
            return LaunchResult.Failure("$label is paused right now.")
        }
        return try {
            launcherApps.startMainActivity(info.componentName, myUser, sourceBounds, null)
            LaunchResult.Success
        } catch (e: Exception) {
            // ActivityNotFoundException, SecurityException, IllegalStateException, ...
            Log.w(TAG, "Launch failed for $key", e)
            refresh()
            LaunchResult.Failure("$label couldn't be opened.")
        }
    }

    /** Opens the Android system App info screen. */
    fun openAppInfo(key: AppKey): LaunchResult {
        val component = ComponentName.unflattenFromString(key.component)
        if (component == null || key.userSerial != mySerial) {
            return LaunchResult.Failure("App info isn't available for ${labelFor(key)}.")
        }
        return try {
            launcherApps.startAppDetailsActivity(component, myUser, null, null)
            LaunchResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "App info failed for $key", e)
            LaunchResult.Failure("App info for ${labelFor(key)} couldn't be opened.")
        }
    }

    /** Starts system uninstall confirmation (ACTION_DELETE). */
    fun requestUninstall(key: AppKey): LaunchResult {
        if (key.userSerial != mySerial) {
            return LaunchResult.Failure("${labelFor(key)} can't be uninstalled from here.")
        }
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            LaunchResult.Success
        } catch (e: ActivityNotFoundException) {
            LaunchResult.Failure("Uninstall isn't available on this device.")
        } catch (e: Exception) {
            Log.w(TAG, "Uninstall request failed for $key", e)
            LaunchResult.Failure("${labelFor(key)} couldn't be uninstalled.")
        }
    }

    private companion object {
        const val TAG = "AppCatalog"
        const val RELOAD_DEBOUNCE_MS = 150L
    }
}
