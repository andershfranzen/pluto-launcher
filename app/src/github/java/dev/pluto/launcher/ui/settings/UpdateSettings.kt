package dev.pluto.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.R
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.ButtonBar
import dev.pluto.launcher.ui.overlay.ButtonStyle
import dev.pluto.launcher.ui.overlay.ModalPanel
import dev.pluto.launcher.ui.overlay.NoticeCard
import dev.pluto.launcher.ui.overlay.PlutoButton
import dev.pluto.launcher.ui.overlay.SwitchRow
import dev.pluto.launcher.ui.overlay.rememberScreenFocus
import dev.pluto.launcher.updates.UpdateTask
import java.text.DateFormat
import java.util.Date

@Composable
internal fun UpdateSettings(settings: LauncherSettings, vm: LauncherViewModel) {
    val update by vm.updateState.collectAsState()
    val context = LocalContext.current
    SettingsSection(stringResource(R.string.settings_updates)) {
        SwitchRow("settings:updates:auto", stringResource(R.string.settings_updates_auto), settings.automaticUpdateChecks,
            { enabled -> vm.chooseAutomaticUpdates(enabled) },
            supporting = stringResource(R.string.settings_updates_auto_help))
        BodyText(when (update.task) {
            UpdateTask.CHECKING -> stringResource(R.string.settings_updates_checking)
            UpdateTask.DOWNLOADING -> stringResource(R.string.settings_updates_downloading, "${update.release?.versionName}")
            UpdateTask.VERIFYING -> stringResource(R.string.settings_updates_verifying)
            UpdateTask.NONE -> when {
                update.readyToInstall -> stringResource(R.string.settings_updates_ready)
                update.release != null -> stringResource(R.string.settings_updates_available, update.release!!.versionName)
                update.lastChecked > 0 -> stringResource(R.string.settings_updates_up_to_date)
                else -> stringResource(R.string.settings_updates_idle)
            }
        })
        if (update.task == UpdateTask.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { (update.downloadedBytes.toFloat() / (update.release?.size ?: 1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        update.error?.let { NoticeCard(it, isError = true) }
        if (update.lastChecked > 0) BodyText(stringResource(R.string.settings_updates_last_checked, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(update.lastChecked))))
        ButtonBar(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            PlutoButton("settings:updates:check", stringResource(R.string.settings_updates_check), { vm.checkForUpdates() }, enabled = !update.busy)
            if (update.task == UpdateTask.DOWNLOADING) PlutoButton("settings:updates:cancel", stringResource(R.string.settings_updates_cancel), { vm.cancelUpdateDownload() })
            else if (update.release != null) PlutoButton("settings:updates:download",
                stringResource(if (update.readyToInstall) R.string.settings_updates_install else R.string.settings_updates_download),
                { if (update.readyToInstall) vm.installUpdate() else vm.downloadUpdate() },
                style = ButtonStyle.FILLED, enabled = !update.busy)
        }
        if (update.release != null) PlutoButton("settings:updates:notes", stringResource(R.string.settings_updates_notes),
            { openLink(context, update.release!!.pageUrl) }, modifier = Modifier.padding(horizontal = 8.dp))
        BodyText(stringResource(R.string.settings_updates_disclosure))
    }
}

/** The first-launch choice uses a content-sized glass dialog, with a compact landscape header. */
@Composable
internal fun UpdateCheckConsent(vm: LauncherViewModel) {
    rememberScreenFocus("updates:allow")
    val window = LocalWindowInfo.current.containerSize
    val landscape = window.width > window.height
    val colors = MaterialTheme.colorScheme
    ModalPanel(
        title = stringResource(R.string.settings_updates_consent_title), idPrefix = "updates:consent",
        onDismiss = { vm.chooseAutomaticUpdates(false) }, interceptBack = true,
        maxWidth = 440.dp, compact = true,
        actions = {
            PlutoButton("updates:decline", stringResource(R.string.settings_updates_consent_decline), { vm.chooseAutomaticUpdates(false) }, style = ButtonStyle.TEXT)
            PlutoButton("updates:allow", stringResource(R.string.settings_updates_consent_allow), { vm.chooseAutomaticUpdates(true) }, style = ButtonStyle.FILLED)
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (landscape) {
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    UpdatePlutoLogo(88.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        UpdateConsentTitle(TextAlign.Start)
                        Text(stringResource(R.string.settings_updates_consent_question), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                }
            } else {
                UpdatePlutoLogo(104.dp)
                UpdateConsentTitle(TextAlign.Center)
                Text(stringResource(R.string.settings_updates_consent_question), style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Column(
                Modifier.fillMaxWidth().background(colors.onSurface.copy(alpha = 0.045f), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UpdateConsentFact(Icons.Rounded.Refresh, stringResource(R.string.settings_updates_consent_daily))
                UpdateConsentFact(Icons.Rounded.Download, stringResource(R.string.settings_updates_consent_approval))
            }
            Text(stringResource(R.string.settings_updates_consent_footer),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun UpdateConsentTitle(alignment: TextAlign) {
    Text(stringResource(R.string.settings_updates_consent_title), style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface, textAlign = alignment,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun UpdatePlutoLogo(size: Dp) {
    Box(
        Modifier.size(size).background(
            Brush.radialGradient(listOf(Color(0xFFD2AE8A).copy(alpha = 0.28f), Color.Transparent)),
            CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.settings_updates_logo), modifier = Modifier.size(size).graphicsLayer { scaleX = 1.65f; scaleY = 1.65f })
    }
}

@Composable
private fun UpdateConsentFact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
