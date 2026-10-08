package dev.pluto.launcher.ui

import android.graphics.Rect
import android.os.Bundle
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
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
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Single shared state model for every layout. Layout composables are pure views of
 * [state]; all mutations go through these methods. Organisation writes persist
 * immediately; session state is mirrored into [savedState] for process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
    /** Set when the organisation or settings store cannot be read; the UI shows a recovery screen. */
    private val storageError = MutableStateFlow<String?>(null)
    /** Bumped by [retryStorage] to re-open the stores after a read failure. */
    private val storageAttempt = MutableStateFlow(0)

    /**
     * The single serialisation point for organisation mutations: every read-modify-write edit
     * (reorders, dock) and every other organisation write (pin, folders, categories, hide,
     * forgetting uninstalled apps) holds it, so a reorder computed from a read can never
     * overwrite a change made after that read.
     */
    private val editLock = Mutex()
    /** Serialises history writes against disabling history, so nothing is recorded after it is off. */
    private val historyLock = Mutex()
    /** Set once the user finished onboarding in this session, so a stale settings emission cannot re-open it. */
    @Volatile private var onboardingDismissed = false

    /**
     * Shared, single subscription to the persisted organisation (null until first load).
     * A Room failure (missing migration, schema mismatch, SQLiteException) must not crash the
     * Home app in a loop: it becomes [storageError] and the database file is left untouched.
     */
    private val organizationState: StateFlow<Organization?> =
        storageAttempt.flatMapLatest {
            repository.organization.catch { e -> onStorageFailure("organization", e) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** I/O errors are handled (fail closed) in SettingsRepository; anything else is a storage error. */
    private val settingsState: StateFlow<LauncherSettings?> =
        storageAttempt.flatMapLatest {
            container.settings.settings.catch { e -> onStorageFailure("settings", e) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<LauncherUiState> = run {
        val builder = LauncherStateBuilder()
        // Null until organisation and settings have both loaded (or forever after a storage error).
        val library = combine(
            container.catalog.apps,
            organizationState,
            settingsState,
        ) { catalog, organization, settings ->
            if (organization == null || settings == null) null else LibraryInputs(catalog, organization, settings)
        }
        val environment = combine(
            container.controllers.controllers,
            container.catalog.otherProfilesPresent,
            windowSize,
            combine(message, storageError, ::Pair),
            isDefaultHome,
        ) { controllers, otherProfiles, window, (msg, error), defaultHome ->
            EnvironmentInputs(controllers, otherProfiles, window, msg, defaultHome, error)
        }
        combine(library, environment, session) { lib, env, sess ->
            if (lib == null) builder.notLoaded(env, sess) else builder.build(lib, env, sess)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())
    }

    init {
        boundary("start app catalog") { container.catalog.start() }
        boundary("start controller monitor") { container.controllers.start() }
        viewModelScope.launch {
            persistNow { repository.ensureDefaults() }
            seedDefaultLayoutOnce()
        }
        viewModelScope.launch { container.catalog.packageEvents.collect(::onPackageEvent) }
        viewModelScope.launch { reconcileWithCatalog() }
        viewModelScope.launch { enforceHistoryPolicy() }
        viewModelScope.launch { announceSettingsRecovery() }
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

    /**
     * Activity resumed: refresh controllers and default-home status. The catalog is kept
     * current by its LauncherApps callback, so it only reloads when it may be stale (no live
     * callback, failed load, locale change): a warm Home return does no full label reload.
     */
    fun onResume(isDefaultHome: Boolean) {
        this.isDefaultHome.value = isDefaultHome
        boundary("refresh app catalog") { container.catalog.refreshIfStale() }
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
    /**
     * Launches [key]. [sourceBounds] (screen coordinates) is the control the app opens from;
     * [options] an ActivityOptions bundle with its opening animation (built by the activity,
     * see LaunchSourceFactory), or null for the default window animation.
     */
    fun launch(key: AppKey, sourceBounds: Rect? = null, options: Bundle? = null) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val result = try {
                container.catalog.launch(key, sourceBounds, options)
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
                is LaunchResult.Failure -> {
                    postMessage(result.message)
                    // The activity no longer resolves: drop the reference if it is really gone.
                    if (result.targetMissing) forgetStale(listOf(key))
                }
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
    fun pin(key: AppKey) = lockedPersist { repository.pin(key) }
    fun unpin(key: AppKey) = lockedPersist { repository.unpin(key) }

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

    /**
     * Adds to the first free dock slot, chosen from the persisted dock under [editLock] (so
     * two quick adds never pick the same slot). Only an empty slot is free: a hidden or
     * temporarily unavailable app keeps its slot. [onDockFull] runs on the main thread when
     * no slot is free, after the "Dock is full" message is posted.
     */
    fun addToDock(key: AppKey, onDockFull: () -> Unit = {}) = edit { org ->
        if (key in org.dock) return@edit
        val slot = org.dock.indexOfFirst { it == null }
        if (slot < 0) {
            postMessage(DOCK_FULL_MESSAGE)
            onDockFull()
            return@edit
        }
        repository.setDock(org.dock.toMutableList().also { it[slot] = key })
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

    fun renameFolder(id: Long, name: String) = lockedPersist {
        repository.renameFolder(id, name.trim().ifBlank { DEFAULT_FOLDER_NAME })
    }

    fun deleteFolder(id: Long) {
        updateSession { s -> s.copy(layers = s.layers.filterNot { it == Layer.FolderLayer(id) }) }
        lockedPersist { repository.deleteFolder(id) }
    }

    fun moveAppToFolder(key: AppKey, folderId: Long) = lockedPersist { repository.moveAppToFolder(key, folderId) }
    fun removeAppFromFolder(key: AppKey) = lockedPersist { repository.removeAppFromFolder(key) }

    fun moveFolderApp(folderId: Long, key: AppKey, op: ReorderOp<AppKey>) = edit { org ->
        val folder = org.folders[folderId] ?: return@edit
        val visible = state.value.folders[folderId]?.apps.orEmpty().mapTo(HashSet()) { it.key }
        val next = reorderVisible(folder.apps, { it in visible }, key, op)
        if (next != folder.apps) repository.setFolderOrder(folderId, next)
    }

    fun addCategory(name: String) = lockedPersist { repository.addCategory(name.trim().ifBlank { DEFAULT_CATEGORY_NAME }) }

    fun renameCategory(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        lockedPersist { repository.renameCategory(id, trimmed) }
    }

    fun deleteCategory(id: Long) {
        val category = state.value.categories.firstOrNull { it.id == id }
        if (category?.isAll == true) return
        updateSession { s -> if (s.activeCategoryId == id) s.copy(activeCategoryId = null) else s }
        lockedPersist { repository.deleteCategory(id) }
    }

    fun moveCategory(id: Long, op: ReorderOp<Long>) = edit { org ->
        val ids = org.categories.sortedWith(compareBy({ it.position }, { it.id })).map { it.id }
        val next = Reorder.apply(ids, id, op)
        if (next != ids) repository.reorderCategories(next)
    }

    fun setCategoryMembership(categoryId: Long, key: AppKey, member: Boolean) =
        lockedPersist { repository.setCategoryMembership(categoryId, key, member) }

    fun hide(key: AppKey) = lockedPersist { repository.hide(key) }
    fun unhide(key: AppKey) = lockedPersist { repository.unhide(key) }

    // --- History & settings -------------------------------------------------
    fun clearHistory() = persist { historyLock.withLock { repository.clearRecents() } }

    /**
     * Disabling also deletes stored history and stops collection. Records are deleted before
     * the flag is written, so "off" is never persisted while records remain; should the
     * process die in between, [enforceHistoryPolicy] deletes them on the next start.
     */
    fun setHistoryEnabled(enabled: Boolean) = persist {
        historyLock.withLock {
            if (!enabled) repository.clearRecents()
            container.settings.update { it.copy(historyEnabled = enabled) }
        }
    }

    /** Re-opens the database and settings after a storage error (the Retry button). */
    fun retryStorage() {
        storageError.value = null
        storageAttempt.update { it + 1 }
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
                // Unreadable settings fail closed (readFailed): nothing is recorded then.
                val settings = container.settings.settings.first()
                if (settings.historyEnabled && !settings.readFailed) {
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
                keys.forEach { key -> persistNow { editLock.withLock { repository.forgetApp(key) } } }
            }
        }
    }

    /**
     * Reconciles persisted references with every successful catalog load, so changes the
     * live callback never saw (uninstalled while the process was dead or the view model was
     * cleared, an update that removed or renamed a launcher activity) leave no stale rows.
     * Never runs from a failed or empty query; [AppCatalog.findStale] only reports keys
     * whose activity is gone from an installed package or whose package is uninstalled.
     */
    private suspend fun reconcileWithCatalog() {
        container.catalog.loads.filterNotNull().collect { load ->
            if (load.apps.isEmpty()) return@collect
            val org = organizationState.filterNotNull().first()
            val available = load.apps.mapTo(HashSet()) { it.key }
            val missing = org.referencedKeys().filterNot { it in available }
            if (missing.isNotEmpty()) forgetStale(missing)
        }
    }

    /** Forgets those of [keys] that the system confirms are gone (checked off the main thread). */
    private suspend fun forgetStale(keys: Collection<AppKey>) {
        val stale = try {
            withContext(Dispatchers.IO) { container.catalog.findStale(keys) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "couldn't check for stale apps", e)
            emptySet()
        }
        stale.forEach { key ->
            boundary("invalidate icons") { container.icons.invalidatePackage(key.packageName) }
            persistNow { editLock.withLock { repository.forgetApp(key) } }
        }
    }

    /**
     * Disabled history must hold no records: whenever settings say history is off (at start
     * and on every change) while records exist, delete them. Covers a crash between the delete
     * and the flag write, and a failed delete.
     */
    private suspend fun enforceHistoryPolicy() {
        combine(settingsState.filterNotNull(), organizationState.filterNotNull()) { settings, org ->
            !settings.historyEnabled && !settings.readFailed && org.recents.isNotEmpty()
        }
            .distinctUntilChanged()
            .collect { mustClear ->
                if (mustClear) persistNow { historyLock.withLock { repository.clearRecents() } }
            }
    }

    /** Tells the user once that a damaged settings file was replaced, or that settings can't be read. */
    private suspend fun announceSettingsRecovery() {
        settingsState.filterNotNull()
            .map { it.recoveredFromCorruption to it.readFailed }
            .distinctUntilChanged()
            .collect { (recovered, unreadable) ->
                when {
                    unreadable -> postMessage(SETTINGS_UNREADABLE_MESSAGE)
                    recovered -> {
                        postMessage(SETTINGS_RESET_MESSAGE)
                        persistNow { container.settings.update { it.copy(recoveredFromCorruption = false) } }
                    }
                }
            }
    }

    /**
     * First run: once the catalog has loaded apps successfully, place the device's default
     * dialer, messaging, browser and camera in the dock and a few system apps on home.
     * Only if no layout exists, and only once ever (settings.layoutSeeded), so a layout the
     * user emptied on purpose is never refilled. An install that finished onboarding before
     * seeding existed is marked seeded without changes.
     */
    private suspend fun seedDefaultLayoutOnce() {
        val initial = settingsState.filterNotNull().first()
        if (initial.layoutSeeded || initial.readFailed) return
        if (initial.onboardingComplete) {
            persistNow { container.settings.update { it.copy(layoutSeeded = true) } }
            return
        }
        val load = container.catalog.loads.filterNotNull().first { it.apps.isNotEmpty() }
        val plan = try {
            withContext(Dispatchers.IO) { container.layoutSeeder.plan(load.apps) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "couldn't plan the default layout", e)
            null
        }
        persistNow {
            if (plan != null && !plan.isEmpty) {
                editLock.withLock { repository.seedDefaultLayout(plan.dock, plan.home) }
            }
            container.settings.update { it.copy(layoutSeeded = true) }
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

    /** [persist] for organisation writes: serialised with read-modify-write edits under [editLock]. */
    private fun lockedPersist(block: suspend () -> Unit) = persist { editLock.withLock { block() } }

    private fun onStorageFailure(what: String, e: Throwable) {
        Log.e(TAG, "$what load failed", e)
        storageError.value = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
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
        private const val DOCK_FULL_MESSAGE = "Dock is full. Remove an app from the dock first."
        private const val SETTINGS_RESET_MESSAGE =
            "Pluto's settings file was damaged and has been reset. Recent launches is off until you turn it on again."
        private const val SETTINGS_UNREADABLE_MESSAGE =
            "Pluto can't read its settings right now, so defaults are in use and Recent launches is paused."

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
