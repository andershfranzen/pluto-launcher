package dev.pluto.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.components.PlutoPanel
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.settings.openDefaultAppsSettings

private const val ID_RETRY = "storage:retry"
private const val ID_HOME_APPS = "storage:home-apps"

/**
 * Shown instead of the launcher when its database or settings cannot be read (for example
 * a build without the needed migration). Nothing is deleted or reset: the user can retry,
 * or switch to another Home app through Android's Default apps settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageErrorScreen(detail: String, onRetry: () -> Unit) {
    val focus = LocalControllerFocus.current
    val context = LocalContext.current
    SideEffect { focus.setDefaultFocus(ID_RETRY) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlutoPanel(Modifier.widthIn(max = 560.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pluto can't open its saved layout",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Your favourites, dock, folders and settings have not been changed or deleted. " +
                        "Try again, or choose another Home app in Android Settings while this is sorted out. " +
                        "Installing the newer Pluto build you used before usually fixes this.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Details: $detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlutoTextButton(id = ID_RETRY, text = "Try again", icon = Icons.Rounded.Refresh, onClick = onRetry, emphasized = true)
                    PlutoTextButton(
                        id = ID_HOME_APPS,
                        text = "Choose Home app",
                        icon = Icons.Rounded.Home,
                        onClick = { openDefaultAppsSettings(context) },
                    )
                }
            }
        }
    }
}
