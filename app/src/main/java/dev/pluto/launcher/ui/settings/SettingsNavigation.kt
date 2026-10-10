package dev.pluto.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import dev.pluto.launcher.ui.focus.controllerFocusable

/** Stable sections keep settings navigable on phones and with a D-pad. */
internal enum class SettingsPage(val title: String, val description: String) {
    APPEARANCE("Appearance", "Make Pluto feel like home."),
    LAYOUT("Layout", "Rotation, handheld mode and controller controls."),
    APPS("Apps", "Organise your library and manage recent launches."),
    GENERAL("General", "Default launcher, privacy and app information."),
}

@Composable
internal fun SettingsNavigation(selectedPage: SettingsPage, onSelect: (SettingsPage) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        // One segmented bar: the chosen page is a filled pill inside a quiet track.
        val track = RoundedCornerShape(18.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(track)
                .background(colors.onSurface.copy(alpha = 0.06f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.35f), track)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsPage.entries.forEach { page ->
                val picked = page == selectedPage
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .controllerFocusable(
                            id = "settings:page:${page.name}",
                            onActivate = { onSelect(page) },
                            contentDescription = "${page.title} settings",
                            shape = shape,
                        )
                        .semantics { selected = picked }
                        .clip(shape)
                        .background(if (picked) colors.inverseSurface else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        page.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (picked) colors.inverseOnSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            selectedPage.description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
        )
    }
}

/** A glassy card with a real section boundary, rather than an endless stack of rows. */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp).semantics { heading() },
        )
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(colors.surfaceContainerHigh.copy(alpha = 0.55f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), shape)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
