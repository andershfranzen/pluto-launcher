package dev.pluto.launcher.ui.state

import androidx.lifecycle.SavedStateHandle
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.SessionState

/**
 * Mirrors [SessionState] into a [SavedStateHandle] using only primitive / String values,
 * so it survives process death as well as rotation. Anything that no longer decodes
 * (e.g. a layer format from an older build) is dropped on restore.
 */
object SessionSaver {
    private const val LAYERS = "session.layers"
    private const val SEARCH_TEXT = "session.searchText"
    private const val SEARCH_ACTIVE = "session.searchActive"
    private const val CATEGORY = "session.activeCategoryId"
    private const val SELECTED_APP = "session.selectedApp"
    private const val FOCUSED_CONTROL = "session.focusedControlId"
    private const val ANCHOR_SURFACES = "session.scrollAnchorSurfaces"
    private const val ANCHOR_ITEMS = "session.scrollAnchorItems"
    private const val EDIT_SELECTION = "session.editSelection"

    fun restore(handle: SavedStateHandle): SessionState {
        val surfaces = handle.get<Array<String>>(ANCHOR_SURFACES).orEmpty()
        val items = handle.get<Array<String>>(ANCHOR_ITEMS).orEmpty()
        return SessionState(
            layers = handle.get<ArrayList<String>>(LAYERS).orEmpty().mapNotNull(Layer::decode),
            searchText = handle.get<String>(SEARCH_TEXT).orEmpty(),
            searchActive = handle.get<Boolean>(SEARCH_ACTIVE) ?: false,
            activeCategoryId = handle.get<Long>(CATEGORY),
            selectedApp = handle.get<String>(SELECTED_APP)?.let(AppKey::decode),
            focusedControlId = handle.get<String>(FOCUSED_CONTROL),
            scrollAnchors = surfaces.zip(items).toMap(),
            editSelection = handle.get<String>(EDIT_SELECTION),
        )
    }

    fun save(handle: SavedStateHandle, session: SessionState) {
        handle[LAYERS] = ArrayList(session.layers.map(Layer::encode))
        handle[SEARCH_TEXT] = session.searchText
        handle[SEARCH_ACTIVE] = session.searchActive
        handle[CATEGORY] = session.activeCategoryId
        handle[SELECTED_APP] = session.selectedApp?.encode()
        handle[FOCUSED_CONTROL] = session.focusedControlId
        handle[ANCHOR_SURFACES] = session.scrollAnchors.keys.toTypedArray()
        handle[ANCHOR_ITEMS] = session.scrollAnchors.values.toTypedArray()
        handle[EDIT_SELECTION] = session.editSelection
    }
}
