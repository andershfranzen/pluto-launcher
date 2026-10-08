package dev.pluto.launcher.ui

import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.pluto.launcher.AppContainer
import dev.pluto.launcher.apps.LaunchResult
import dev.pluto.launcher.apps.PackageEvent
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.domain.FocusAnchor
import dev.pluto.launcher.domain.Reorder
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.model.ReorderOp
import dev.pluto.launcher.ui.state.EnvironmentInputs
import dev.pluto.launcher.ui.state.LauncherStateBuilder
import dev.pluto.launcher.ui.state.LibraryInputs
import dev.pluto.launcher.ui.state.SessionSaver
import dev.pluto.launcher.ui.state.WindowSizeDp
import dev.pluto.launcher.ui.state.reorderVisible
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Single shared state model for every layout. Layout composables are pure views of
 * [state]; all mutations go through these methods. Organisation writes persist
 * immediately; session state is mirrored into [savedState] for process death.
 */
class LauncherViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val repository get() = container.organization

    private val session = MutableStateFlow(SessionSaver.restore(savedState))
    private val windowSize = MutableStateFlow<WindowSizeDp?>(null)
    private val message = MutableStateFlow<UserMessage?>(null)
    private val isDefaultHome = MutableStateFlow(false)
    private val messageIds = AtomicLong(0)

    /** Serialises read-modify-write edits (reorders, dock moves) so rapid presses never use stale data. */
    private val editLock = Mutex()
    /** Serialises history writes against disabling history, so nothing is recorded after it is off. */
    private val historyLock = Mutex()
    /** Set once the user finished onboarding in this session, so a stale settings emission cannot re-open it. */
    @Volatile private var onboardingDismissed = false

    /** Shared, single subscription to the persisted organisation (null until first load). */
    private val organizationState: StateFlow<Organization?> =
        repository.organization.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val settingsState: StateFlow<LauncherSettings?> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<LauncherUiState> = run {
        val builder = LauncherStateBuilder()
        val library = combine(
            container.catalog.apps,
            organizationState.filterNotNull(),
            settingsState.filterNotNull(),
        ) { catalog, organization, settings -> LibraryInputs(catalog, organization, settings) }
        val environment = combine(
            container.controllers.controllers,
            container.catalog.otherProfilesPresent,
            windowSize,
            message,
            isDefaultHome,
        ) { controllers, otherProfiles, window, msg, defaultHome ->
            EnvironmentInputs(controllers, otherProfiles, window, msg, defaultHome)
        }
        combine(library, environment, session) { lib, env, sess -> builder.build(lib, env, sess) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())
    }

    init {
        boundary("start app catalog") { container.catalog.start() }
        boundary("start controller monitor") { container.controllers.start() }
        persist { repository.ensureDefaults() }
        viewModelScope.launch { container.catalog.packageEvents.collect(::onPackageEvent) }
        viewModelScope.launch { showOnboardingWhenNeeded() }
        viewModelScope.launch { keepSelectionByIdentity() }
        viewModelScope.launch { pruneStaleLayers() }
    }

    override fun onCleared() {
        boundary("stop app catalog") { container.catalog.stop() }
        boundary("stop controller monitor") { container.controllers.stop() }
    }

    // --- Environment --------------------------------------------------------
    /** Called whenever the window size changes (rotation, multi-window). Recomputes mode. */
    fun onWindowSizeChanged(widthDp: Int, heightDp: Int) {
        windowSize.value = WindowSizeDp(widthDp, heightDp)
    }

    /** Activity resumed: refresh catalog, controllers, default-home status. */
    fun onResume(isDefaultHome: Boolean) {
        this.isDefaultHome.value = isDefaultHome
        boundary("refresh app catalog") { container.catalog.refresh() }
        boundary("refresh controllers") { container.controllers.refresh() }
    }

    /** Home button pressed while launcher is already in front: close all layers and clear search. */
    fun onHomePressed() = closeAllLayers()

    // --- Navigation ---------------------------------------------------------
    fun openLayer(layer: Layer) = updateSession { it.withLayer(layer) }

    /** Closes the top layer (search counts as a layer of the drawer: clears/closes search first). Returns false when already at home. */
    fun back(): Boolean {
        var handled = false
        updateSession { s ->
            val top = s.topLayer
            when {
                top == Layer.Onboarding -> {
                    // Onboarding is only dismissed by completeOnboarding(); keep the launcher where it is.
                    handled = true
                    s
                }
                top == Layer.Drawer && s.searchActive -> {
                    handled = true
                    s.copy(searchActive = false, searchText = "")
                }
                top != null -> {
                    handled = true
                    s.popTop()
                }
                s.searchActive || s.searchText.isNotEmpty() -> {
                    handled = true
                    s.copy(searchActive = false, searchText = "")
                }
                else -> s
            }
        }
        return handled
    }

    fun closeAllLayers() = updateSession { s ->
        s.copy(
            layers = s.layers.filter { it == Layer.Onboarding },
            searchActive = false,
            searchText = "",
            editSelection = null,
        )
    }

    fun openDrawer(withSearch: Boolean = false) = updateSession { s ->
        s.withLayer(Layer.Drawer).copy(searchActive = withSearch || s.searchActive)
    }

    fun setSearchActive(active: Boolean) = updateSession { s ->
        if (active) {
            s.withLayer(Layer.Drawer).copy(searchActive = true)
        } else {
            s.copy(searchActive = false, searchText = "")
        }
    }

    fun setSearchText(text: String) = updateSession { s ->
        if (text.isEmpty()) s.copy(searchText = "") else s.withLayer(Layer.Drawer).copy(searchText = text, searchActive = true)
    }

    fun clearSearch() = updateSession { it.copy(searchText = "") }

    /** null selects the ALL category. */
    fun selectCategory(categoryId: Long?) = updateSession { s ->
        val category = state.value.categories.firstOrNull { it.id == categoryId }
        s.copy(activeCategoryId = category?.takeUnless { it.isAll }?.id)
    }

    fun nextCategory() = stepCategory(+1)
    fun previousCategory() = stepCategory(-1)

    private fun stepCategory(delta: Int) {
        val current = state.value
        val categories = current.categories
        if (categories.isEmpty()) return
        val index = categories.indexOf(current.activeCategory).coerceAtLeast(0)
        selectCategory(categories[Math.floorMod(index + delta, categories.size)].id)
    }

    // --- Selection / focus memory ------------------------------------------
    fun onAppSelected(key: AppKey?) = updateSession { it.copy(selectedApp = key) }
    fun onControlFocused(controlId: String?) = updateSession { it.copy(focusedControlId = controlId) }
    fun onScrollAnchor(surface: String, itemId: String?) = updateSession { s ->
        s.copy(scrollAnchors = if (itemId == null) s.scrollAnchors - surface else s.scrollAnchors + (surface to itemId))
    }
    fun setEditSelection(id: String?) = updateSession { it.copy(editSelection = id) }

    // --- Launching ----------------------------------------------------------
    fun launch(key: AppKey, sourceBounds: Rect? = null) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val result = try {
                container.catalog.launch(key, sourceBounds)
            } catch (e: Exception) {
                Log.w(TAG, "launch failed for $key", e)
                LaunchResult.Failure("That app couldn't be opened.")
            }
            when (result) {
                LaunchResult.Success -> {
                    // A transient app-actions sheet has done its job once the app is open.
                    updateSession { s -> if (s.topLayer == Layer.AppActions(key)) s.popTop() else s }
                    recordLaunch(key)
                }
                is LaunchResult.Failure -> postMessage(result.message)
            }
        }
    }

    fun openAppInfo(key: AppKey) = systemAction("open app info") { container.catalog.openAppInfo(key) }
    fun requestUninstall(key: AppKey) = systemAction("request uninstall") { container.catalog.requestUninstall(key) }

    /** Shows a short, readable message (e.g. guidance after a declined system dialog). */
    fun showMessage(text: String) = postMessage(text)

    fun dismissMessage(id: Long) {
        message.update { current -> if (current?.id == id) null else current }
    }

    // --- Organisation -------------------------------------------------------
    fun pin(key: AppKey) = persist { repository.pin(key) }
    fun unpin(key: AppKey) = persist { repository.unpin(key) }

    fun moveHomeItem(item: HomeItem, op: ReorderOp<HomeItem>) = edit { org ->
        val visible = state.value.homeTiles.mapTo(HashSet()) { it.id }
        val next = reorderVisible(org.homeItems, { it.id in visible }, item, op)
        if (next != org.homeItems) repository.setHomeOrder(next)
    }

    /** Puts [key] in [slot] (0..4); an app already in another slot moves. null clears the slot. */
    fun setDockSlot(slot: Int, key: AppKey?) {
        if (slot !in 0 until Organization.DOCK_SLOTS) return
        edit { org ->
            val dock = org.dock.map { if (key != null && it == key) null else it }.toMutableList()
            dock[slot] = key
            repository.setDock(dock)
        }
    }

    /** Adds to the first free dock slot; returns false when the dock is full. */
    fun addToDock(key: AppKey): Boolean {
        val current = state.value
        if (current.isInDock(key)) return true
        // A slot is free when nothing launchable is shown there (empty, or its app is gone/hidden).
        val slot = current.dock.indexOfFirst { it == null }
        if (slot < 0) {
            postMessage("Dock is full. Remove an app from the dock first.")
            return false
        }
        setDockSlot(slot, key)
        return true
    }

    fun removeFromDock(key: AppKey) = edit { org ->
        if (key in org.dock) repository.setDock(org.dock.map { if (it == key) null else it })
    }

    fun moveDockSlot(slot: Int, op: ReorderOp<Int>) = edit { org ->
        val order = Reorder.apply(org.dock.indices.toList(), slot, op)
        val next = order.map { org.dock[it] }
        if (next != org.dock) repository.setDock(next)
    }

    fun createFolder(name: String, apps: List<AppKey>) = edit { org ->
        val members = apps.distinct()
        // Place the folder where the first of its apps sat on the grid, else append.
        val homeIndex = org.homeItems.indexOfFirst { it is HomeItem.App && it.key in members }.takeIf { it >= 0 }
        repository.createFolder(name.trim().ifBlank { DEFAULT_FOLDER_NAME }, members, homeIndex)
    }

    fun renameFolder(id: Long, name: String) = persist {
        repository.renameFolder(id, name.trim().ifBlank { DEFAULT_FOLDER_NAME })
    }

    fun deleteFolder(id: Long) {
        updateSession { s -> s.copy(layers = s.layers.filterNot { it == Layer.FolderLayer(id) }) }
        persist { repository.deleteFolder(id) }
    }

    fun moveAppToFolder(key: AppKey, folderId: Long) = persist { repository.moveAppToFolder(key, folderId) }
    fun removeAppFromFolder(key: AppKey) = persist { repository.removeAppFromFolder(key) }

    fun moveFolderApp(folderId: Long, key: AppKey, op: ReorderOp<AppKey>) = edit { org ->
        val folder = org.folders[folderId] ?: return@edit
        val visible = state.value.folders[folderId]?.apps.orEmpty().mapTo(HashSet()) { it.key }
        val next = reorderVisible(folder.apps, { it in visible }, key, op)
        if (next != folder.apps) repository.setFolderOrder(folderId, next)
    }

    fun addCategory(name: String) = persist { repository.addCategory(name.trim().ifBlank { DEFAULT_CATEGORY_NAME }) }

    fun renameCategory(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        persist { repository.renameCategory(id, trimmed) }
    }

    fun deleteCategory(id: Long) {
        val category = state.value.categories.firstOrNull { it.id == id }
        if (category?.isAll == true) return
        updateSession { s -> if (s.activeCategoryId == id) s.copy(activeCategoryId = null) else s }
        persist { repository.deleteCategory(id) }
    }

    fun moveCategory(id: Long, op: ReorderOp<Long>) = edit { org ->
        val ids = org.categories.sortedWith(compareBy({ it.position }, { it.id })).map { it.id }
        val next = Reorder.apply(ids, id, op)
        if (next != ids) repository.reorderCategories(next)
    }

    fun setCategoryMembership(categoryId: Long, key: AppKey, member: Boolean) =
        persist { repository.setCategoryMembership(categoryId, key, member) }

    fun hide(key: AppKey) = persist { repository.hide(key) }
    fun unhide(key: AppKey) = persist { repository.unhide(key) }

    // --- History & settings -------------------------------------------------
    fun clearHistory() = persist { historyLock.withLock { repository.clearRecents() } }

    /** Disabling also deletes stored history and stops collection. */
    fun setHistoryEnabled(enabled: Boolean) = persist {
        historyLock.withLock {
            container.settings.update { it.copy(historyEnabled = enabled) }
            if (!enabled) repository.clearRecents()
        }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) = persist {
        container.settings.update(transform)
    }

    fun completeOnboarding() {
        onboardingDismissed = true
        updateSession { s -> s.copy(layers = s.layers.filterNot { it == Layer.Onboarding }) }
        persist { container.settings.update { it.copy(onboardingComplete = true) } }
    }

    // --- Internals ----------------------------------------------------------

    private suspend fun recordLaunch(key: AppKey) {
        try {
            historyLock.withLock {
                // Read the persisted value, not the UI snapshot, so a just-disabled history is honoured.
                if (container.settings.settings.first().historyEnabled) {
                    repository.recordLaunch(key, System.currentTimeMillis())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not record launch", e)
        }
    }

    private suspend fun onPackageEvent(event: PackageEvent) {
        when (event) {
            is PackageEvent.Removed -> {
                boundary("invalidate icons") { container.icons.invalidatePackage(event.packageName) }
                val org = organizationState.filterNotNull().first()
                val keys = org.referencedKeys().filter {
                    it.packageName == event.packageName && it.userSerial == event.userSerial
                }
                keys.forEach { key -> persistNow { repository.forgetApp(key) } }
            }
        }
    }

    /** Shows Onboarding once settings have loaded and say it hasn't been completed; keeps it on top. */
    private suspend fun showOnboardingWhenNeeded() {
        settingsState.filterNotNull()
            .map { it.onboardingComplete }
            .distinctUntilChanged()
            .collect { complete ->
                if (complete) {
                    updateSession { s -> s.copy(layers = s.layers.filterNot { it == Layer.Onboarding }) }
                } else if (!onboardingDismissed) {
                    updateSession { s -> s.copy(layers = s.layers.filterNot { it == Layer.Onboarding } + Layer.Onboarding) }
                }
            }
    }

    /**
     * Focus by identity: if the selected app vanishes from the visible list (uninstalled or
     * hidden), select its nearest surviving neighbour in the list it was browsed in.
     */
    private suspend fun keepSelectionByIdentity() {
        var previous: SelectionLists? = null
        state.map { if (it.loading) null else SelectionLists(it.categoryApps.map { a -> a.key }, it.apps.map { a -> a.key }) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { lists ->
                val prev = previous
                previous = lists
                val selected = session.value.selectedApp ?: return@collect
                if (prev == null || selected in lists.visible) return@collect
                val replacement = if (selected in prev.category) {
                    FocusAnchor.resolve(prev.category, lists.category, selected)
                } else {
                    FocusAnchor.resolve(prev.visible, lists.visible, selected)
                }
                updateSession { s -> if (s.selectedApp == selected) s.copy(selectedApp = replacement) else s }
            }
    }

    /** Drops layers pointing at folders or apps that no longer exist, so no stale target stays open. */
    private suspend fun pruneStaleLayers() {
        state.collect { ui ->
            if (ui.loading) return@collect
            val available = ui.allApps.mapTo(HashSet()) { it.key }
            updateSession { s ->
                val kept = s.layers.filter { layer ->
                    when (layer) {
                        is Layer.FolderLayer -> layer.folderId in ui.folders
                        is Layer.AppActions -> layer.key in available
                        is Layer.CategoryMembership -> layer.key in available
                        is Layer.MoveToFolder -> layer.key in available
                        else -> true
                    }
                }
                if (kept.size == s.layers.size) s else s.copy(layers = kept)
            }
        }
    }

    private fun updateSession(transform: (SessionState) -> SessionState) {
        val before = session.value
        session.value = transform(before)
        val after = session.value
        if (after != before) SessionSaver.save(savedState, after)
    }

    private fun postMessage(text: String) {
        message.value = UserMessage(messageIds.incrementAndGet(), text)
    }

    private fun systemAction(what: String, action: () -> LaunchResult) {
        val result = try {
            action()
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
            LaunchResult.Failure("Android couldn't $what right now.")
        }
        if (result is LaunchResult.Failure) postMessage(result.message)
    }

    /** Runs a persistence call in the background; a storage failure becomes a readable message. */
    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { persistNow(block) }
    }

    private suspend fun persistNow(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "saving launcher configuration failed", e)
            postMessage("Couldn't save that change. Please try again.")
        }
    }

    /** Read-modify-write against the latest persisted organisation, serialised with other edits. */
    private fun edit(block: suspend (Organization) -> Unit) = persist {
        editLock.withLock {
            val org = repository.organization.first()
            block(org)
        }
    }

    private inline fun boundary(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
        }
    }

    private data class SelectionLists(val category: List<AppKey>, val visible: List<AppKey>)

    companion object {
        private const val TAG = "LauncherViewModel"
        private const val DEFAULT_FOLDER_NAME = "Folder"
        private const val DEFAULT_CATEGORY_NAME = "New category"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LauncherViewModel(container, createSavedStateHandle()) }
        }
    }
}

