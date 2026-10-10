package dev.pluto.launcher.ui.overlay

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.domain.Reorder
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.model.ReorderOp
import dev.pluto.launcher.ui.FolderUi
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppIcon

/*
 * Edit mode: every home favourite, dock slot and folder member is a focusable row.
 * Selecting a row (stored in session.editSelection so it survives rotation) reveals its
 * controls directly underneath, so controller users move down into them and touch users
 * tap them. Moves never need drag and drop.
 *
 * Selection ids: HomeItem.id for home tiles, "dock:<slot>" for dock slots and the app
 * key (AppKey.encode()) for apps inside folders.
 */

private const val DOCK_PREFIX = "dock:"

/** What is selected, resolved against the current state. */
private sealed interface EditTarget {
    val label: String

    data class Home(val tile: HomeTile, val index: Int, val item: HomeItem) : EditTarget {
        override val label: String
            get() = when (tile) {
                is HomeTile.App -> tile.entry.label
                is HomeTile.FolderTile -> tile.folder.folder.name
                is HomeTile.Widget -> OverlayText.WIDGET
            }
    }

    data class Dock(val slot: Int, val entry: AppEntry?) : EditTarget {
        override val label: String get() = entry?.label ?: OverlayText.dockSlot(slot)
    }

    data class FolderApp(val folder: FolderUi, val entry: AppEntry, val index: Int) : EditTarget {
        override val label: String get() = entry.label
    }
}

/** Nested dialogs of the edit screen; saved by name so they survive rotation. */
private enum class EditDialog { MOVE_BEFORE, MOVE_AFTER, PICK_APP, PICK_FAVOURITE, RENAME_FOLDER, DELETE_FOLDER, NEW_FOLDER }

private fun rowId(selectionId: String) = "edit:row:$selectionId"

/** Folders in home order first, then any not currently on the home grid. */
private fun orderedFolders(state: LauncherUiState): List<FolderUi> {
    val onHome = state.homeTiles.filterIsInstance<HomeTile.FolderTile>().map { it.folder.folder.id }
    val byId = state.folders
    return onHome.mapNotNull { byId[it] } + byId.values.filter { it.folder.id !in onHome }.sortedBy { it.folder.name.lowercase() }
}

private fun resolve(selection: String?, state: LauncherUiState): EditTarget? {
    if (selection == null) return null
    if (selection.startsWith(DOCK_PREFIX)) {
        val slot = selection.removePrefix(DOCK_PREFIX).toIntOrNull() ?: return null
        if (slot !in 0 until Organization.DOCK_SLOTS) return null
        return EditTarget.Dock(slot, state.dock.getOrNull(slot))
    }
    val homeIndex = state.homeTiles.indexOfFirst { it.id == selection }
    if (homeIndex >= 0) {
        val item = HomeItem.decode(selection) ?: return null
        return EditTarget.Home(state.homeTiles[homeIndex], homeIndex, item)
    }
    val key = AppKey.decode(selection) ?: return null
    for (folder in state.folders.values) {
        val index = folder.apps.indexOfFirst { it.key == key }
        if (index >= 0) return EditTarget.FolderApp(folder, folder.apps[index], index)
    }
    return null
}

/**
 * Edit mode for home favourites, dock and folders: pick an item, then Move up / down /
 * before / after (target picker), Unpin, Rename/Delete folder, New folder. No drag-and-drop needed.
 */
