package dev.pluto.launcher.ui.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.focus.LocalFocusInert
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Only the visible interval and a small look-ahead; never walks the entire catalog. */
@Composable
internal fun <T> PrewarmGridIcons(grid: LazyGridState, items: List<T>, size: Dp,
    enabled: Boolean = true, keys: (T) -> List<AppKey>) {
    val cache = LocalIconCache.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inert = LocalFocusInert.current
    val currentKeys = rememberUpdatedState(keys)
    LaunchedEffect(grid, items, px, enabled, cache, lifecycle, inert) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow {
                if (inert.value) emptyList() else {
                    val visible = grid.layoutInfo.visibleItemsInfo
                    val first = visible.minOfOrNull { it.index } ?: 0
                    val last = visible.maxOfOrNull { it.index } ?: -1
                    if (visible.isEmpty()) emptyList()
                    else viewportIconKeys(items, first, last, cache.prewarmLimit(px), currentKeys.value)
                }
            }.collectLatest { visible ->
                delay(80) // Skip transient intervals during fast flings.
                cache.prefetch(visible, listOf(px))
            }
        }
    }
}

internal fun HomeGridEntry.prewarmKeys(): List<AppKey> = when (this) {
    HomeGridEntry.Gap -> emptyList()
    is HomeGridEntry.Tile -> when (val tile = tile) {
        is HomeTile.App -> listOf(tile.entry.key)
        is HomeTile.FolderTile -> emptyList()
        is HomeTile.Widget -> emptyList()
    }
}

/** Evaluates keys only near the viewport; limits total warming even for expanded folders. */
internal fun <T> viewportIconKeys(items: List<T>, first: Int, last: Int, limit: Int,
    keys: (T) -> List<AppKey>): List<AppKey> {
    if (limit <= 0 || last < first || items.isEmpty()) return emptyList()
    val end = (last.toLong() + 12).coerceAtMost(items.lastIndex.toLong()).toInt()
    return buildList {
        for (index in first.coerceAtLeast(0)..end) {
            for (key in keys(items[index])) {
                if (size >= limit) break
                if (key !in this) add(key)
            }
            if (size >= limit) break
        }
    }
}