/** Pushes [layer], or brings it back to the top if already open. Onboarding always stays on top. */
private fun SessionState.withLayer(layer: Layer): SessionState {
    if (topLayer == layer) return this
    val existing = layers.indexOf(layer)
    val base = if (existing >= 0) layers.subList(0, existing + 1) else layers + layer
    // Onboarding stays above everything until completeOnboarding().
    val ordered = if (Layer.Onboarding in base || Layer.Onboarding in layers) {
        base.filterNot { it == Layer.Onboarding } + Layer.Onboarding
    } else {
        base
    }
    return copy(layers = ordered)
}

/** Pops the top layer, tidying state that belonged to it. Never pops Onboarding. */
private fun SessionState.popTop(): SessionState {
    val top = topLayer ?: return this
    if (top == Layer.Onboarding) return this
    return copy(
        layers = layers.dropLast(1),
        searchActive = if (top == Layer.Drawer) false else searchActive,
        searchText = if (top == Layer.Drawer) "" else searchText,
        editSelection = if (top == Layer.Edit) null else editSelection,
    )
}

/** Every app identity the organisation refers to anywhere. */
private fun Organization.referencedKeys(): Set<AppKey> = buildSet {
    homeItems.forEach { if (it is HomeItem.App) add(it.key) }
    dock.forEach { if (it != null) add(it) }
    folders.values.forEach { addAll(it.apps) }
    categoryMembers.values.forEach { addAll(it) }
    addAll(hidden)
    recents.forEach { add(it.key) }
}
