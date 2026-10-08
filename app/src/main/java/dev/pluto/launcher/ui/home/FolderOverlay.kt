package dev.pluto.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppTile
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.components.PlutoPanel
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.controllerFocusTarget

private const val FV_CLOSE = "folderview:close"
private const val FV_RENAME = "folderview:rename"
private const val FV_EDIT = "folderview:edit"
private const val FV_NAME_FIELD = "folderview:name"
private const val FV_SAVE = "folderview:save"
private const val FV_CANCEL = "folderview:cancel"

private fun folderAppId(folderId: Long, key: AppKey) = "folderview:$folderId:${key.encode()}"

/**
 * An open folder (layer FolderLayer): its name, Rename and Edit controls, a close button
 * and the grid of member apps. Rename happens in place; the draft survives rotation.
 * If the folder disappears (deleted elsewhere) the layer closes itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderOverlay(state: LauncherUiState, vm: LauncherViewModel, folderId: Long) {
    val folderUi = state.folders[folderId]
    if (folderUi == null) {
        LaunchedEffect(folderId) { vm.back() }
        return
    }
    val focus = LocalControllerFocus.current
    val keyboard = LocalSoftwareKeyboardController.current
    val iconSize = state.iconSize()
    val apps = folderUi.apps
    var renaming by rememberSaveable(folderId) { mutableStateOf(false) }
    var draft by rememberSaveable(folderId) { mutableStateOf(folderUi.folder.name) }

    val defaultId = apps.firstOrNull()?.let { folderAppId(folderId, it.key) } ?: FV_CLOSE
    SideEffect { focus.setDefaultFocus(if (renaming) FV_NAME_FIELD else defaultId) }
    LaunchedEffect(folderId) {
        withFrameNanos { }
        if (focus.inputMode == InputMode.CONTROLLER) focus.requestFocusWhenReady(defaultId)
    }
    LaunchedEffect(renaming) {
        if (renaming) {
            if (focus.requestFocusWhenReady(FV_NAME_FIELD)) keyboard?.show()
        }
    }

    fun cancelRename() {
        draft = folderUi.folder.name
        keyboard?.hide()
        renaming = false
        focus.requestFocus(FV_RENAME)
    }

    // Back (system or controller) cancels an in-place rename before it closes the folder.
    BackHandler(enabled = renaming) { cancelRename() }
    TrackFocusOrder("folder:$folderId", apps.map { folderAppId(folderId, it.key) })

    fun commitRename() {
        val name = draft.trim()
        if (name.isNotEmpty() && name != folderUi.folder.name) vm.renameFolder(folderId, name)
        keyboard?.hide()
        renaming = false
        focus.requestFocus(FV_RENAME)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        PlutoPanel(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight),
        ) {
            Column(Modifier.padding(16.dp)) {
                if (renaming) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            label = { Text("Folder name") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { commitRename() }),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .controllerFocusTarget(FV_NAME_FIELD, onActivate = { keyboard?.show() }),
                        )
                        PlutoIconButton(FV_SAVE, Icons.Outlined.Check, "Save name", { commitRename() })
                        PlutoIconButton(FV_CANCEL, Icons.Outlined.Close, "Cancel rename", { cancelRename() })
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            folderUi.folder.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                .semantics { heading() },
                        )
                        PlutoIconButton(FV_CLOSE, Icons.Outlined.Close, "Close folder", { vm.back() })
                    }
                    FlowRow(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlutoTextButton(FV_RENAME, "Rename", {
                            draft = folderUi.folder.name
                            renaming = true
                        }, icon = Icons.Outlined.DriveFileRenameOutline)
                        PlutoTextButton(FV_EDIT, "Edit", {
                            vm.setEditSelection("folder:$folderId")
                            vm.openLayer(Layer.Edit)
                        }, icon = Icons.Outlined.Edit)
                    }
                }

                if (apps.isEmpty()) {
                    Text(
                        "This folder is empty. In All apps, tap Actions, choose an app and pick Move to folder….",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = iconSize + 36.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                    ) {
                        items(apps, key = { it.key.encode() }) { entry ->
                            val id = folderAppId(folderId, entry.key)
                            AppTile(
                                entry = entry,
                                iconSize = iconSize,
                                onLaunch = { vm.launch(entry.key) },
                                onActions = { vm.openLayer(Layer.AppActions(entry.key)) },
                                focusId = id,
                                onFocused = {
                                    vm.onAppSelected(entry.key)
                                    vm.onControlFocused(id)
                                },
                            )
                        }
                    }
                }

                if (showControllerHints(state, focus)) {
                    ButtonLegend(
                        state.activeMapping(),
                        Legends.Folder,
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
