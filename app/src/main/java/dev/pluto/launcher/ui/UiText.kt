package dev.pluto.launcher.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Translated text for code that runs outside composition: the `SettingsText`/`OverlayText`
 * style text objects, view-model messages and system services. Composables use
 * `stringResource` instead.
 *
 * MainActivity handles locale changes itself (no recreation), so it re-attaches its own
 * resources on every configuration change and the composition root is keyed on the locale:
 * text read here is then read again in the new language.
 */
object UiText {
    @Volatile private var resources: Resources? = null

    /** Called by the Application, then by the activity (whose resources follow per-app languages). */
    fun attach(context: Context) {
        resources = context.resources
    }

    fun get(@StringRes id: Int): String = requireResources().getString(id)

    fun get(@StringRes id: Int, vararg args: Any): String = requireResources().getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        requireResources().getQuantityString(id, count, *args)

    private fun requireResources(): Resources =
        resources ?: error("UiText used before LauncherApplication.onCreate attached resources")
}
