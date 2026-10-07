package dev.pluto.launcher.system

import android.app.Activity
import android.content.Context
import android.content.Intent

/** Default Home role helpers using RoleManager (API 29+). */
object HomeRole {
    fun isDefaultHome(context: Context): Boolean = TODO()

    /** Intent for the system's request-role flow; null if the role isn't available. */
    fun requestIntent(context: Context): Intent? = TODO()

    /** Opens Settings > Default apps so the user can switch back to another launcher. */
    fun openDefaultAppsSettings(activity: Activity): Unit = TODO()
}
