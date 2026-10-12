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
        title = if (screenLock) "Allow double-tap screen locking?" else "Allow notification dots?",
        text = if (screenLock)
            "Pluto uses Android’s Accessibility Service API only to lock your screen when you double-tap empty space on Home. " +
                "This service cannot read screen content and does not monitor accessibility events. It does not collect, store or share personal data. " +
                "Screen locking is optional. You can turn off “Pluto screen lock” in Android’s Accessibility settings at any time."
        else
            "Android notification access lets Pluto receive notification information, including while Pluto is in the background. " +
                "Pluto uses app and profile identifiers, notification flags and badge eligibility only to show dots. " +
                "It does not read or store message text. The metadata stays in memory on your device and is never sent to a server. " +
                "Notification dots are optional. You can revoke notification access in Android Settings at any time.",
        confirmLabel = "Agree and continue",
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
    LayerScaffold(title = "Privacy policy", idPrefix = "privacy", onClose = vm::back, fullScreen = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyText("Pluto Launcher · Anders (andershfranzen) · Updated 12 October 2026")
            SectionHeader("Your information stays on your device")
            BodyText("Pluto has no account, ads, analytics, tracking SDKs or remote crash reporting. The developer does not receive your installed-app list, layout, searches, ROMs, notifications or launch history.")
            SectionHeader("Apps, layout and recent launches")
            BodyText("Pluto reads launchable app names, icons and shortcuts to display and search them. Favourites, folders, hidden apps, categories, widget IDs and settings are saved in the app’s private storage. Optional recent launches are stored locally; you can clear them or turn history off in Settings. Search queries are used locally and are not saved as a search history.")
            SectionHeader("ROM folders and artwork")
            BodyText("Pluto accesses only folders and images you select with Android’s file picker. It saves file references, game names and artwork locally to build your library. Opening a game gives your chosen emulator access to its selected file. Removing a ROM tab deletes Pluto’s library records, not your game files. Emulators and widget providers handle their own data under their own policies.")
            SectionHeader("Notification dots")
            BodyText("Optional Android notification access is used for app/profile identifiers, notification flags and badge eligibility. This metadata is held only in memory, including in the background while access is enabled. Pluto does not read or store notification message text or send notification data off the device. Revoke access in Android Settings to stop the listener.")
            SectionHeader("Double-tap screen locking")
            BodyText("The optional Accessibility Service API is used only to perform Android’s lock-screen action after your double tap. It cannot retrieve screen content and does not subscribe to accessibility events. No personal data is collected, stored or shared through this service. Disable “Pluto screen lock” in Android’s Accessibility settings to revoke access.")
            SectionHeader("Updates and external links")
            BodyText("The Google Play build has no Internet permission or GitHub updater. Google Play handles its updates under Google’s own privacy policy. The GitHub build contacts GitHub only for update checks you permit and downloads you request. GitHub receives normal connection metadata, such as your IP address and Pluto version; no launcher activity is sent. Update checks are optional and downloads require your approval. External support links open in your browser and are subject to the destination’s privacy policy.")
            SectionHeader("Retention and deletion")
            BodyText("Pluto’s saved data remains in its private on-device storage until you remove it, clear the app’s storage or uninstall. Android backup is disabled. Revoking file permissions stops future access. Clearing Pluto’s storage does not delete the ROM files or artwork you selected. Pluto has no server account or server-held user records to delete.")
            SectionHeader("Contact")
            BodyText("For privacy questions, email anders@afive.dev. You can also report issues through the Pluto Launcher GitHub issue tracker. Avoid including private information in a public issue.")
            PlutoButton("privacy:contact", "Email the developer", { openLink(context, "mailto:anders@afive.dev") })
            PlutoButton("privacy:website", "View public privacy policy", { openLink(context, PRIVACY_URL) })
        }
    }
}
