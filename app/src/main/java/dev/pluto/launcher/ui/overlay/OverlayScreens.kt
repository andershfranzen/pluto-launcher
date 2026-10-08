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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.motion.LocalAppLauncher
import dev.pluto.launcher.ui.motion.LocalOriginRegistry

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
    val appLauncher = LocalAppLauncher.current
    // Launch grows the app out of the tile that opened this sheet, when that tile is on screen.
    val controller = LocalControllerFocus.current
    val origins = LocalOriginRegistry.current
    val originId = remember(key) { controller.openerId?.takeIf { origins.boundsOf(it) != null } }
    // Rows stagger in once per opening (not again after rotation).
    var entrancePlayed by rememberSaveable(key.encode()) { mutableStateOf(false) }
    val stagger = remember { !entrancePlayed }
    LaunchedEffect(Unit) { entrancePlayed = true }

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

            fun Modifier.entrance(index: Int) = staggerIn(index, enabled = stagger)
            BodyText(entry.packageName, Modifier.entrance(0))
            ActionRow(
                id = "actions:launch",
                label = OverlayText.LAUNCH,
                icon = Icons.Rounded.PlayArrow,
                supporting = if (!entry.isEnabled) OverlayText.SUSPENDED else null,
                modifier = Modifier.entrance(1),
                onClick = {
                    vm.back()
                    appLauncher.launch(key, originId)
                },
            )
            // One row per action whose label crossfades when its state flips, so focus stays put.
            ActionRow(
                id = "actions:pin",
                label = if (pinnedDirectly) OverlayText.UNPIN else OverlayText.PIN,
                icon = Icons.Rounded.PushPin,
                supporting = if (pinnedDirectly) null else folder?.let { OverlayText.inFolder(it.folder.name) },
                modifier = Modifier.entrance(2),
                animateChanges = true,
                onClick = { if (pinnedDirectly) vm.unpin(key) else vm.pin(key) },
            )
            ActionRow(
                id = "actions:dock",
                label = if (inDock) OverlayText.REMOVE_FROM_DOCK else OverlayText.ADD_TO_DOCK,
                icon = if (inDock) Icons.Rounded.RemoveCircleOutline else Icons.Rounded.AddCircleOutline,
                modifier = Modifier.entrance(3),
                animateChanges = true,
                onClick = {
                    dockFull = false
                    if (inDock) vm.removeFromDock(key) else vm.addToDock(key, onDockFull = { dockFull = true })
                },
            )
            ExpandingSection(visible = dockFull && !inDock) { NoticeCard(OverlayText.DOCK_FULL) }
            ActionRow(
                id = "actions:categories",
                label = OverlayText.CATEGORIES,
                icon = Icons.Rounded.Category,
                supporting = OverlayText.inCategories(categoryNames),
                modifier = Modifier.entrance(4),
                onClick = { vm.openLayer(Layer.CategoryMembership(key)) },
            )
            ActionRow(
                id = "actions:folder",
                label = OverlayText.MOVE_TO_FOLDER,
                icon = Icons.Rounded.Folder,
                supporting = folder?.let { OverlayText.inFolder(it.folder.name) },
                modifier = Modifier.entrance(5),
                onClick = { vm.openLayer(Layer.MoveToFolder(key)) },
            )
            ActionRow(
                id = "actions:hide",
                label = if (isHidden) OverlayText.UNHIDE else OverlayText.HIDE,
                icon = if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                supporting = if (isHidden) null else OverlayText.HIDE_HINT,
                modifier = Modifier.entrance(6),
                animateChanges = true,
                onClick = {
                    if (isHidden) {
                        vm.unhide(key)
                    } else {
                        vm.back()
                        vm.hide(key)
                    }
                },
            )
            ActionRow(
                id = "actions:info",
                label = OverlayText.APP_INFO,
                icon = Icons.Rounded.Info,
                modifier = Modifier.entrance(7),
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
                modifier = Modifier.entrance(8),
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
        AnimatedDialog(if (naming) Unit else null) {
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
