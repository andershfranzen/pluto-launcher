package dev.pluto.launcher.data

import dev.pluto.launcher.data.db.CategoryAppEntity
import dev.pluto.launcher.data.db.CategoryEntity
import dev.pluto.launcher.data.db.DockSlotEntity
import dev.pluto.launcher.data.db.FolderAppEntity
import dev.pluto.launcher.data.db.FolderEntity
import dev.pluto.launcher.data.db.HiddenAppEntity
import dev.pluto.launcher.data.db.HomeItemEntity
import dev.pluto.launcher.model.HomeWidget
import dev.pluto.launcher.data.db.HomeWidgetEntity
import dev.pluto.launcher.data.db.LauncherDao
import dev.pluto.launcher.data.db.RecentLaunchEntity
import dev.pluto.launcher.domain.RecentHistory
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.BuiltInCategory
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.model.Folder
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.model.RecentLaunch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * All user organisation, persisted in Room. Every mutating call writes immediately
 * (inside a DAO transaction where it touches several tables).
 *
 * Invariants maintained here:
 * - An app appears on the home grid at most once: either directly (HomeItem.App) or inside
 *   exactly one folder (a folder is itself a HomeItem.FolderRef).
 * - The dock is independent of the home grid; max 5 slots, an app occupies at most one slot.
 * - Built-in categories ALL and GAMES exist after [ensureDefaults] (older installs may also
 *   have TOOLS); ALL cannot be deleted.
 *
 * Edits that span several DAO calls are serialised by [writeLock] so concurrent
 * read-modify-write sequences never interleave. The observed [organization] is also
 * repaired on read, so a row left over from an interrupted edit can never break the UI.
 */
class OrganizationRepository(private val dao: LauncherDao) {
    private val writeLock = Mutex()

    val organization: Flow<Organization> = combine(
        dao.observeHomeItems(),
        dao.observeDock(),
        dao.observeFolders(),
        dao.observeFolderApps(),
        dao.observeCategories(),
    ) { home, dock, folders, folderApps, categories ->
        StructureRows(home, dock, folders, folderApps, categories)
    }.combine(
        combine(dao.observeCategoryApps(), dao.observeHidden(), dao.observeRecents(), dao.observeWidgets()) { members, hidden, recents, widgets ->
            MembershipRows(members, hidden, recents, widgets)
        },
    ) { structure, membership -> buildOrganization(structure, membership) }
        .distinctUntilChanged()

    /**
     * Seeds the default categories ("All apps" and "Games") when the table is empty. Users add
     * as many of their own as they like; TOOLS is no longer seeded but stays a known built-in
     * for installs that already have it.
     */
    suspend fun ensureDefaults(): Unit = writeLock.withLock {
        val existing = dao.categories()
        if (existing.isNotEmpty()) {
            // ALL is the virtual "every app" view and must always exist; restore it first if missing.
            if (existing.none { it.builtIn == BuiltInCategory.ALL.name }) {
                val id = dao.insertCategory(
                    CategoryEntity(name = DEFAULT_NAMES.getValue(BuiltInCategory.ALL), builtIn = BuiltInCategory.ALL.name, position = 0),
                )
                dao.reorderCategories(listOf(id) + existing.map { it.id })
            }
            return@withLock
        }
        dao.insertCategories(
            SEEDED.mapIndexed { i, b -> CategoryEntity(name = DEFAULT_NAMES.getValue(b), builtIn = b.name, position = i) },
        )
    }

    /**
     * Writes the first-run default layout, but only while the user has no layout at all (no
     * favourites, dock entries or folders), in one transaction. Returns true when it wrote.
     */
    suspend fun seedDefaultLayout(dock: List<AppKey?>, home: List<AppKey>): Boolean = writeLock.withLock {
        if (dao.layoutRowCount() > 0) return@withLock false
        val padded = List(Organization.DOCK_SLOTS) { i -> dock.getOrNull(i)?.encode() }
        val homeIds = home.distinct().map { HomeItem.App(it).id }
        if (padded.all { it == null } && homeIds.isEmpty()) return@withLock false
        dao.seedLayout(padded, homeIds)
        true
    }

