package dev.pluto.launcher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.ui.DragSource
import dev.pluto.launcher.ui.DropTarget
import dev.pluto.launcher.ui.FolderUi
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.components.AppIcon
import dev.pluto.launcher.ui.components.FolderTile
import dev.pluto.launcher.ui.components.GlassPanel
import kotlinx.coroutines.delay

/** The drag in progress on Home, shared by every tile, the dock, the drawer and the overlay; null where drag is off. */
val LocalHomeDrag = staticCompositionLocalOf<HomeDragController?> { null }

/** What the floating copy under the finger looks like. */
sealed interface DragLook {
    data class App(val entry: AppEntry) : DragLook
    data class Folder(val folder: FolderUi) : DragLook
    data class Widget(val label: String) : DragLook
}

/** Id of the gap an app from the drawer or dock opens in the home grid while hovering over it. */
internal const val DRAG_PLACEHOLDER_ID = "drag:placeholder"

/**
 * One drag at a time. A tile starts it ([homeDragSource]) after a press-and-hold that then
 * moves; the root follows the finger ([move], [release]) because a drag out of the drawer
 * keeps going after the drawer has slid away. Targets come from registered geometry: the
 * home grid ([HomeGridProbe]), the dock slots and the Remove bar, all in window coordinates.
 */
@Stable
class HomeDragController(
    private val onStart: (DragSource) -> Unit,
    private val onDrop: (DragSource, DropTarget) -> Unit,
    /** Feedback when the finger moves onto a different target (null: off every target). */
    private val onTargetChanged: (DropTarget?) -> Unit = {},
) {
    var source by mutableStateOf<DragSource?>(null)
        private set
    var look by mutableStateOf<DragLook?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var target by mutableStateOf<DropTarget?>(null)
        private set

    /** The home grid's order while dragging: the dragged item (or a gap) where it would land. */
    var preview by mutableStateOf<List<String>?>(null)
        private set

    var pointerId: PointerId? = null
        private set

    val isActive: Boolean get() = source != null

    internal var grid: HomeGridProbe? = null
    internal val dockSlots = HashMap<Int, Rect>()
    internal var removeZone: Rect? = null

    private var mergeCandidate: String? = null
    private var mergeSince = 0L

    /** Whether the Remove bar applies to this drag (apps on Home, anything in the dock). */
    val canRemove: Boolean
        get() = when (val s = source) {
            is DragSource.Home -> s.app != null
            is DragSource.Dock -> true
            else -> false
        }

    fun start(source: DragSource, look: DragLook, pointerId: PointerId, at: Offset) {
        if (isActive) return
        this.source = source
        this.look = look
        this.pointerId = pointerId
        pointer = at
        target = null
        mergeCandidate = null
        preview = null
        onStart(source)
        retarget()
    }

    fun move(at: Offset) {
        if (!isActive) return
        pointer = at
        retarget()
    }

    /** Re-evaluates without movement, so resting over an app still turns into a folder. */
    fun tick() {
        if (isActive) retarget()
    }

    fun release() {
        val s = source ?: return
        val t = target
        clear()
        if (t != null) onDrop(s, t)
    }

    fun cancel() = clear()

    private fun clear() {
        source = null
        look = null
        pointerId = null
        target = null
        preview = null
        mergeCandidate = null
    }

    private fun retarget() {
        val s = source ?: return
        val p = pointer
        val next: DropTarget? = when {
            canRemove && removeZone?.contains(p) == true -> DropTarget.Remove
            s.app != null && dockSlots.entries.firstOrNull { it.value.contains(p) } != null ->
                DropTarget.DockSlot(dockSlots.entries.first { it.value.contains(p) }.key)
            else -> gridTarget(s, p)
        }
        if (next !is DropTarget.Merge && mergeCandidate != null && gridTargetIsMergeZone(s, p) == null) mergeCandidate = null
        if (next != target) onTargetChanged(next)
        target = next
        val grid = grid
        if (grid != null) {
            val base = grid.ids.filterNot { it == s.homeId || it == DRAG_PLACEHOLDER_ID }
            preview = when (next) {
                is DropTarget.HomeIndex -> base.toMutableList().apply {
                    add(next.index.coerceIn(0, size), if (s is DragSource.Home || s.homeId in grid.ids) s.homeId else DRAG_PLACEHOLDER_ID)
                }
                // Over a folder target, the dock or Remove: Home keeps the last arrangement.
                else -> preview?.takeIf { s is DragSource.Home }
            }
        }
    }

    /** The app or folder whose centre the finger is over, if this drag can merge into it. */
    private fun gridTargetIsMergeZone(s: DragSource, p: Offset): String? {
        if (s.app == null) return null
        val (id, rect) = grid?.hit(p) ?: return null
        if (id == s.homeId || id == DRAG_PLACEHOLDER_ID) return null
        if (!id.startsWith(HomeItem.APP_PREFIX) && !id.startsWith(HomeItem.FOLDER_PREFIX)) return null
        val inset = Size(rect.width * MERGE_INSET, rect.height * MERGE_INSET)
        val centre = Rect(rect.left + inset.width, rect.top + inset.height, rect.right - inset.width, rect.bottom - inset.height)
        return id.takeIf { centre.contains(p) }
    }

    private fun gridTarget(s: DragSource, p: Offset): DropTarget? {
        val grid = grid ?: return null
        if (!grid.bounds().contains(p)) return null
        gridTargetIsMergeZone(s, p)?.let { id ->
            val now = android.os.SystemClock.uptimeMillis()
            if (mergeCandidate != id) {
                mergeCandidate = id
                mergeSince = now
            }
            // A short rest over an app's centre makes a folder; until then nothing moves under the finger.
            return if (now - mergeSince >= MERGE_DWELL_MS) DropTarget.Merge(id) else target
        }
        val base = grid.ids.filterNot { it == s.homeId || it == DRAG_PLACEHOLDER_ID }
        val hit = grid.hit(p)
        if (hit == null) return DropTarget.HomeIndex(base.size)
        val (id, rect) = hit
        if (id == s.homeId || id == DRAG_PLACEHOLDER_ID) return target ?: DropTarget.HomeIndex(base.size)
        val index = base.indexOf(id)
        if (index < 0) return target
        return DropTarget.HomeIndex(if (p.x < rect.center.x) index else index + 1)
    }

    private companion object {
        const val MERGE_DWELL_MS = 280L
        /** The middle of a tile (this much in from each side) is "onto it"; the edges reorder. */
        const val MERGE_INSET = 0.24f
    }
}

