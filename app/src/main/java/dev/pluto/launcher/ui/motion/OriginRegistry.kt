package dev.pluto.launcher.ui.motion

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Knows where controls are drawn (window coordinates), keyed by their focus id, so motion
 * can start from them: a folder grows out of its tile, a launched app zooms out of its icon,
 * a sheet rises from the tile that opened it.
 *
 * Bounds are resolved lazily: [reportOrigin] only keeps a reference to the element's live
 * layout coordinates (no callback per frame while lists scroll), and [boundsOf] computes the
 * bounds when motion actually needs them. Elements that left composition keep their last
 * known bounds. Plain maps, not snapshot state: reading never triggers recomposition.
 * Main thread only.
 */
@Stable
class OriginRegistry {
    private val bounds = HashMap<String, Rect>()
    private val live = HashMap<String, OriginNode>()

    /** Records fixed bounds for [id] (replaces a tracked element). */
    fun report(id: String, rect: Rect) {
        live.remove(id)
        bounds[id] = rect
    }

    fun boundsOf(id: String?): Rect? {
        if (id == null) return null
        live[id]?.currentBounds()?.let { return it }
        return bounds[id]
    }

    fun forget(id: String) {
        bounds.remove(id)
        live.remove(id)
    }

    internal fun track(id: String, node: OriginNode) {
        live[id] = node
    }

    internal fun untrack(id: String, node: OriginNode) {
        if (live[id] !== node) return
        live.remove(id)
        // Keep where it was last seen, e.g. for a layer closing back toward a tile that is gone.
        node.lastBounds?.let { bounds[id] = it }
    }
}

val LocalOriginRegistry = staticCompositionLocalOf { OriginRegistry() }

/**
 * Makes this element's window bounds available from [registry] under [id]. Costs nothing
 * per frame: the bounds are computed from the retained coordinates only when asked for.
 */
fun Modifier.reportOrigin(registry: OriginRegistry, id: String): Modifier = this then OriginElement(registry, id)

private class OriginElement(val registry: OriginRegistry, val id: String) : ModifierNodeElement<OriginNode>() {
    override fun create() = OriginNode(registry, id)

    override fun update(node: OriginNode) = node.update(registry, id)

    override fun InspectorInfo.inspectableProperties() {
        name = "reportOrigin"
        properties["id"] = id
    }

    override fun equals(other: Any?): Boolean = other is OriginElement && other.registry === registry && other.id == id
    override fun hashCode(): Int = 31 * System.identityHashCode(registry) + id.hashCode()
}

internal class OriginNode(private var registry: OriginRegistry, private var id: String) :
    Modifier.Node(), LayoutAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null

    /** The bounds computed last (kept for after the element left composition). */
    var lastBounds: Rect? = null
        private set

    override val shouldAutoInvalidate: Boolean get() = false

    fun update(registry: OriginRegistry, id: String) {
        if (registry === this.registry && id == this.id) return
        if (isAttached) this.registry.untrack(this.id, this)
        this.registry = registry
        this.id = id
        if (isAttached && coordinates != null) registry.track(id, this)
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        // Coordinates are live: positions are read from them on demand, never copied per frame.
        if (this.coordinates !== coordinates) {
            this.coordinates = coordinates
            registry.track(id, this)
        }
    }

    fun currentBounds(): Rect? {
        val c = coordinates?.takeIf { it.isAttached } ?: return lastBounds
        return try {
            c.boundsInWindow().also { lastBounds = it }
        } catch (e: IllegalStateException) {
            lastBounds
        }
    }

    override fun onDetach() {
        currentBounds()
        registry.untrack(id, this)
        coordinates = null
    }
}