    /**
     * Reorders the home grid. A reorder never removes anything: a current row missing from
     * [items] (added by a concurrent edit after the caller read the grid) is kept at the end.
     */
    suspend fun setHomeOrder(items: List<HomeItem>): Unit = writeLock.withLock {
        // Apps that live in a folder cannot also sit on the grid directly.
        val ids = items.filter { item -> item !is HomeItem.App || dao.folderAppFor(item.key.encode()) == null }
            .map { it.id }
        val supplied = ids.toHashSet()
        val kept = dao.homeItems().map { it.itemId }.filterNot { it in supplied }
        dao.replaceHomeItems(ids + kept)
    }

    /** Appends to the home grid unless already there directly or via a folder. */
    suspend fun pin(key: AppKey): Unit = writeLock.withLock {
        val id = HomeItem.App(key).id
        val home = dao.homeItems()
        if (home.any { it.itemId == id } || dao.folderAppFor(key.encode()) != null) return@withLock
        dao.insertHomeItems(listOf(HomeItemEntity(id, (home.maxOfOrNull { it.position } ?: -1) + 1)))
    }

    /** Removes from the home grid (direct item or folder membership). Dock is untouched. */
    suspend fun unpin(key: AppKey): Unit = writeLock.withLock {
        dao.deleteHomeItem(HomeItem.App(key).id)
        dao.removeFromFolders(key.encode())
    }

    /** [slots] must have Organization.DOCK_SLOTS entries. */
    suspend fun setDock(slots: List<AppKey?>): Unit = writeLock.withLock {
        val padded = List(Organization.DOCK_SLOTS) { i -> slots.getOrNull(i)?.encode() }
        dao.replaceDock(padded)
    }

    /**
     * Creates a folder holding [apps] (removed from wherever they were on home), placed at [homeIndex] or appended.
     * [homeIndex] indexes the grid after [apps] have been taken out of it.
     */
    suspend fun createFolder(name: String, apps: List<AppKey>, homeIndex: Int? = null): Long = writeLock.withLock {
        val folderId = dao.insertFolder(FolderEntity(name = name))
        val appIds = apps.mapTo(HashSet()) { HomeItem.App(it).id }
        val home = dao.homeItems().map { it.itemId }.filterNot { it in appIds }.toMutableList()
        val folderRef = HomeItem.FolderRef(folderId).id
        if (homeIndex == null) home.add(folderRef) else home.add(homeIndex.coerceIn(0, home.size), folderRef)
        dao.replaceHomeItems(home)
        // REPLACE on the appKey primary key moves apps out of any other folder.
        dao.replaceFolderApps(folderId, apps.map { it.encode() })
        folderId
    }

    /** Adds a bound widget to the end of the home grid. */
    suspend fun addWidget(appWidgetId: Int, provider: String, rows: Int): Unit = writeLock.withLock {
        dao.insertWidget(HomeWidgetEntity(appWidgetId, provider, rows.coerceIn(HomeWidget.MIN_ROWS, HomeWidget.MAX_ROWS)))
        val home = dao.homeItems()
        dao.insertHomeItems(listOf(HomeItemEntity(HomeItem.Widget(appWidgetId).id, (home.maxOfOrNull { it.position } ?: -1) + 1)))
    }

    /** Takes a widget off the grid (the caller frees its host id). */
    suspend fun removeWidget(appWidgetId: Int): Unit = writeLock.withLock {
        dao.deleteHomeItemById(HomeItem.Widget(appWidgetId).id)
        dao.deleteWidget(appWidgetId)
    }

    suspend fun setWidgetRows(appWidgetId: Int, rows: Int): Unit = writeLock.withLock {
        dao.setWidgetRows(appWidgetId, rows.coerceIn(HomeWidget.MIN_ROWS, HomeWidget.MAX_ROWS))
    }

    suspend fun renameFolder(id: Long, name: String): Unit = writeLock.withLock {
        dao.renameFolder(id, name)
    }

