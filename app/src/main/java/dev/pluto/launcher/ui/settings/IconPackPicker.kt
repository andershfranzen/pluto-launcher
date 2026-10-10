package dev.pluto.launcher.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.pluto.launcher.apps.IconPack
import dev.pluto.launcher.apps.IconPackInfo
import dev.pluto.launcher.ui.overlay.ActionRow
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.ButtonStyle
import dev.pluto.launcher.ui.overlay.KitText
import dev.pluto.launcher.ui.overlay.ModalPanel
import dev.pluto.launcher.ui.overlay.PlutoButton
import dev.pluto.launcher.ui.overlay.rememberScreenFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Installed icon packs (null while looking), queried off the main thread. */
@Composable
internal fun rememberIconPacks(): List<IconPackInfo>? {
    val context = LocalContext.current.applicationContext
    val packs by produceState<List<IconPackInfo>?>(null, context) {
        value = withContext(Dispatchers.IO) { IconPack.installed(context) }
    }
    return packs
}

/** Choose the system icons or an installed icon pack. Picking applies at once and closes. */
@Composable
internal fun IconPackDialog(current: String?, packs: List<IconPackInfo>?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    rememberScreenFocus(iconPackRowId(current))
    ModalPanel(
        title = SettingsText.ICON_PACK,
        idPrefix = "settings:iconpack",
        onDismiss = onDismiss,
        interceptBack = true,
        actions = { PlutoButton("settings:iconpack:cancel", KitText.CANCEL, onDismiss, style = ButtonStyle.TEXT) },
    ) {
        ActionRow(
            id = iconPackRowId(null),
            label = SettingsText.SYSTEM_ICONS,
            supporting = SettingsText.SYSTEM_ICONS_HELP,
            icon = Icons.Rounded.Apps,
            selected = current == null,
            onClick = { onPick(null) },
        )
        when {
            packs == null -> BodyText(SettingsText.ICON_PACKS_LOADING)
            packs.isEmpty() -> BodyText(SettingsText.NO_ICON_PACKS)
            else -> packs.forEach { pack ->
                ActionRow(
                    id = iconPackRowId(pack.packageName),
                    label = pack.label,
                    icon = Icons.Rounded.Palette,
                    selected = current == pack.packageName,
                    onClick = { onPick(pack.packageName) },
                )
            }
        }
    }
}

private fun iconPackRowId(packageName: String?) = "settings:iconpack:${packageName ?: "system"}"

/** The label shown for the current choice in settings. */
internal fun iconPackLabel(current: String?, packs: List<IconPackInfo>?): String = when {
    current == null -> SettingsText.SYSTEM_ICONS
    else -> packs?.firstOrNull { it.packageName == current }?.label ?: SettingsText.ICON_PACK_MISSING
}
