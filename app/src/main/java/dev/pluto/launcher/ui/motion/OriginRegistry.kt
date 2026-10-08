package dev.pluto.launcher.ui.motion

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Remembers where controls were last drawn (window coordinates), keyed by their focus id,
 * so motion can start from them: a folder grows out of its tile, a launched app zooms
 * out of its icon, a sheet rises from the tile that opened it.
 * Plain map, not snapshot state: reading it never triggers recomposition.
 */
@Stable
class OriginRegistry {
    private val bounds = HashMap<String, Rect>()

    fun report(id: String, rect: Rect) {
        bounds[id] = rect
    }

    fun boundsOf(id: String?): Rect? = id?.let { bounds[it] }

    fun forget(id: String) {
        bounds.remove(id)
    }
}

val LocalOriginRegistry = staticCompositionLocalOf { OriginRegistry() }

/** Reports this element's window bounds to [registry] under [id] whenever it moves. */
fun Modifier.reportOrigin(registry: OriginRegistry, id: String): Modifier =
    onGloballyPositioned { registry.report(id, it.boundsInWindow()) }
