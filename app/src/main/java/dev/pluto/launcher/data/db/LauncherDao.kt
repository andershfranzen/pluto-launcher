package dev.pluto.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherDao {
    // --- Observation -------------------------------------------------------

    @Query("SELECT * FROM home_item ORDER BY position")
    fun observeHomeItems(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM dock_slot ORDER BY slot")
    fun observeDock(): Flow<List<DockSlotEntity>>

    @Query("SELECT * FROM folder")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folder_app ORDER BY folderId, position")
    fun observeFolderApps(): Flow<List<FolderAppEntity>>

    @Query("SELECT * FROM category ORDER BY position")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category_app")
    fun observeCategoryApps(): Flow<List<CategoryAppEntity>>

    @Query("SELECT * FROM hidden_app")
    fun observeHidden(): Flow<List<HiddenAppEntity>>

    @Query("SELECT * FROM recent_launch ORDER BY launchedAt DESC")
    fun observeRecents(): Flow<List<RecentLaunchEntity>>

    // --- One-shot reads (for read-modify-write inside transactions) --------

    @Query("SELECT * FROM home_item ORDER BY position")
    suspend fun homeItems(): List<HomeItemEntity>

    @Query("SELECT * FROM dock_slot ORDER BY slot")
    suspend fun dock(): List<DockSlotEntity>

    @Query("SELECT * FROM folder_app WHERE folderId = :folderId ORDER BY position")
    suspend fun folderApps(folderId: Long): List<FolderAppEntity>

    @Query("SELECT * FROM folder_app WHERE appKey = :appKey")
    suspend fun folderAppFor(appKey: String): FolderAppEntity?

    @Query("SELECT * FROM category ORDER BY position")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM recent_launch ORDER BY launchedAt DESC")
    suspend fun recents(): List<RecentLaunchEntity>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun categoryCount(): Int

    // --- Home grid ----------------------------------------------------------

    @Query("DELETE FROM home_item")
    suspend fun clearHomeItems()

    @Insert
    suspend fun insertHomeItems(items: List<HomeItemEntity>)

    /** Replaces the whole favourites order atomically. */
    @Transaction
    suspend fun replaceHomeItems(orderedIds: List<String>) {
        clearHomeItems()
        insertHomeItems(orderedIds.distinct().mapIndexed { i, id -> HomeItemEntity(id, i) })
    }

    // --- Dock ---------------------------------------------------------------

    @Query("DELETE FROM dock_slot")
    suspend fun clearDock()

    @Insert
    suspend fun insertDock(slots: List<DockSlotEntity>)

    /** [slots] has one entry per slot index; null leaves the slot empty. Duplicate apps keep the first slot. */
    @Transaction
    suspend fun replaceDock(slots: List<String?>) {
        clearDock()
        val seen = HashSet<String>()
        insertDock(slots.mapIndexedNotNull { i, key -> key?.takeIf(seen::add)?.let { DockSlotEntity(i, it) } })
    }

    // --- Folders ------------------------------------------------------------

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folder SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM folder WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("DELETE FROM folder_app WHERE appKey = :appKey")
    suspend fun removeFromFolders(appKey: String)

    @Query("DELETE FROM folder_app WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderApps(apps: List<FolderAppEntity>)

    /** Replaces a folder's membership and order. Apps are moved out of any other folder. */
    @Transaction
    suspend fun replaceFolderApps(folderId: Long, orderedKeys: List<String>) {
        clearFolder(folderId)
        insertFolderApps(orderedKeys.distinct().mapIndexed { i, k -> FolderAppEntity(k, folderId, i) })
    }

    // --- Categories ---------------------------------------------------------

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("UPDATE category SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("DELETE FROM category WHERE id = :id AND (builtIn IS NULL OR builtIn != 'ALL')")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE category SET position = :position WHERE id = :id")
    suspend fun setCategoryPosition(id: Long, position: Int)

    @Transaction
    suspend fun reorderCategories(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { i, id -> setCategoryPosition(id, i) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCategoryApp(entry: CategoryAppEntity)

    @Query("DELETE FROM category_app WHERE categoryId = :categoryId AND appKey = :appKey")
    suspend fun removeCategoryApp(categoryId: Long, appKey: String)

    // --- Hidden -------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(entry: HiddenAppEntity)

    @Query("DELETE FROM hidden_app WHERE appKey = :appKey")
    suspend fun unhide(appKey: String)

    // --- Recent launches ----------------------------------------------------

    @Upsert
    suspend fun upsertRecent(entry: RecentLaunchEntity)

    @Query("DELETE FROM recent_launch WHERE appKey NOT IN (SELECT appKey FROM recent_launch ORDER BY launchedAt DESC LIMIT :keep)")
    suspend fun trimRecents(keep: Int)

    @Transaction
    suspend fun recordLaunch(appKey: String, launchedAt: Long, keep: Int) {
        upsertRecent(RecentLaunchEntity(appKey, launchedAt))
        trimRecents(keep)
    }

    @Query("DELETE FROM recent_launch")
    suspend fun clearRecents()

    // --- Uninstall cleanup --------------------------------------------------

    @Query("DELETE FROM home_item WHERE itemId = :homeItemId")
    suspend fun deleteHomeItem(homeItemId: String)

    @Query("DELETE FROM dock_slot WHERE appKey = :appKey")
    suspend fun deleteDockApp(appKey: String)

    @Query("DELETE FROM category_app WHERE appKey = :appKey")
    suspend fun deleteCategoryApp(appKey: String)

    @Query("DELETE FROM recent_launch WHERE appKey = :appKey")
    suspend fun deleteRecent(appKey: String)

    /**
     * Removes every reference to an app whose package was fully uninstalled.
     * Hidden state is dropped too so a reinstall starts visible.
     */
    @Transaction
    suspend fun forgetApp(appKey: String) {
        deleteHomeItem("app:$appKey")
        deleteDockApp(appKey)
        removeFromFolders(appKey)
        deleteCategoryApp(appKey)
        unhide(appKey)
        deleteRecent(appKey)
    }
}