/** The home grid's geometry for hit-testing: where it is in the window and where its items are. */
internal class HomeGridProbe(private val state: LazyGridState) {
    var origin = Offset.Zero
    var size = Size.Zero
    /** The grid's own item order (not the preview). */
    var ids: List<String> = emptyList()

    fun bounds(): Rect = Rect(origin, size)

    fun hit(p: Offset): Pair<String, Rect>? {
        for (item in state.layoutInfo.visibleItemsInfo) {
            val rect = Rect(
                origin + Offset(item.offset.x.toFloat(), item.offset.y.toFloat()),
                Size(item.size.width.toFloat(), item.size.height.toFloat()),
            )
            if (rect.contains(p)) return (item.key as? String ?: continue) to rect
        }
        return null
    }
}

/**
 * Registers a home grid with the drag (its position and items) and returns the tiles to show:
 * during a drag, in the preview order, with a gap for an app arriving from elsewhere.
 */
@Composable
internal fun rememberDragAwareTiles(tiles: List<HomeTile>, gridState: LazyGridState): Pair<List<HomeGridEntry>, Modifier> {
    val drag = LocalHomeDrag.current
    val probe = remember(gridState) { HomeGridProbe(gridState) }
    probe.ids = tiles.map { it.id }
    val register = if (drag == null) {
        Modifier
    } else {
        Modifier.onGloballyPositioned {
            probe.origin = it.positionInWindow()
            probe.size = Size(it.size.width.toFloat(), it.size.height.toFloat())
            drag.grid = probe
        }
    }
    val preview = drag?.preview
    val byId = tiles.associateBy { it.id }
    val entries = if (preview == null) {
        tiles.map { HomeGridEntry.Tile(it) }
    } else {
        preview.mapNotNull { id -> if (id == DRAG_PLACEHOLDER_ID) HomeGridEntry.Gap else byId[id]?.let { HomeGridEntry.Tile(it) } }
    }
    return entries to register
}

/** An item in a drag-aware home grid. */
internal sealed interface HomeGridEntry {
    val key: String

    data class Tile(val tile: HomeTile) : HomeGridEntry {
        override val key: String get() = tile.id
    }

    data object Gap : HomeGridEntry {
        override val key: String get() = DRAG_PLACEHOLDER_ID
    }
}

/** The empty place an arriving app will take: a faint ring the size of an icon. */
@Composable
internal fun DragGap(iconSize: Dp, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Box(modifier.padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.size(iconSize).border(1.5.dp, color, CircleShape))
    }
}

/**
 * Highlights a home item while a drag would drop onto it (folder in the making), and hides
 * the dragged item's own tile (its place stays, so the grid shows where it would land).
 */