@Composable
fun EditScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val selection = state.session.editSelection
    val target = resolve(selection, state)
    val isTop = state.session.topLayer == Layer.Edit
    var dialog by rememberSaveable { mutableStateOf<EditDialog?>(null) }
    var dialogOpener by rememberSaveable { mutableStateOf<String?>(null) }
    val folders = orderedFolders(state)

    val firstRow = state.homeTiles.firstOrNull()?.let { rowId(it.id) } ?: rowId("${DOCK_PREFIX}0")
    // Opened with a preselected item (empty dock slot, folder, ...): start on it, or on its
    // first control for an empty dock slot, instead of the top of the list.
    val initialFocus = remember { initialFocusFor(target) }
    val screenFocus = rememberScreenFocus(initialFocus ?: firstRow)

    fun openDialog(which: EditDialog, openerId: String) {
        dialogOpener = openerId
        dialog = which
    }

    fun closeDialog() {
        dialog = null
        screenFocus.returnFrom(dialogOpener)
    }

    fun toggle(selectionId: String) = vm.setEditSelection(if (selection == selectionId) null else selectionId)

    /** After removing the selected row, focus a surviving neighbour instead of losing focus. */
    fun dropSelection(neighbourRow: String?) {
        vm.setEditSelection(null)
        screenFocus.focus(neighbourRow)
    }

    /** Dock moves change the slot, so selection and focus follow the app to its new slot. */
    fun moveDock(slot: Int, op: ReorderOp<Int>, controlId: String) {
        vm.moveDockSlot(slot, op)
        val newSlot = Reorder.apply((0 until Organization.DOCK_SLOTS).toList(), slot, op).indexOf(slot)
        if (newSlot >= 0 && newSlot != slot) {
            vm.setEditSelection("$DOCK_PREFIX$newSlot")
            screenFocus.focus(controlId)
        }
    }

    LayerScaffold(
        title = OverlayText.EDIT_TITLE,
        idPrefix = "edit",
        onClose = { vm.back() },
        trapFocus = isTop,
        dialogOpen = dialog != null,
        overlay = {
            // While a dialog animates out it keeps showing what it was opened for.
            val shownTarget = rememberLastNonNull(target)
            AnimatedDialog(dialog) { shownDialog ->
                val target = if (isDialogExiting()) shownTarget else target
                when (shownDialog) {
                    EditDialog.MOVE_BEFORE, EditDialog.MOVE_AFTER -> if (target != null) {
                        val before = shownDialog == EditDialog.MOVE_BEFORE
                        TargetPicker(
                            target = target,
                            state = state,
                            title = if (before) OverlayText.moveBeforeTitle(target.label) else OverlayText.moveAfterTitle(target.label),
                            onPick = { targetId ->
                                dialog = null
                                when (target) {
                                    is EditTarget.Home -> HomeItem.decode(targetId)?.let { other ->
                                        vm.moveHomeItem(target.item, if (before) ReorderOp.Before(other) else ReorderOp.After(other))
                                    }
                                    is EditTarget.Dock -> targetId.removePrefix(DOCK_PREFIX).toIntOrNull()?.let { other ->
                                        moveDock(target.slot, if (before) ReorderOp.Before(other) else ReorderOp.After(other), "edit:ctl:before")
                                    }
                                    is EditTarget.FolderApp -> AppKey.decode(targetId)?.let { other ->
                                        vm.moveFolderApp(
                                            target.folder.folder.id,
                                            target.entry.key,
                                            if (before) ReorderOp.Before(other) else ReorderOp.After(other),
                                        )
                                    }
                                }
                                screenFocus.returnFrom(dialogOpener)
                            },
                            onDismiss = ::closeDialog,
                        )
                    } else {
                        DismissNow(::closeDialog)
                    }
                    EditDialog.PICK_FAVOURITE -> AppPicker(
                        state = state,
                        title = OverlayText.CHOOSE_FAVOURITE,
                        isCurrent = { key -> state.homeTiles.any { it is HomeTile.App && it.entry.key == key } },
                        currentLabel = OverlayText.ON_HOME,
                        onPick = { key ->
                            dialog = null
                            vm.pin(key)
                            screenFocus.returnFrom("edit:addfav")
                        },
                        onDismiss = ::closeDialog,
                    )
                    EditDialog.PICK_APP -> if (target is EditTarget.Dock) {
                        AppPicker(
                            state = state,
                            title = OverlayText.CHOOSE_APP,
                            isCurrent = { it == target.entry?.key },
                            currentLabel = null,
                            onPick = { key ->
                                dialog = null
                                vm.setDockSlot(target.slot, key)
                                screenFocus.returnFrom(rowId("$DOCK_PREFIX${target.slot}"))
                            },
                            onDismiss = ::closeDialog,
                        )
                    } else {
                        DismissNow(::closeDialog)
                    }
                    EditDialog.RENAME_FOLDER -> {
                        val folder = (target as? EditTarget.Home)?.tile as? HomeTile.FolderTile
                        if (folder != null) {
                            TextInputDialog(
                                idPrefix = "edit:rename",
                                title = OverlayText.RENAME_FOLDER,
                                fieldLabel = OverlayText.FOLDER_NAME,
                                initial = folder.folder.folder.name,
                                confirmLabel = OverlayText.RENAME,
                                onConfirm = { name ->
                                    vm.renameFolder(folder.folder.folder.id, name)
                                    closeDialog()
                                },
                                onDismiss = ::closeDialog,
                            )
                        } else {
                            DismissNow(::closeDialog)
                        }
                    }
                    EditDialog.DELETE_FOLDER -> {
                        val home = target as? EditTarget.Home
                        val folder = home?.tile as? HomeTile.FolderTile
                        if (folder != null) {
                            ConfirmDialog(
                                idPrefix = "edit:delete",
                                title = OverlayText.deleteFolderTitle(folder.folder.folder.name),
                                text = OverlayText.DELETE_FOLDER_TEXT,
                                confirmLabel = OverlayText.DELETE,
                                destructive = true,
                                onConfirm = {
                                    dialog = null
                                    vm.deleteFolder(folder.folder.folder.id)
                                    vm.setEditSelection(null)
                                    controllerFocusAfterRemoval(state, home.index)?.let { screenFocus.returnFrom(it) }
                                        ?: screenFocus.returnFrom("edit:newfolder")
                                },
                                onDismiss = ::closeDialog,
                            )
                        } else {
                            DismissNow(::closeDialog)
                        }
                    }
                    EditDialog.NEW_FOLDER -> TextInputDialog(
                        idPrefix = "edit:newfolder:name",
                        title = OverlayText.NEW_FOLDER_TITLE,
                        fieldLabel = OverlayText.FOLDER_NAME,
                        initial = OverlayText.DEFAULT_FOLDER_NAME,
                        confirmLabel = OverlayText.CREATE,
                        onConfirm = { name ->
                            vm.createFolder(name, emptyList())
                            closeDialog()
                        },
                        onDismiss = ::closeDialog,
                    )
                }
            }
        },
    ) {
        BodyText(OverlayText.EDIT_INTRO)
        ButtonBar(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), arrangement = Arrangement.spacedBy(8.dp)) {
            PlutoButton(
                "edit:addfav",
                OverlayText.ADD_FAVOURITE,
                { openDialog(EditDialog.PICK_FAVOURITE, "edit:addfav") },
                icon = Icons.Rounded.Add,
            )
            PlutoButton(
                "edit:newfolder",
                OverlayText.NEW_FOLDER,
                { openDialog(EditDialog.NEW_FOLDER, "edit:newfolder") },
                icon = Icons.Rounded.CreateNewFolder,
            )
        }

        // --- Home favourites ---
        SectionHeader(OverlayText.HOME_SECTION)
        if (state.homeTiles.isEmpty()) BodyText(OverlayText.HOME_EMPTY)
        // Rows slide to their new places when moved and fade/shrink out when removed.
        AnimatedItems(state.homeTiles, key = { it.id }) { tile, index ->
            val selected = target is EditTarget.Home && target.tile.id == tile.id
            val item = remember(tile.id) { HomeItem.decode(tile.id) }
            Column {
                when (tile) {
                    is HomeTile.App -> ActionRow(
                        id = rowId(tile.id),
                        label = tile.entry.label,
                        leading = { AppIcon(tile.entry, 36.dp) },
                        selected = selected,
                        lift = true,
                        contentDescription = if (selected) OverlayText.selectedHint(tile.entry.label) else tile.entry.label,
                        onClick = { toggle(tile.id) },
                    )
                    is HomeTile.FolderTile -> ActionRow(
                        id = rowId(tile.id),
                        label = tile.folder.folder.name,
                        supporting = OverlayText.appCount(tile.folder.apps.size),
                        icon = Icons.Rounded.Folder,
                        selected = selected,
                        lift = true,
                        onClick = { toggle(tile.id) },
                    )
                    is HomeTile.Widget -> ActionRow(
                        id = rowId(tile.id),
                        label = OverlayText.WIDGET,
                        supporting = OverlayText.widgetRows(tile.widget.rows),
                        icon = Icons.Rounded.Widgets,
                        selected = selected,
                        lift = true,
                        onClick = { toggle(tile.id) },
                    )
                }
                ExpandingSection(visible = selected && item != null) {
                    val last = state.homeTiles.lastIndex
                    ControlBar {
                        EditControl("edit:ctl:up", OverlayText.MOVE_UP, Icons.Rounded.ArrowUpward, enabled = index > 0) {
                            item?.let { vm.moveHomeItem(it, ReorderOp.Up) }
                        }
                        EditControl("edit:ctl:down", OverlayText.MOVE_DOWN, Icons.Rounded.ArrowDownward, enabled = index < last) {
                            item?.let { vm.moveHomeItem(it, ReorderOp.Down) }
                        }
                        EditControl("edit:ctl:before", OverlayText.MOVE_BEFORE, Icons.Rounded.SwapVert, enabled = last > 0) {
                            openDialog(EditDialog.MOVE_BEFORE, "edit:ctl:before")
                        }
                        EditControl("edit:ctl:after", OverlayText.MOVE_AFTER, Icons.Rounded.SwapVert, enabled = last > 0) {
                            openDialog(EditDialog.MOVE_AFTER, "edit:ctl:after")
                        }
                        when (tile) {
                            is HomeTile.App -> {
                                EditControl("edit:ctl:unpin", OverlayText.UNPIN, Icons.Rounded.PushPin) {
                                    vm.unpin(tile.entry.key)
                                    dropSelection(controllerFocusAfterRemoval(state, index))
                                }
                                AppActionsControl(tile.entry.key, vm)
                            }
                            is HomeTile.FolderTile -> {
                                EditControl("edit:ctl:rename", OverlayText.RENAME_FOLDER, Icons.Rounded.DriveFileRenameOutline) {
                                    openDialog(EditDialog.RENAME_FOLDER, "edit:ctl:rename")
                                }
                                EditControl("edit:ctl:delete", OverlayText.DELETE_FOLDER, Icons.Rounded.Delete, destructive = true) {
                                    openDialog(EditDialog.DELETE_FOLDER, "edit:ctl:delete")
                                }
                            }
                            is HomeTile.Widget -> {
                                EditControl("edit:ctl:widget", OverlayText.WIDGET_OPTIONS, Icons.Rounded.Widgets) {
                                    vm.openLayer(Layer.WidgetActions(tile.widget.appWidgetId))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Dock ---
        SectionHeader(OverlayText.DOCK_SECTION)
        for (slot in 0 until Organization.DOCK_SLOTS) {
            val selectionId = "$DOCK_PREFIX$slot"
            val entry = state.dock.getOrNull(slot)
            val selected = target is EditTarget.Dock && target.slot == slot
            ActionRow(
                id = rowId(selectionId),
                label = entry?.label ?: OverlayText.EMPTY_SLOT,
                supporting = OverlayText.dockSlot(slot),
                leading = entry?.let { { AppIcon(it, 36.dp) } },
                icon = if (entry == null) Icons.Rounded.Add else null,
                selected = selected,
                lift = true,
                onClick = { toggle(selectionId) },
            )
            ExpandingSection(visible = selected) {
                ControlBar {
                    if (entry != null) {
                        EditControl("edit:ctl:up", OverlayText.MOVE_UP, Icons.Rounded.ArrowUpward, enabled = slot > 0) {
                            moveDock(slot, ReorderOp.Up, "edit:ctl:up")
                        }
                        EditControl(
                            "edit:ctl:down",
                            OverlayText.MOVE_DOWN,
                            Icons.Rounded.ArrowDownward,
                            enabled = slot < Organization.DOCK_SLOTS - 1,
                        ) {
                            moveDock(slot, ReorderOp.Down, "edit:ctl:down")
                        }
                        EditControl("edit:ctl:before", OverlayText.MOVE_BEFORE, Icons.Rounded.SwapVert) {
                            openDialog(EditDialog.MOVE_BEFORE, "edit:ctl:before")
                        }
                        EditControl("edit:ctl:after", OverlayText.MOVE_AFTER, Icons.Rounded.SwapVert) {
                            openDialog(EditDialog.MOVE_AFTER, "edit:ctl:after")
                        }
                        EditControl("edit:ctl:replace", OverlayText.REPLACE_APP, Icons.Rounded.Add) {
                            openDialog(EditDialog.PICK_APP, "edit:ctl:replace")
                        }
                        EditControl("edit:ctl:undock", OverlayText.REMOVE_FROM_DOCK, Icons.Rounded.RemoveCircleOutline) {
                            vm.setDockSlot(slot, null)
                            screenFocus.focus(rowId(selectionId))
                        }
                        AppActionsControl(entry.key, vm)
                    } else {
                        EditControl("edit:ctl:add", OverlayText.ADD_APP_TO_SLOT, Icons.Rounded.Add) {
                            openDialog(EditDialog.PICK_APP, "edit:ctl:add")
                        }
                    }
                }
            }
        }

        // --- Folder contents ---
        if (folders.isNotEmpty()) SectionHeader(OverlayText.FOLDERS_SECTION)
        AnimatedItems(folders, key = { it.folder.id }) { folderUi, _ ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BodyText(OverlayText.folderHeader(folderUi.folder.name))
                ExpandingSection(visible = folderUi.apps.isEmpty()) { BodyText(OverlayText.FOLDER_EMPTY) }
                AnimatedItems(folderUi.apps, key = { it.key.encode() }) { app, index ->
                    val selectionId = app.key.encode()
                    Column {
                        val selected = target is EditTarget.FolderApp && target.entry.key == app.key &&
                            target.folder.folder.id == folderUi.folder.id
                        ActionRow(
                            id = rowId(selectionId),
                            label = app.label,
                            supporting = folderUi.folder.name,
                            leading = { AppIcon(app, 36.dp) },
                            selected = selected,
                            lift = true,
                            onClick = { toggle(selectionId) },
                        )
                        ExpandingSection(visible = selected) {
                            val folderId = folderUi.folder.id
                            val last = folderUi.apps.lastIndex
                            ControlBar {
                                EditControl("edit:ctl:up", OverlayText.MOVE_UP, Icons.Rounded.ArrowUpward, enabled = index > 0) {
                                    vm.moveFolderApp(folderId, app.key, ReorderOp.Up)
                                }
                                EditControl("edit:ctl:down", OverlayText.MOVE_DOWN, Icons.Rounded.ArrowDownward, enabled = index < last) {
                                    vm.moveFolderApp(folderId, app.key, ReorderOp.Down)
                                }
                                EditControl("edit:ctl:before", OverlayText.MOVE_BEFORE, Icons.Rounded.SwapVert, enabled = last > 0) {
                                    openDialog(EditDialog.MOVE_BEFORE, "edit:ctl:before")
                                }
                                EditControl("edit:ctl:after", OverlayText.MOVE_AFTER, Icons.Rounded.SwapVert, enabled = last > 0) {
                                    openDialog(EditDialog.MOVE_AFTER, "edit:ctl:after")
                                }
                                EditControl("edit:ctl:unfolder", OverlayText.REMOVE_FROM_FOLDER, Icons.Rounded.FolderOff) {
                                    vm.removeAppFromFolder(app.key)
                                    val neighbour = folderUi.apps.getOrNull(index + 1) ?: folderUi.apps.getOrNull(index - 1)
                                    dropSelection(neighbour?.let { rowId(it.key.encode()) } ?: "edit:newfolder")
                                }
                                AppActionsControl(app.key, vm)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Opens the app's full App actions sheet (categories, folder, hide, app info) from Edit. */
@Composable
private fun AppActionsControl(key: AppKey, vm: LauncherViewModel) {
    EditControl("edit:ctl:actions", OverlayText.APP_ACTIONS_ELLIPSIS, Icons.Rounded.MoreVert) {
        vm.openLayer(Layer.AppActions(key))
    }
}

/** Initial focus for a preselected item: the "Add app" control of an empty dock slot, else its row. */
private fun initialFocusFor(target: EditTarget?): String? = when (target) {
    null -> null
    is EditTarget.Dock -> if (target.entry == null) "edit:ctl:add" else rowId("$DOCK_PREFIX${target.slot}")
    is EditTarget.Home -> rowId(target.tile.id)
    is EditTarget.FolderApp -> rowId(target.entry.key.encode())
}

/**
 * Closes a dialog whose subject vanished (e.g. app uninstalled) after composition, not during
 * it. A dialog already animating out is closed; it must not close (and move focus) again.
 */
@Composable
private fun DismissNow(onDismiss: () -> Unit) {
    val leaving = isDialogExiting()
    LaunchedEffect(Unit) { if (!leaving) onDismiss() }
}

/** Row id of the home tile that takes the place of the one at [removedIndex]. */
private fun controllerFocusAfterRemoval(state: LauncherUiState, removedIndex: Int): String? {
    val tiles = state.homeTiles
    val neighbour = tiles.getOrNull(removedIndex + 1) ?: tiles.getOrNull(removedIndex - 1)
    return neighbour?.let { rowId(it.id) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlBar(content: @Composable () -> Unit) {
    FlowRow(
        // Top padding includes the column spacing that used to separate it from its row,
        // so the whole gap expands/collapses with the bar.
        modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun EditControl(
    id: String,
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    PlutoButton(id = id, label = label, onClick = onClick, icon = icon, enabled = enabled, destructive = destructive)
}

/** Lists the other items of the selected item's list as move targets. */
@Composable
private fun TargetPicker(
    target: EditTarget,
    state: LauncherUiState,
    title: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // (selection id, label, leading app) for every possible target except the item itself.
    val targets: List<Triple<String, String, AppEntry?>> = when (target) {
        is EditTarget.Home -> state.homeTiles.filter { it.id != target.tile.id }.map { tile ->
            when (tile) {
                is HomeTile.App -> Triple(tile.id, tile.entry.label, tile.entry)
                is HomeTile.FolderTile -> Triple(tile.id, OverlayText.folderHeader(tile.folder.folder.name), null)
                is HomeTile.Widget -> Triple(tile.id, OverlayText.WIDGET, null)
            }
        }
        is EditTarget.Dock -> (0 until Organization.DOCK_SLOTS).filter { it != target.slot }.map { slot ->
            val entry = state.dock.getOrNull(slot)
            Triple("$DOCK_PREFIX$slot", "${OverlayText.dockSlot(slot)}: ${entry?.label ?: OverlayText.EMPTY_SLOT}", entry)
        }
        is EditTarget.FolderApp -> target.folder.apps.filter { it.key != target.entry.key }.map {
            Triple(it.key.encode(), it.label, it)
        }
    }
    rememberScreenFocus(targets.firstOrNull()?.let { "edit:target:${it.first}" } ?: "edit:target:cancel")
    ModalPanel(
        title = title,
        idPrefix = "edit:target",
        onDismiss = onDismiss,
        interceptBack = true,
        actions = { PlutoButton("edit:target:cancel", KitText.CANCEL, onDismiss, style = ButtonStyle.TEXT) },
    ) {
        targets.forEach { (id, label, app) ->
            ActionRow(
                id = "edit:target:$id",
                label = label,
                leading = app?.let { { AppIcon(it, 32.dp) } },
                icon = if (app == null) Icons.Rounded.Folder else null,
                onClick = { onPick(id) },
            )
        }
    }
}

/**
 * Picker over every visible app (filling a dock slot, adding a favourite). Lazy because
 * libraries can be large. [isCurrent] apps are marked (selected row; [currentLabel] as text).
 */
@Composable
private fun AppPicker(
    state: LauncherUiState,
    title: String,
    isCurrent: (AppKey) -> Boolean,
    currentLabel: String?,
    onPick: (AppKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val apps = state.apps
    rememberScreenFocus(apps.firstOrNull()?.let { "edit:pick:${it.key.encode()}" } ?: "edit:pick:cancel")
    ModalPanel(
        title = title,
        idPrefix = "edit:pick",
        onDismiss = onDismiss,
        interceptBack = true,
        scrollable = false,
        actions = { PlutoButton("edit:pick:cancel", KitText.CANCEL, onDismiss, style = ButtonStyle.TEXT) },
    ) {
        if (apps.isEmpty()) {
            BodyText(OverlayText.NO_APPS)
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).padding(horizontal = 12.dp)) {
                items(apps, key = { it.key.encode() }) { app ->
                    val slot = state.dock.indexOfFirst { it?.key == app.key }
                    val current = isCurrent(app.key)
                    ActionRow(
                        id = "edit:pick:${app.key.encode()}",
                        label = app.label,
                        supporting = listOfNotNull(
                            currentLabel?.takeIf { current },
                            if (slot >= 0) "${OverlayText.DOCK_SECTION} · ${OverlayText.dockSlot(slot)}" else null,
                        ).joinToString(" · ").ifEmpty { null },
                        leading = { AppIcon(app, 32.dp) },
                        selected = current,
                        onClick = { onPick(app.key) },
                    )
                }
            }
        }
    }
}
