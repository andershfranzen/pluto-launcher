package dev.pluto.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.ceil

/**
 * Pluto's app widget host: the process-wide [AppWidgetHost] (listening while the launcher is
 * started), provider lookup and id bookkeeping. Binding and configuration need an activity
 * and live in MainActivity ([WidgetBinder]).
 */
class WidgetHost(context: Context) {
    private val app = context.applicationContext
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    private val host = AppWidgetHost(app, HOST_ID)

    fun startListening() = guard("start listening") { host.startListening() }
    fun stopListening() = guard("stop listening") { host.stopListening() }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /** Widgets the personal profile offers, by app then name. Binder work: call off the main thread. */
    fun providers(): List<AppWidgetProviderInfo> =
        runCatching { manager.getInstalledProvidersForProfile(Process.myUserHandle()) }.getOrDefault(emptyList())

    fun allocate(): Int = host.allocateAppWidgetId()

    fun delete(appWidgetId: Int) = guard("delete widget id") { host.deleteAppWidgetId(appWidgetId) }

    /** True when the system already lets Pluto bind this provider (otherwise ask the user). */
    fun bindIfAllowed(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null) }.getOrDefault(false)

    fun createView(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(context, appWidgetId, info)

    /** Starts the provider's configuration screen; the result comes back to [activity]'s onActivityResult. */
    fun startConfigure(activity: android.app.Activity, appWidgetId: Int, requestCode: Int) =
        host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't $what", e)
        }
    }

    companion object {
        private const val TAG = "WidgetHost"
        private const val HOST_ID = 0x504C

        /** Rows a new widget takes: its target height where it gives one, else its minimum height. */
        fun defaultRows(info: AppWidgetProviderInfo, density: Float, rowHeightDp: Float): Int {
            val target = if (android.os.Build.VERSION.SDK_INT >= 31) info.targetCellHeight else 0
            val rows = if (target > 0) target else ceil(info.minHeight / density / rowHeightDp).toInt()
            return rows.coerceIn(dev.pluto.launcher.model.HomeWidget.MIN_ROWS, dev.pluto.launcher.model.HomeWidget.MAX_ROWS)
        }
    }
}

/** Binds a chosen provider (asking the user when needed), runs its configuration, then adds it to Home. */
fun interface WidgetBinder {
    fun add(info: AppWidgetProviderInfo)
}

val LocalWidgetHost = staticCompositionLocalOf<WidgetHost?> { null }
val LocalWidgetBinder = staticCompositionLocalOf<WidgetBinder?> { null }

/** Approximate height of one home grid row (icon plus label), used to size new widgets. */
const val HOME_ROW_HEIGHT_DP = 96f
