package dev.pluto.launcher.system

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

/**
 * Pulls down the notification shade, as stock launchers do on a swipe down over Home.
 * StatusBarManager.expandNotificationsPanel is hidden API (greylisted, used by launchers for
 * years) and needs the normal EXPAND_STATUS_BAR permission; failure is logged and ignored.
 */
object NotificationShade {
    private const val TAG = "NotificationShade"

    @SuppressLint("WrongConstant")
    fun expand(context: Context) {
        try {
            val manager = context.getSystemService("statusbar") ?: return
            manager.javaClass.getMethod("expandNotificationsPanel").invoke(manager)
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Couldn't expand the notification shade", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't expand the notification shade", e)
        }
    }
}