@Composable
internal fun Modifier.dragFeedback(itemId: String): Modifier {
    val drag = LocalHomeDrag.current ?: return this
    val merging = (drag.target as? DropTarget.Merge)?.itemId == itemId
    val grow by animateFloatAsState(if (merging) 1f else 0f, label = "mergeGrow")
    val dragged = drag.source?.homeId == itemId
    val ring = MaterialTheme.colorScheme.onSurface
    return this
        .graphicsLayer {
            alpha = if (dragged) 0f else 1f
            val s = 1f + 0.08f * grow
            scaleX = s
            scaleY = s
        }
        .drawBehind {
            if (grow > 0.01f) {
                drawRoundRect(
                    ring.copy(alpha = 0.16f * grow),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.3f),
                )
            }
        }
}

/**
 * Makes a tile draggable: press and hold (the actions menu opens as usual), then move past
 * the touch slop and the tile lifts off as a drag; the menu closes. A move before the hold
 * is a scroll or swipe and is left alone. Observes in the Initial pass and consumes only once
 * dragging, so taps, long presses and scrolling behave exactly as before.
 */
@Composable
internal fun Modifier.homeDragSource(source: () -> DragSource?, look: () -> DragLook?): Modifier {
    val drag = LocalHomeDrag.current ?: return this
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onPlaced { coords[0] = it }
        .pointerInput(drag) {
            val slop = viewConfiguration.touchSlop
            val hold = viewConfiguration.longPressTimeoutMillis
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (dragging) {
                        change.consume()
                        if (!change.pressed) break
                        continue
                    }
                    if (!change.pressed) break
                    val held = change.uptimeMillis - down.uptimeMillis >= hold
                    val moved = (change.position - down.position).getDistance() > slop
                    if (moved && !held) break
                    if (moved) {
                        val s = source() ?: break
                        val l = look() ?: break
                        val c = coords[0]?.takeIf { it.isAttached } ?: break
                        drag.start(s, l, down.id, c.localToWindow(change.position))
                        dragging = true
                        change.consume()
                    }
                }
            }
        }
}

/** Registers a dock slot as a drop target and highlights it while a drag is over it. */
@Composable
internal fun Modifier.dockDropTarget(slot: Int): Modifier {
    val drag = LocalHomeDrag.current ?: return this
    // A folded (empty, hidden) slot must not keep catching drops at its old place.
    DisposableEffect(drag, slot) { onDispose { drag.dockSlots.remove(slot) } }
    val over = (drag.target as? DropTarget.DockSlot)?.slot == slot
    val glow by animateFloatAsState(if (over) 1f else 0f, label = "dockOver")
    val ring = MaterialTheme.colorScheme.onSurface
    return this
        .onGloballyPositioned { drag.dockSlots[slot] = it.boundsInWindow() }
        .drawBehind {
            if (glow > 0.01f) drawCircle(ring.copy(alpha = 0.18f * glow), radius = size.minDimension * 0.55f)
        }
}

/**
 * Above everything while dragging: the Remove bar at the top (when it applies) and the
 * floating copy under the finger. Not interactive itself; the root feeds it the pointer.
 */
@Composable
internal fun HomeDragOverlay(drag: HomeDragController, iconSize: Dp) {
    if (!drag.isActive) return
    LaunchedEffect(drag.source) {
        while (drag.isActive) {
            delay(60)
            drag.tick()
        }
    }
    Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        if (drag.canRemove) RemoveBar(drag, Modifier.align(Alignment.TopCenter))
        val look = drag.look
        val density = LocalDensity.current
        val half = with(density) { (iconSize * DRAG_SCALE / 2).roundToPx() }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = drag.pointer.x - half
                    translationY = drag.pointer.y - half * 1.2f
                }
                .shadow(12.dp, CircleShape, clip = false),
        ) {
            when (look) {
                is DragLook.App -> AppIcon(look.entry, iconSize * DRAG_SCALE)
                is DragLook.Folder -> FolderTile(look.folder, iconSize * DRAG_SCALE, "drag:folder", {}, {}, showLabel = false)
                is DragLook.Widget -> GlassPanel(shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(look.label, style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun RemoveBar(drag: HomeDragController, modifier: Modifier) {
    val over = drag.target == DropTarget.Remove
    val hot by animateFloatAsState(if (over) 1f else 0f, label = "removeOver")
    val danger = MaterialTheme.colorScheme.error
    GlassPanel(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 12.dp)
            .onGloballyPositioned {
                // A generous target: the whole top band, not just the pill.
                val b = it.boundsInWindow()
                drag.removeZone = Rect(0f, 0f, Float.MAX_VALUE, b.bottom + 24f)
            }
            .drawBehind { drawRect(danger.copy(alpha = 0.45f * hot)) },
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(HomeText.REMOVE, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

private const val DRAG_SCALE = 1.15f
