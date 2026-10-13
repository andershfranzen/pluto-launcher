package dev.pluto.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.domain.CrashReport
import dev.pluto.launcher.system.CrashLog
import dev.pluto.launcher.ui.overlay.ActionRow
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.ButtonBar
import dev.pluto.launcher.ui.overlay.ButtonStyle
import dev.pluto.launcher.ui.overlay.ModalPanel
import dev.pluto.launcher.ui.overlay.PlutoButton
import dev.pluto.launcher.ui.overlay.rememberScreenFocus
import java.io.File

/** How much of a trace the panel shows; the full record is what the issue link prefills. */
private const val TRACE_LINES = 60

/**
 * Lists the crash records kept on this device, shows the chosen one and offers a prefilled
 * GitHub issue. Reading the folder happens when the panel opens; the only thing that ever
 * leaves the device is an issue the user submits in their own browser.
 */
@Composable
internal fun CrashReportsDialog(records: List<File>, onCleared: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = rememberVersionName()
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = records.firstOrNull { it.name == opened } ?: records.firstOrNull()
    val record = selected?.let { CrashLog.text(context, it) }.orEmpty()
    rememberScreenFocus(selected?.let { "crash:record:${it.name}" } ?: "settings:crashes:close")

    ModalPanel("Crash reports", "settings:crashes", onDismiss, interceptBack = true, maxWidth = 640.dp) {
        if (records.isEmpty()) {
            BodyText("No crashes recorded. If Pluto ever crashes, the trace is kept here — on this device only — so you can decide whether to report it.")
            return@ModalPanel
        }
        BodyText("Kept on this device only. Nothing is sent until you open the report on GitHub and submit it yourself.")
        records.forEach { file ->
            ActionRow(
                id = "crash:record:${file.name}",
                label = CrashLog.label(file),
                supporting = CrashReport.errorLine(CrashLog.text(context, file)),
                icon = Icons.Rounded.Report,
                selected = file.name == selected?.name,
                onClick = { opened = file.name },
            )
        }
        Spacer(Modifier.height(8.dp))
        Trace(record)
        Spacer(Modifier.height(8.dp))
        ButtonBar(Modifier.padding(horizontal = 8.dp), arrangement = Arrangement.spacedBy(8.dp)) {
            PlutoButton(
                "crash:report",
                "Report on GitHub",
                { openLink(context, CrashReport.issueUrl(CrashReport.issueTitle(versionName, record), record)) },
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                style = ButtonStyle.FILLED,
            )
            PlutoButton(
                "crash:clear",
                "Clear all",
                {
                    CrashLog.clear(context)
                    onCleared()
                },
                icon = Icons.Rounded.Delete,
                style = ButtonStyle.TEXT,
            )
        }
    }
}

/** The record itself, monospaced so a stack trace reads as one. Long traces are cut here. */
@Composable
private fun Trace(record: String) {
    val lines = remember(record) { record.lineSequence().take(TRACE_LINES).toList() }
    val truncated = remember(record) { record.lineSequence().drop(TRACE_LINES).any() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            lines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (truncated) {
            Text(
                "… the full trace is in the report.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
