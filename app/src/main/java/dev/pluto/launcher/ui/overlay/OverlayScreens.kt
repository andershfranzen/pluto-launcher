package dev.pluto.launcher.ui.overlay

import androidx.compose.runtime.Composable
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel

/*
 * Entry points for modal layers rendered by LauncherRoot when the matching Layer is on top.
 * Each is a full-screen or dialog-style surface, fully controller-navigable, with an
 * explicit initial focus, and closes via vm.back().
 */

/** Launch, Pin/Unpin, Add to dock/Remove from dock, Categories…, Move to folder…, Hide, App info, Uninstall (system confirmation). */
@Composable
fun AppActionsSheet(key: AppKey, state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()

/** Checkbox list of non-ALL categories for [key]. */
@Composable
fun CategoryMembershipDialog(key: AppKey, state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()

/** Existing folders + "New folder…" (name entry) + "Remove from folder". */
@Composable
fun MoveToFolderDialog(key: AppKey, state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()

/**
 * Edit mode for home favourites, dock and folders: pick an item, then Move up / down /
 * before / after (target picker), Unpin, Rename/Delete folder, New folder. No drag-and-drop needed.
 */
@Composable
fun EditScreen(state: LauncherUiState, vm: LauncherViewModel): Unit = TODO()
