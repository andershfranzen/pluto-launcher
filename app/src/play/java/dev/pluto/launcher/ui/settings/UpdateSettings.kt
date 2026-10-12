package dev.pluto.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.PlutoButton

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun UpdateSettings(settings: LauncherSettings, vm: LauncherViewModel) {
    val context = LocalContext.current
    SettingsSection("Updates") {
        BodyText("Google Play keeps Pluto up to date.")
        PlutoButton("settings:updates:play", "Open in Play Store", {
            openLink(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
        })
    }
}

// The shared onboarding source calls this only for GitHub distributions.
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun UpdateCheckConsent(vm: LauncherViewModel) = Unit
