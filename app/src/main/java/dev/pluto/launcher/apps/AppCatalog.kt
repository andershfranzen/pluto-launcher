package dev.pluto.launcher.apps

import android.content.Context
import android.graphics.Rect
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface LaunchResult {
    data object Success : LaunchResult
    /** Human-readable reason, e.g. "Calculator is not available right now." */
    data class Failure(val message: String) : LaunchResult
}

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
 */
class AppCatalog(context: Context) {
    /** All launchable activities in the personal profile, unsorted. Null until first load. */
    val apps: StateFlow<List<AppEntry>?> = TODO()
    val otherProfilesPresent: StateFlow<Boolean> = TODO()
    val packageEvents: SharedFlow<PackageEvent> = TODO()

    fun start(): Unit = TODO()
    fun stop(): Unit = TODO()
    /** Full reload (e.g. on resume, to catch anything missed while suspended). */
    fun refresh(): Unit = TODO()

    /** Launches via LauncherApps.startMainActivity. Never throws; on failure triggers a refresh of that package. */
    fun launch(key: AppKey, sourceBounds: Rect? = null): LaunchResult = TODO()

    /** Opens the Android system App info screen. */
    fun openAppInfo(key: AppKey): LaunchResult = TODO()

    /** Starts system uninstall confirmation (ACTION_DELETE). */
    fun requestUninstall(key: AppKey): LaunchResult = TODO()
}
