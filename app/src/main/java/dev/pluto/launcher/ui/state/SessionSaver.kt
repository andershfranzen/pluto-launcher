package dev.pluto.launcher.ui.state

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.SessionMemory
import dev.pluto.launcher.ui.SessionState

/**
 * Saves [SessionState] (including its [SessionMemory]) into a [SavedStateHandle] using only
 * primitive / String values, so it survives process death as well as rotation. Anything that
 * no longer decodes (e.g. a layer format from an older build) is dropped on restore.
 *
 * Nothing is written while the user navigates: [install] registers a provider that the
 * system calls when it actually saves instance state, so focus moves and scrolling never
 * pay for persistence.
 */
object SessionSaver {
    /** The provider's bundle key in the handle. */
    internal const val KEY = "session"

    private const val LAYERS = "session.layers"
    private const val SEARCH_TEXT = "session.searchText"
    private const val SEARCH_ACTIVE = "session.searchActive"
    private const val CATEGORY = "session.activeCategoryId"
    private const val SELECTED_APP = "session.selectedApp"
    private const val FOCUSED_CONTROL = "session.focusedControlId"
    private const val ANCHOR_SURFACES = "session.scrollAnchorSurfaces"
    private const val ANCHOR_ITEMS = "session.scrollAnchorItems"
    private const val EDIT_SELECTION = "session.editSelection"

    /** Restores the session saved by [install] (or by an older build's per-key format). */
    fun restore(handle: SavedStateHandle): SessionState {
        val bundle = handle.get<Bundle>(KEY)
        return if (bundle != null) {
            decode(
                layers = bundle.getStringArrayList(LAYERS),
                searchText = bundle.getString(SEARCH_TEXT),
                searchActive = bundle.getBoolean(SEARCH_ACTIVE, false),
                category = if (bundle.containsKey(CATEGORY)) bundle.getLong(CATEGORY) else null,
                selectedApp = bundle.getString(SELECTED_APP),
                focusedControl = bundle.getString(FOCUSED_CONTROL),
                surfaces = bundle.getStringArray(ANCHOR_SURFACES),
                items = bundle.getStringArray(ANCHOR_ITEMS),
                editSelection = bundle.getString(EDIT_SELECTION),
            )
        } else {
            decode(
                layers = handle.get<ArrayList<String>>(LAYERS),
                searchText = handle.get<String>(SEARCH_TEXT),
                searchActive = handle.get<Boolean>(SEARCH_ACTIVE) ?: false,
                category = handle.get<Long>(CATEGORY),
                selectedApp = handle.get<String>(SELECTED_APP),
                focusedControl = handle.get<String>(FOCUSED_CONTROL),
                surfaces = handle.get<Array<String>>(ANCHOR_SURFACES),
                items = handle.get<Array<String>>(ANCHOR_ITEMS),
                editSelection = handle.get<String>(EDIT_SELECTION),
            )
        }
    }

    /** Saves [current]'s value lazily, whenever the handle itself is saved. */
    fun install(handle: SavedStateHandle, current: () -> SessionState) {
        handle.setSavedStateProvider(KEY) { toBundle(current()) }
    }

    fun toBundle(session: SessionState): Bundle = Bundle().apply {
        putStringArrayList(LAYERS, ArrayList(session.layers.map(Layer::encode)))
        putString(SEARCH_TEXT, session.searchText)
        putBoolean(SEARCH_ACTIVE, session.searchActive)
        session.activeCategoryId?.let { putLong(CATEGORY, it) }
        putString(SELECTED_APP, session.selectedApp?.encode())
        putString(FOCUSED_CONTROL, session.focusedControlId)
        val anchors = session.scrollAnchors.entries.toList()
        putStringArray(ANCHOR_SURFACES, Array(anchors.size) { anchors[it].key })
        putStringArray(ANCHOR_ITEMS, Array(anchors.size) { anchors[it].value })
        putString(EDIT_SELECTION, session.editSelection)
    }

    /** Builds a session from saved primitives; pure (unit-tested). */
    internal fun decode(
        layers: List<String>?,
        searchText: String?,
        searchActive: Boolean,
        category: Long?,
        selectedApp: String?,
        focusedControl: String?,
        surfaces: Array<String>?,
        items: Array<String>?,
        editSelection: String?,
    ): SessionState = SessionState(
        layers = layers.orEmpty().mapNotNull(Layer::decode),
        searchText = searchText.orEmpty(),
        searchActive = searchActive,
        activeCategoryId = category,
        editSelection = editSelection,
        memory = SessionMemory(
            selectedApp = selectedApp?.let(AppKey::decode),
            focusedControlId = focusedControl,
            scrollAnchors = surfaces.orEmpty().zip(items.orEmpty()).toMap(),
        ),
    )
}
