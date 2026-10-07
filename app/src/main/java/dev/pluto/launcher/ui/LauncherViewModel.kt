package dev.pluto.launcher.ui

import android.graphics.Rect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.pluto.launcher.AppContainer
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.ReorderOp
import kotlinx.coroutines.flow.StateFlow

/**
 * Single shared state model for every layout. Layout composables are pure views of
 * [state]; all mutations go through these methods. Organisation writes persist
 * immediately; session state is mirrored into [savedState] for process death.
 */
class LauncherViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<LauncherUiState> = TODO()

    // --- Environment --------------------------------------------------------
    /** Called whenever the window size changes (rotation, multi-window). Recomputes mode. */
    fun onWindowSizeChanged(widthDp: Int, heightDp: Int): Unit = TODO()
    /** Activity resumed: refresh catalog, controllers, default-home status. */
    fun onResume(isDefaultHome: Boolean): Unit = TODO()
    /** Home button pressed while launcher is already in front: close all layers and clear search. */
    fun onHomePressed(): Unit = TODO()

    // --- Navigation ---------------------------------------------------------
    fun openLayer(layer: Layer): Unit = TODO()
    /** Closes the top layer (search counts as a layer of the drawer: clears/closes search first). Returns false when already at home. */
    fun back(): Boolean = TODO()
    fun closeAllLayers(): Unit = TODO()
    fun openDrawer(withSearch: Boolean = false): Unit = TODO()
    fun setSearchActive(active: Boolean): Unit = TODO()
    fun setSearchText(text: String): Unit = TODO()
    fun clearSearch(): Unit = TODO()
    /** null selects the ALL category. */
    fun selectCategory(categoryId: Long?): Unit = TODO()
    fun nextCategory(): Unit = TODO()
    fun previousCategory(): Unit = TODO()

    // --- Selection / focus memory ------------------------------------------
    fun onAppSelected(key: AppKey?): Unit = TODO()
    fun onControlFocused(controlId: String?): Unit = TODO()
    fun onScrollAnchor(surface: String, itemId: String?): Unit = TODO()
    fun setEditSelection(id: String?): Unit = TODO()

    // --- Launching ----------------------------------------------------------
    fun launch(key: AppKey, sourceBounds: Rect? = null): Unit = TODO()
    fun openAppInfo(key: AppKey): Unit = TODO()
    fun requestUninstall(key: AppKey): Unit = TODO()
    fun dismissMessage(id: Long): Unit = TODO()

    // --- Organisation -------------------------------------------------------
    fun pin(key: AppKey): Unit = TODO()
    fun unpin(key: AppKey): Unit = TODO()
    fun moveHomeItem(item: HomeItem, op: ReorderOp<HomeItem>): Unit = TODO()
    /** Puts [key] in [slot] (0..4); an app already in another slot moves. null clears the slot. */
    fun setDockSlot(slot: Int, key: AppKey?): Unit = TODO()
    /** Adds to the first free dock slot; returns false when the dock is full. */
    fun addToDock(key: AppKey): Boolean = TODO()
    fun removeFromDock(key: AppKey): Unit = TODO()
    fun moveDockSlot(slot: Int, op: ReorderOp<Int>): Unit = TODO()
    fun createFolder(name: String, apps: List<AppKey>): Unit = TODO()
    fun renameFolder(id: Long, name: String): Unit = TODO()
    fun deleteFolder(id: Long): Unit = TODO()
    fun moveAppToFolder(key: AppKey, folderId: Long): Unit = TODO()
    fun removeAppFromFolder(key: AppKey): Unit = TODO()
    fun moveFolderApp(folderId: Long, key: AppKey, op: ReorderOp<AppKey>): Unit = TODO()
    fun addCategory(name: String): Unit = TODO()
    fun renameCategory(id: Long, name: String): Unit = TODO()
    fun deleteCategory(id: Long): Unit = TODO()
    fun moveCategory(id: Long, op: ReorderOp<Long>): Unit = TODO()
    fun setCategoryMembership(categoryId: Long, key: AppKey, member: Boolean): Unit = TODO()
    fun hide(key: AppKey): Unit = TODO()
    fun unhide(key: AppKey): Unit = TODO()

    // --- History & settings -------------------------------------------------
    fun clearHistory(): Unit = TODO()
    /** Disabling also deletes stored history and stops collection. */
    fun setHistoryEnabled(enabled: Boolean): Unit = TODO()
    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings): Unit = TODO()
    fun completeOnboarding(): Unit = TODO()
}
