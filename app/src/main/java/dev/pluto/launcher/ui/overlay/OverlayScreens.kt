package dev.pluto.launcher.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppIcon

/*
 * Entry points for modal layers rendered by LauncherRoot when the matching Layer is on top.
 * Each is a full-screen or dialog-style surface, fully controller-navigable, with an
 * explicit initial focus, and closes via vm.back().
 *
 * Layers opened from here (Categories…, Move to folder…) stack on top of the sheet, so
 * Back returns to the sheet and then home. Actions that leave the launcher or finish the
 * task (Launch, Hide, App info, Uninstall) close the sheet first.
 */

/** Launch, Pin/Unpin, Add to dock/Remove from dock, Categories…, Move to folder…, Hide, App info, Uninstall (system confirmation). */
@Composable
fun AppActionsSheet(key: AppKey, state: LauncherUiState, vm: LauncherViewModel) {
    val entry = state.allApps.firstOrNull { it.key == key }
    val isTop = state.session.topLayer == Layer.AppActions(key)
    var dockFull by rememberSaveable(key.encode()) { mutableStateOf(false) }
    rememberScreenFocus(if (entry != null) "actions:launch" else "actions:close")

    ModalPanel(
        title = entry?.label ?: OverlayText.APP_ACTIONS,
        idPrefix = "actions",
        onDismiss = { vm.back() },
        trapFocus = isTop,
        leading = entry?.let { { AppIcon(it, 40.dp) } },
    ) {
        if (entry == null) {
            NoticeCard(OverlayText.APP_MISSING, isError = true, icon = Icons.Rounded.Warning)
        } else {
            val pinnedDirectly = state.homeTiles.any { it is HomeTile.App && it.entry.key == key }
            val folder = state.folderOf(key)
            val inDock = state.isInDock(key)
            val isHidden = key in state.hidden
            val categoryNames = state.categories
                .filter { !it.isAll && key in state.categoryMembers[it.id].orEmpty() }
                .map { it.name }

            BodyText(entry.packageName)
            ActionRow(
                id = "actions:launch",
                label = OverlayText.LAUNCH,
                icon = Icons.Rounded.PlayArrow,
                supporting = if (!entry.isEnabled) OverlayText.SUSPENDED else null,
                onClick = {
                    vm.back()
                    vm.launch(key)
                },
            )
            if (pinnedDirectly) {
                ActionRow(id = "actions:pin", label = OverlayText.UNPIN, icon = Icons.Rounded.PushPin, onClick = { vm.unpin(key) })
            } else {
                ActionRow(
                    id = "actions:pin",
                    label = OverlayText.PIN,
                    icon = Icons.Rounded.PushPin,
                    supporting = folder?.let { OverlayText.inFolder(it.folder.name) },
                    onClick = { vm.pin(key) },
                )
            }
            if (inDock) {
                ActionRow(
                    id = "actions:dock",
                    label = OverlayText.REMOVE_FROM_DOCK,
                    icon = Icons.Rounded.RemoveCircleOutline,
                    onClick = {
                        dockFull = false
                        vm.removeFromDock(key)
                    },
                )
            } else {
                ActionRow(
                    id = "actions:dock",
                    label = OverlayText.ADD_TO_DOCK,
                    icon = Icons.Rounded.AddCircleOutline,
                    onClick = { dockFull = !vm.addToDock(key) },
                )
            }
            if (dockFull && !inDock) NoticeCard(OverlayText.DOCK_FULL)
            ActionRow(
                id = "actions:categories",
                label = OverlayText.CATEGORIES,
                icon = Icons.Rounded.Category,
                supporting = OverlayText.inCategories(categoryNames),
                onClick = { vm.openLayer(Layer.CategoryMembership(key)) },
            )
            ActionRow(
                id = "actions:folder",
                label = OverlayText.MOVE_TO_FOLDER,
                icon = Icons.Rounded.Folder,
                supporting = folder?.let { OverlayText.inFolder(it.folder.name) },
                onClick = { vm.openLayer(Layer.MoveToFolder(key)) },
            )
            if (isHidden) {
                ActionRow(id = "actions:hide", label = OverlayText.UNHIDE, icon = Icons.Rounded.Visibility, onClick = { vm.unhide(key) })
            } else {
                ActionRow(
                    id = "actions:hide",
                    label = OverlayText.HIDE,
                    icon = Icons.Rounded.VisibilityOff,
                    supporting = OverlayText.HIDE_HINT,
                    onClick = {
                        vm.back()
                        vm.hide(key)
                    },
                )
            }
            ActionRow(
                id = "actions:info",
                label = OverlayText.APP_INFO,
                icon = Icons.Rounded.Info,
                onClick = {
                    vm.back()
                    vm.openAppInfo(key)
                },
            )
            // A normal list item behind the system's own confirmation; never bound to a button.
            ActionRow(
                id = "actions:uninstall",
                label = OverlayText.UNINSTALL,
                icon = Icons.Rounded.Delete,
                supporting = OverlayText.UNINSTALL_HINT,
                destructive = true,
                onClick = {
                    vm.back()
                    vm.requestUninstall(key)
                },
            )
        }
    }
}

