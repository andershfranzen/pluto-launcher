package dev.pluto.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.pluto.launcher.R
import dev.pluto.launcher.system.NotificationDots
import dev.pluto.launcher.system.ScreenLockService
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.ConfirmDialog
import dev.pluto.launcher.ui.overlay.LayerScaffold
import dev.pluto.launcher.ui.overlay.PlutoButton
import dev.pluto.launcher.ui.overlay.SectionHeader
import dev.pluto.launcher.ui.overlay.rememberScreenFocus

internal const val PRIVACY_URL = "https://andershfranzen.github.io/pluto-launcher/privacy/"

/** Separate, affirmative consent for each optional Android access; dismissal grants nothing. */
@Composable
internal fun OptionalPermissionDisclosure(layer: Layer, vm: LauncherViewModel) {
    val context = LocalContext.current
    val screenLock = layer == Layer.ScreenLockPermission
    ConfirmDialog(
        idPrefix = if (screenLock) "access:lock" else "access:notifications",
        title = stringResource(if (screenLock) R.string.settings_access_lock_title else R.string.settings_access_notifications_title),
        text = stringResource(if (screenLock) R.string.settings_access_lock_text else R.string.settings_access_notifications_text),
        confirmLabel = stringResource(R.string.settings_access_agree),
        onConfirm = {
            vm.updateSettings { if (screenLock) it.copy(doubleTapToLock = true) else it.copy(notificationDots = true) }
            vm.back()
            if (screenLock) {
                if (!ScreenLockService.isReady) ScreenLockService.openSettings(context)
            } else NotificationDots.openSettings(context)
        },
        onDismiss = vm::back,
    )
}

/** Available offline; the public policy covers both Play and GitHub distributions. */
@Composable
internal fun PrivacyPolicyScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    rememberScreenFocus("privacy:close")
    LayerScaffold(title = stringResource(R.string.settings_privacy_policy), idPrefix = "privacy", onClose = vm::back, fullScreen = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyText(stringResource(R.string.settings_privacy_byline))
            SectionHeader(stringResource(R.string.settings_privacy_device_title))
            BodyText(stringResource(R.string.settings_privacy_device_text))
            SectionHeader(stringResource(R.string.settings_privacy_apps_title))
            BodyText(stringResource(R.string.settings_privacy_apps_text))
            SectionHeader(stringResource(R.string.settings_privacy_roms_title))
            BodyText(stringResource(R.string.settings_privacy_roms_text))
            SectionHeader(stringResource(R.string.settings_privacy_dots_title))
            BodyText(stringResource(R.string.settings_privacy_dots_text))
            SectionHeader(stringResource(R.string.settings_privacy_lock_title))
            BodyText(stringResource(R.string.settings_privacy_lock_text))
            SectionHeader(stringResource(R.string.settings_privacy_updates_title))
            BodyText(stringResource(R.string.settings_privacy_updates_text))
            SectionHeader(stringResource(R.string.settings_privacy_retention_title))
            BodyText(stringResource(R.string.settings_privacy_retention_text))
            SectionHeader(stringResource(R.string.settings_privacy_contact_title))
            BodyText(stringResource(R.string.settings_privacy_contact_text))
            PlutoButton("privacy:contact", stringResource(R.string.settings_privacy_email), { openLink(context, "mailto:anders@afive.dev") })
            PlutoButton("privacy:website", stringResource(R.string.settings_privacy_website), { openLink(context, PRIVACY_URL) })
        }
    }
}
