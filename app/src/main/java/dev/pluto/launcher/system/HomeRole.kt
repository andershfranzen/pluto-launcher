package dev.pluto.launcher.system

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/** Default Home role helpers using RoleManager (API 29+). */
object HomeRole {
    private const val TAG = "HomeRole"

    fun isDefaultHome(context: Context): Boolean = try {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't query the Home role", e)
        false
    }

    /** Intent for the system's request-role flow; null if the role isn't available. */
    fun requestIntent(context: Context): Intent? = try {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            null
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't create the Home role request", e)
        null
    }

    /** Opens Settings > Default apps so the user can switch back to another launcher. */
    fun openDefaultAppsSettings(activity: Activity) {
        val candidates = listOf(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_HOME_SETTINGS,
            Settings.ACTION_SETTINGS,
        )
        for (action in candidates) {
            try {
                activity.startActivity(Intent(action))
                return
            } catch (e: ActivityNotFoundException) {
                // Some ROMs omit a screen; fall through to the next, more general one.
            } catch (e: SecurityException) {
                Log.w(TAG, "Not allowed to open $action", e)
            }
        }
    }
}