/** Checkbox list of non-ALL categories for [key]. */
@Composable
fun CategoryMembershipDialog(key: AppKey, state: LauncherUiState, vm: LauncherViewModel) {
    val entry = state.allApps.firstOrNull { it.key == key }
    val categories = state.categories.filter { !it.isAll }.sortedBy { it.position }
    val isTop = state.session.topLayer == Layer.CategoryMembership(key)
    rememberScreenFocus(categories.firstOrNull()?.let { "membership:cat:${it.id}" } ?: "membership:manage")

    ModalPanel(
        title = entry?.label?.let { "${OverlayText.CATEGORIES_TITLE}: $it" } ?: OverlayText.CATEGORIES_TITLE,
        idPrefix = "membership",
        onDismiss = { vm.back() },
        trapFocus = isTop,
        leading = entry?.let { { AppIcon(it, 40.dp) } },
        actions = {
            PlutoButton("membership:done", OverlayText.DONE, { vm.back() }, style = ButtonStyle.FILLED)
        },
    ) {
        if (entry == null) NoticeCard(OverlayText.APP_MISSING, isError = true, icon = Icons.Rounded.Warning)
        BodyText(OverlayText.ALL_APPS_NOTE)
        if (categories.isEmpty()) BodyText(OverlayText.NO_CATEGORIES)
        categories.forEach { category ->
            val member = key in state.categoryMembers[category.id].orEmpty()
            CheckRow(
                id = "membership:cat:${category.id}",
                label = category.name,
                checked = member,
                onCheckedChange = { vm.setCategoryMembership(category.id, key, it) },
            )
        }
        ActionRow(
            id = "membership:manage",
            label = OverlayText.MANAGE_CATEGORIES,
            icon = Icons.Rounded.Category,
            onClick = { vm.openLayer(Layer.Categories) },
        )
    }
}

/** Existing folders + "New folder…" (name entry) + "Remove from folder". */
@Composable
fun MoveToFolderDialog(key: AppKey, state: LauncherUiState, vm: LauncherViewModel) {
    val entry = state.allApps.firstOrNull { it.key == key }
    val current = state.folderOf(key)
    val folders = state.folders.values.sortedBy { it.folder.name.lowercase() }
    val isTop = state.session.topLayer == Layer.MoveToFolder(key)
    var naming by rememberSaveable(key.encode()) { mutableStateOf(false) }
    val screenFocus = rememberScreenFocus(
        folders.firstOrNull { it.folder.id != current?.folder?.id }?.let { "movefolder:f:${it.folder.id}" }
            ?: "movefolder:new",
    )

    Box(Modifier.fillMaxSize()) {
        ModalPanel(
            title = entry?.label?.let { "${OverlayText.MOVE_TITLE}: $it" } ?: OverlayText.MOVE_TITLE,
            idPrefix = "movefolder",
            onDismiss = { vm.back() },
            trapFocus = isTop,
            dialogOpen = naming,
            leading = entry?.let { { AppIcon(it, 40.dp) } },
            actions = {
                PlutoButton("movefolder:cancel", KitText.CANCEL, { vm.back() }, style = ButtonStyle.TEXT)
            },
        ) {
            if (entry == null) NoticeCard(OverlayText.APP_MISSING, isError = true, icon = Icons.Rounded.Warning)
            if (folders.isEmpty()) BodyText(OverlayText.NO_FOLDERS)
            folders.forEach { folderUi ->
                val folder = folderUi.folder
                val isCurrent = folder.id == current?.folder?.id
                ActionRow(
                    id = "movefolder:f:${folder.id}",
                    label = folder.name,
                    icon = Icons.Rounded.Folder,
                    supporting = if (isCurrent) OverlayText.CURRENT_FOLDER else OverlayText.appCount(folder.apps.size),
                    selected = isCurrent,
                    enabled = !isCurrent,
                    onClick = {
                        vm.moveAppToFolder(key, folder.id)
                        vm.back()
                    },
                )
            }
            ActionRow(
                id = "movefolder:new",
                label = OverlayText.NEW_FOLDER,
                icon = Icons.Rounded.CreateNewFolder,
                onClick = { naming = true },
            )
            if (current != null) {
                ActionRow(
                    id = "movefolder:remove",
                    label = OverlayText.REMOVE_FROM_FOLDER,
                    icon = Icons.Rounded.FolderOff,
                    supporting = OverlayText.inFolder(current.folder.name),
                    onClick = {
                        vm.removeAppFromFolder(key)
                        vm.back()
                    },
                )
            }
        }
        if (naming) {
            TextInputDialog(
                idPrefix = "movefolder:name",
                title = OverlayText.NEW_FOLDER_TITLE,
                fieldLabel = OverlayText.FOLDER_NAME,
                initial = OverlayText.DEFAULT_FOLDER_NAME,
                confirmLabel = OverlayText.CREATE,
                onConfirm = { name ->
                    naming = false
                    vm.createFolder(name, listOf(key))
                    vm.back()
                },
                onDismiss = {
                    naming = false
                    screenFocus.returnFrom("movefolder:new")
                },
            )
        }
    }
}

// EditScreen lives in EditScreen.kt (same package).

/** Shared app glyph for rows whose app is unavailable. */
internal val UnavailableAppIcon = Icons.Rounded.Apps
