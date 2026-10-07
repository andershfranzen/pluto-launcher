package dev.pluto.launcher.data

import dev.pluto.launcher.data.db.LauncherDao
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization
import kotlinx.coroutines.flow.Flow

/**
 * All user organisation, persisted in Room. Every mutating call writes immediately
 * (inside a DAO transaction where it touches several tables).
 *
 * Invariants maintained here:
 * - An app appears on the home grid at most once: either directly (HomeItem.App) or inside
 *   exactly one folder (a folder is itself a HomeItem.FolderRef).
 * - The dock is independent of the home grid; max 5 slots, an app occupies at most one slot.
 * - Built-in categories ALL, GAMES and TOOLS exist after [ensureDefaults]; ALL cannot be deleted.
 */
class OrganizationRepository(private val dao: LauncherDao) {
    val organization: Flow<Organization> = TODO()

    /** Seeds built-in categories (names "All apps", "Games", "Tools") when the table is empty. */
    suspend fun ensureDefaults(): Unit = TODO()

    suspend fun setHomeOrder(items: List<HomeItem>): Unit = TODO()
    /** Appends to the home grid unless already there directly or via a folder. */
    suspend fun pin(key: AppKey): Unit = TODO()
    /** Removes from the home grid (direct item or folder membership). Dock is untouched. */
    suspend fun unpin(key: AppKey): Unit = TODO()

    /** [slots] must have Organization.DOCK_SLOTS entries. */
    suspend fun setDock(slots: List<AppKey?>): Unit = TODO()

    /** Creates a folder holding [apps] (removed from wherever they were on home), placed at [homeIndex] or appended. */
    suspend fun createFolder(name: String, apps: List<AppKey>, homeIndex: Int? = null): Long = TODO()
    suspend fun renameFolder(id: Long, name: String): Unit = TODO()
    /** Deletes the folder only; its apps return to the home grid where the folder was. Never uninstalls. */
    suspend fun deleteFolder(id: Long): Unit = TODO()
    /** Moves [key] into [folderId] (out of the home grid and any other folder). */
    suspend fun moveAppToFolder(key: AppKey, folderId: Long): Unit = TODO()
    /** Moves [key] out of its folder onto the home grid right after the folder. */
    suspend fun removeAppFromFolder(key: AppKey): Unit = TODO()
    suspend fun setFolderOrder(folderId: Long, keys: List<AppKey>): Unit = TODO()

    suspend fun addCategory(name: String): Long = TODO()
    suspend fun renameCategory(id: Long, name: String): Unit = TODO()
    /** No-op for the ALL category. */
    suspend fun deleteCategory(id: Long): Unit = TODO()
    suspend fun reorderCategories(orderedIds: List<Long>): Unit = TODO()
    suspend fun setCategoryMembership(categoryId: Long, key: AppKey, member: Boolean): Unit = TODO()

    suspend fun hide(key: AppKey): Unit = TODO()
    suspend fun unhide(key: AppKey): Unit = TODO()

    /** Records a launch from this launcher, keeping the 12 most recent distinct apps. */
    suspend fun recordLaunch(key: AppKey, nowMillis: Long): Unit = TODO()
    suspend fun clearRecents(): Unit = TODO()

    /** Drops every reference to [key] (package fully uninstalled). */
    suspend fun forgetApp(key: AppKey): Unit = TODO()
}
