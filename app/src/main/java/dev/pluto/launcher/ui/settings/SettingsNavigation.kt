package dev.pluto.launcher.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import dev.pluto.launcher.R
import dev.pluto.launcher.ui.UiText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import dev.pluto.launcher.ui.components.LocalGlassBackdrop
import dev.pluto.launcher.ui.focus.controllerFocusable

/** Stable sections keep settings navigable on phones and with a D-pad. */
internal enum class SettingsPage(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    APPEARANCE(R.string.settings_page_appearance, R.string.settings_page_appearance_description),
    LAYOUT(R.string.settings_page_layout, R.string.settings_page_layout_description),
    APPS(R.string.settings_page_apps, R.string.settings_page_apps_description),
    GENERAL(R.string.settings_page_general, R.string.settings_page_general_description),
    ;

    val title: String get() = UiText.get(titleRes)
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
                            contentDescription = stringResource(page.descriptionRes),
                            shape = shape,
                        )
                        .semantics { selected = picked }
                        .clip(shape)
                        .background(if (picked) colors.inverseSurface else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    dev.pluto.launcher.ui.overlay.FitLabel(
                        stringResource(page.titleRes),
                        MaterialTheme.typography.labelLarge,
                        if (picked) colors.inverseOnSurface else colors.onSurfaceVariant,
                        TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
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
                .background(colors.surfaceContainerHigh.copy(alpha = if (LocalGlassBackdrop.current != null) 0.35f else 0.55f))
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), shape)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