    /** Deletes the folder only; its apps return to the home grid where the folder was. Never uninstalls. */
    suspend fun deleteFolder(id: Long): Unit = writeLock.withLock {
        val released = dao.folderApps(id).map { it.appKey }
        val home = dao.homeItems().map { it.itemId }
        val ref = HomeItem.FolderRef(id).id
        val releasedIds = released.mapNotNull { AppKey.decode(it)?.let { key -> HomeItem.App(key).id } }.filterNot { it in home }
        val index = home.indexOf(ref)
        val next = if (index >= 0) {
            home.subList(0, index) + releasedIds + home.subList(index + 1, home.size)
        } else {
            home + releasedIds
        }
        // Cascade removes the folder_app rows.
        dao.deleteFolder(id)
        dao.replaceHomeItems(next)
    }

    /** Moves [key] into [folderId] (out of the home grid and any other folder). */
    suspend fun moveAppToFolder(key: AppKey, folderId: Long): Unit = writeLock.withLock {
        if (dao.observeFolders().first().none { it.id == folderId }) return@withLock
        val encoded = key.encode()
        val members = dao.folderApps(folderId)
        if (members.any { it.appKey == encoded }) return@withLock
        dao.deleteHomeItem(HomeItem.App(key).id)
        dao.insertFolderApps(listOf(FolderAppEntity(encoded, folderId, (members.maxOfOrNull { it.position } ?: -1) + 1)))
    }

    /** Moves [key] out of its folder onto the home grid right after the folder. */
    suspend fun removeAppFromFolder(key: AppKey): Unit = writeLock.withLock {
        val encoded = key.encode()
        val membership = dao.folderAppFor(encoded) ?: return@withLock
        dao.removeFromFolders(encoded)
        val appId = HomeItem.App(key).id
        val home = dao.homeItems().map { it.itemId }.filterNot { it == appId }.toMutableList()
        val folderIndex = home.indexOf(HomeItem.FolderRef(membership.folderId).id)
        if (folderIndex >= 0) home.add(folderIndex + 1, appId) else home.add(appId)
        dao.replaceHomeItems(home)
    }

    suspend fun setFolderOrder(folderId: Long, keys: List<AppKey>): Unit = writeLock.withLock {
        if (dao.observeFolders().first().none { it.id == folderId }) return@withLock
        // Anything now in the folder leaves the grid so it is never shown twice.
        keys.forEach { dao.deleteHomeItem(HomeItem.App(it).id) }
        dao.replaceFolderApps(folderId, keys.map { it.encode() })
    }

    suspend fun addCategory(name: String): Long = writeLock.withLock {
        val position = (dao.categories().maxOfOrNull { it.position } ?: -1) + 1
        dao.insertCategory(CategoryEntity(name = name, builtIn = null, position = position))
    }

    suspend fun renameCategory(id: Long, name: String): Unit = writeLock.withLock {
        dao.renameCategory(id, name)
    }

    /** No-op for the ALL category. */
    suspend fun deleteCategory(id: Long): Unit = writeLock.withLock {
        // The DAO query itself refuses to delete ALL; membership rows cascade.
        dao.deleteCategory(id)
    }

    suspend fun reorderCategories(orderedIds: List<Long>): Unit = writeLock.withLock {
        val existing = dao.categories().map { it.id }
        // Keep any category the caller did not mention, in its current relative order.
        val ordered = orderedIds.distinct().filter { it in existing } + existing.filterNot { it in orderedIds }
        dao.reorderCategories(ordered)
    }

    suspend fun setCategoryMembership(categoryId: Long, key: AppKey, member: Boolean): Unit = writeLock.withLock {
        val category = dao.categories().firstOrNull { it.id == categoryId } ?: return@withLock
        // ALL is virtual: every visible app belongs to it implicitly.
        if (category.builtIn == BuiltInCategory.ALL.name) return@withLock
        if (member) {
            dao.addCategoryApp(CategoryAppEntity(categoryId, key.encode()))
        } else {
            dao.removeCategoryApp(categoryId, key.encode())
        }
    }

    suspend fun hide(key: AppKey): Unit = writeLock.withLock {
        dao.hide(HiddenAppEntity(key.encode()))
    }

    suspend fun unhide(key: AppKey): Unit = writeLock.withLock {
        dao.unhide(key.encode())
    }

