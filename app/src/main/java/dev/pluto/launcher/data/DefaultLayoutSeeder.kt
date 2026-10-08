package dev.pluto.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import dev.pluto.launcher.domain.DefaultLayout
import dev.pluto.launcher.model.AppEntry

/**
 * Plans the first-run layout from the device's own default apps, never from hard-coded
 * package names. Each slot asks Android which activity handles a role-like intent
 * (MATCH_DEFAULT_ONLY, so the user's chosen default wins); a slot is skipped when only the
 * system chooser/resolver answers or the handler has no launchable entry in the catalog.
 *
 * Dock: phone, messaging, browser, camera. Favourites: settings, calendar, clock, gallery, files.
 */
class DefaultLayoutSeeder(context: Context) {
    private val packageManager: PackageManager = context.applicationContext.packageManager

    /** Blocking (PackageManager queries); call off the main thread. [catalog] is the personal-profile catalog. */
    fun plan(catalog: List<AppEntry>): DefaultLayout.Plan =
        DefaultLayout.choose(
            dockSlots = DOCK_SLOTS.map { intents -> intents.mapNotNull(::handlerFor) },
            homeSlots = HOME_SLOTS.map { intents -> intents.mapNotNull(::handlerFor) },
            catalog = catalog,
        )

    private fun handlerFor(intent: Intent): DefaultLayout.Handler? {
        val info = try {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't resolve $intent", e)
            null
        } ?: return null
        if (isChooser(info.packageName, info.name)) return null
        return DefaultLayout.Handler(info.packageName, ComponentName(info.packageName, info.name).flattenToString())
    }

    /** The system resolver/chooser answers when no default is set and several apps qualify. */
    private fun isChooser(packageName: String, className: String): Boolean =
        packageName == "android" ||
            packageName == "com.android.intentresolver" ||
            className.endsWith("ResolverActivity") ||
            className.endsWith("ChooserActivity")

    private companion object {
        const val TAG = "DefaultLayoutSeeder"

        fun category(category: String): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

        val DOCK_SLOTS: List<List<Intent>> = listOf(
            listOf(Intent(Intent.ACTION_DIAL)),
            listOf(category(Intent.CATEGORY_APP_MESSAGING), Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            listOf(category(Intent.CATEGORY_APP_BROWSER), Intent(Intent.ACTION_VIEW, Uri.parse("https://"))),
            listOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        )

        val HOME_SLOTS: List<List<Intent>> = listOf(
            listOf(Intent(Settings.ACTION_SETTINGS)),
            listOf(category(Intent.CATEGORY_APP_CALENDAR)),
            listOf(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            listOf(category(Intent.CATEGORY_APP_GALLERY)),
            listOf(category(Intent.CATEGORY_APP_FILES)),
        )
    }
}