    /** Records a launch from this launcher, keeping the 12 most recent distinct apps. */
    suspend fun recordLaunch(key: AppKey, nowMillis: Long): Unit = writeLock.withLock {
        // Guarantee move-to-front even if the clock went backwards since the last launch.
        val newest = dao.recents().firstOrNull()?.launchedAt ?: Long.MIN_VALUE
        dao.recordLaunch(key.encode(), maxOf(nowMillis, newest + 1), RecentHistory.MAX_ENTRIES)
    }

    suspend fun clearRecents(): Unit = writeLock.withLock {
        dao.clearRecents()
    }

    /** Drops every reference to [key] (package fully uninstalled). */
    suspend fun forgetApp(key: AppKey): Unit = writeLock.withLock {
        dao.forgetApp(key.encode())
    }

    private class StructureRows(
        val home: List<HomeItemEntity>,
        val dock: List<DockSlotEntity>,
        val folders: List<FolderEntity>,
        val folderApps: List<FolderAppEntity>,
        val categories: List<CategoryEntity>,
    )

    private class MembershipRows(
        val members: List<CategoryAppEntity>,
        val hidden: List<HiddenAppEntity>,
        val recents: List<RecentLaunchEntity>,
        val widgets: List<HomeWidgetEntity>,
    )

    companion object {
        /** Built-in categories a fresh install starts with, in order. */
        val SEEDED = listOf(BuiltInCategory.ALL, BuiltInCategory.GAMES)

        val DEFAULT_NAMES: Map<BuiltInCategory, String> = mapOf(
            BuiltInCategory.ALL to "All apps",
            BuiltInCategory.GAMES to "Games",
            BuiltInCategory.TOOLS to "Tools",
        )

        /** Decodes rows into the model, dropping anything undecodable and repairing invariants. */
        private fun buildOrganization(s: StructureRows, m: MembershipRows): Organization {
            val appsByFolder = s.folderApps.groupBy({ it.folderId }) { AppKey.decode(it.appKey) }
            val folders = s.folders.associate { f ->
                f.id to Folder(f.id, f.name, appsByFolder[f.id].orEmpty().filterNotNull().distinct())
            }
            val inFolders = folders.values.flatMapTo(HashSet()) { it.apps }

            val widgets = m.widgets.associate { it.appWidgetId to HomeWidget(it.appWidgetId, it.provider, it.rows) }
            val home = s.home.mapNotNull { HomeItem.decode(it.itemId) }
                .filter { item ->
                    when (item) {
                        is HomeItem.App -> item.key !in inFolders
                        is HomeItem.FolderRef -> item.folderId in folders
                        is HomeItem.Widget -> item.appWidgetId in widgets
                    }
                }
                .distinct()
            // A folder must always be reachable from the grid; surface any orphan at the end.
            val placedFolders = home.filterIsInstance<HomeItem.FolderRef>().mapTo(HashSet()) { it.folderId }
            val homeItems = home + folders.keys.filterNot { it in placedFolders }.sorted().map(HomeItem::FolderRef)

            val dock = arrayOfNulls<AppKey>(Organization.DOCK_SLOTS)
            for (row in s.dock) {
                val key = AppKey.decode(row.appKey) ?: continue
                if (row.slot in dock.indices && key !in dock) dock[row.slot] = key
            }

            val categories = s.categories.map { c ->
                Category(
                    id = c.id,
                    name = c.name,
                    builtIn = BuiltInCategory.entries.firstOrNull { it.name == c.builtIn },
                    position = c.position,
                )
            }
            val categoryIds = categories.mapTo(HashSet()) { it.id }
            val members = m.members.filter { it.categoryId in categoryIds }
                .groupBy({ it.categoryId }) { AppKey.decode(it.appKey) }
                .mapValues { (_, keys) -> keys.filterNotNull().toSet() }

            return Organization(
                homeItems = homeItems,
                dock = dock.toList(),
                folders = folders,
                categories = categories,
                categoryMembers = members,
                hidden = m.hidden.mapNotNullTo(HashSet()) { AppKey.decode(it.appKey) },
                recents = m.recents.mapNotNull { r -> AppKey.decode(r.appKey)?.let { RecentLaunch(it, r.launchedAt) } }
                    .distinctBy { it.key }
                    .take(RecentHistory.MAX_ENTRIES),
                widgets = widgets,
            )
        }
    }
}
