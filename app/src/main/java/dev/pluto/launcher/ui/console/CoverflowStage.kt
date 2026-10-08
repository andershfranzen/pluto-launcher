package dev.pluto.launcher.ui.console

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.domain.FocusAnchor
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.motion.PlutoMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** Focus id of a card: per shelf, so the incoming and outgoing flows never share an id. */
internal fun cardFocusId(shelfKey: String, key: AppKey): String = "hh:card:$shelfKey:${key.encode()}"

/** Card shape (also the focus ring's shape). */
private val CardShape = RoundedCornerShape(26.dp)
private const val CARD_CORNER_FRACTION = 0.15f
private const val ICON_INSET_FRACTION = 0.13f
private const val REFLECTION_GAP_FRACTION = 0.04f
private const val REFLECTION_FRACTION = 0.42f

/** Room under the card for its reflection, in card heights. */
private const val STAGE_REFLECTION_SPACE = 0.30f

/** Milder perspective than the default camera (side cards stay readable). */
private const val CAMERA_DISTANCE = 14f

/** How many cards around the selection get their art prepared ahead of need. */
private const val ART_PREWARM = 10

/** Size the card art (icon) is rasterised at; cards are drawn up to ~200dp with a 13% inset. */
val ConsoleArtSize = 128.dp

/** The flow's spring: the spatialFast token, so a held D-pad (100 ms repeat) glides and retargets without queueing. */
private fun flowSpring(): FiniteAnimationSpec<Float> = PlutoMotion.spatialFast()

/**
 * State of one shelf's flow. [position] is the continuous flow position (in cards) that
 * layout and drawing read directly, so gliding never recomposes; [selectedIndex] is the
 * card the flow is heading to. A new target simply retargets the running spring (keeping
 * its velocity): nothing ever queues.
 */
@Stable
internal class CoverflowState(initialIndex: Int, initialApps: List<AppEntry>) {
    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set
    var selectedIndex by mutableIntStateOf(initialIndex)
        private set
    var apps by mutableStateOf(initialApps)
        private set

    /** Bumped when card art finishes loading, so cards redraw (draw-phase read only). */
    var artTick by mutableIntStateOf(0)

    /** Card width in px from the last layout (plain: only gestures read it). */
    internal var cardWidthPx = 0f

    private var velocity = 0f
    private var job: Job? = null
    internal val requesters = HashMap<AppKey, FocusRequester>()
    internal val anchors = HashMap<AppKey, OriginAnchor>()

    val count: Int get() = apps.size
    val selectedKey: AppKey? get() = apps.getOrNull(selectedIndex)?.key
    val selectedEntry: AppEntry? get() = apps.getOrNull(selectedIndex)

    /** Moves the selection to [index] and glides there (instant with reduce motion: MotionDurationScale 0). */
    fun select(index: Int, scope: CoroutineScope) {
        if (count == 0) return
        val target = index.coerceIn(0, count - 1)
        if (target != Snapshot.withoutReadObservation { selectedIndex }) selectedIndex = target
        settle(target, scope)
    }

    private fun settle(target: Int, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            val from = position
            if (from == target.toFloat() && velocity == 0f) return@launch
            try {
                animate(from, target.toFloat(), velocity, flowSpring()) { value, v ->
                    position = value
                    velocity = v
                }
            } finally {
                if (position == target.toFloat()) velocity = 0f
            }
        }
    }

    // --- Touch: 1:1 scrubbing, flings snap to the nearest card -----------------------------
    fun dragStart() {
        job?.cancel()
        velocity = 0f
    }

    fun dragBy(delta: Float) {
        if (count == 0) return
        position = (position + delta).coerceIn(-OVERSCROLL, count - 1 + OVERSCROLL)
        val nearest = CoverflowMath.nearest(position, count)
        if (nearest != selectedIndex) selectedIndex = nearest
    }

    fun release(velocityCardsPerSecond: Float, scope: CoroutineScope) {
        if (count == 0) return
        val target = CoverflowMath.snapTarget(position, velocityCardsPerSecond, count)
        velocity = velocityCardsPerSecond
        selectedIndex = target
        settle(target, scope)
    }

    /**
     * The shelf's apps changed (install, uninstall, hide, reorder, a new recent launch):
     * keep the selected app by identity (or its nearest surviving neighbour) and shift the
     * flow position with it, so the centre card stays put instead of jumping. Called during
     * composition (so layout in the same frame already sees the new list); the caller lets the
     * flow settle afterwards (an effect keyed on the list).
     */
    fun sync(newApps: List<AppEntry>) {
        val old = Snapshot.withoutReadObservation { apps }
        if (old === newApps) return
        if (old.map { it.key } == newApps.map { it.key }) {
            apps = newApps
            return
        }
        val oldSelected = Snapshot.withoutReadObservation { selectedIndex }
        val keep = FocusAnchor.resolve(old.map { it.key }, newApps.map { it.key }, old.getOrNull(oldSelected)?.key)
        val newIndex = newApps.indexOfFirst { it.key == keep }.coerceAtLeast(0)
        apps = newApps
        if (newApps.isEmpty()) return
        val shift = newIndex - oldSelected
        job?.cancel()
        position = (Snapshot.withoutReadObservation { position } + shift).coerceIn(-OVERSCROLL, newApps.size - 1 + OVERSCROLL)
        velocity = 0f
        selectedIndex = newIndex
    }

    /** Focus target of the card at [index]: Cancel past either end (focus stays), Default when not composed. */
    fun requesterAt(index: Int): FocusRequester {
        if (index < 0 || index >= apps.size) return FocusRequester.Cancel
        return requesters[apps[index].key] ?: FocusRequester.Default
    }

    fun selectedRequester(): FocusRequester = selectedKey?.let(requesters::get) ?: FocusRequester.Default

    /** Window bounds of the selected card, for the app-opening animation. */
    fun selectedBounds(): Rect? = selectedKey?.let(anchors::get)?.boundsInWindow()

    private companion object {
        const val OVERSCROLL = 0.35f
    }
}

/**
 * Slide/fade of a whole flow while shelves switch: [offset] is a fraction of the stage width,
 * [alpha] multiplies every card's alpha (cards use ModulateAlpha, so no page-sized offscreen layer).
 */
@Stable
internal class PageMotion(val offset: () -> Float, val alpha: () -> Float) {
    companion object {
        val Static = PageMotion({ 0f }, { 1f })
    }
}

/**
 * The Coverflow stage: only the cards within [CoverflowMath.RADIUS] of the selection and
 * flow position are composed. Cards are placed (layout phase) and tilted (layer properties)
 * from [CoverflowState.position], so gliding, scrubbing and snapping never recompose; the
 * card set only changes when the window moves by a card.
 */
@Composable
internal fun CoverflowStage(
    stage: CoverflowState,
    shelfKey: String,
    icons: IconCache,
    versions: Map<String, Int>,
    page: PageMotion,
    onActivate: (index: Int, entry: AppEntry) -> Unit,
    onActions: (entry: AppEntry) -> Unit,
    onFocused: (index: Int, entry: AppEntry) -> Unit,
    onTouchSelect: (index: Int) -> Unit,
    upTarget: () -> FocusRequester,
    downTarget: () -> FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val artPx = with(LocalDensity.current) { ConsoleArtSize.roundToPx() }
    val window by remember(stage) {
        derivedStateOf { CoverflowMath.window(stage.position, stage.selectedIndex, stage.count) }
    }
    val currentVersions by rememberUpdatedState(versions)
    val currentOnTouchSelect by rememberUpdatedState(onTouchSelect)

    // Card art: nearest cards first, then a little ahead either side; conflated so a held
    // D-pad never piles up work (only the newest window is prepared next).
    LaunchedEffect(stage, artPx, icons) {
        snapshotFlow { stage.apps to stage.selectedIndex }.conflate().collect { (apps, selected) ->
            if (apps.isEmpty()) return@collect
            val order = (0..ART_PREWARM * 2).map { step -> selected + if (step % 2 == 0) step / 2 else -(step + 1) / 2 }
            for (i in order) {
                val entry = apps.getOrNull(i) ?: continue
                val version = currentVersions[entry.packageName] ?: 0
                if (ConsoleArtCache.peek(entry.key, artPx, version) == null) {
                    if (ConsoleArtCache.load(icons, entry.key, artPx, version) != null) stage.artTick++
                }
            }
        }
    }

    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val apps = stage.apps
    val count = apps.size
    Layout(
        content = {
            for (index in window) {
                val entry = apps.getOrNull(index) ?: continue
                key(entry.key) {
                    CoverCard(
                        stage = stage,
                        entry = entry,
                        index = index,
                        count = count,
                        focusId = cardFocusId(shelfKey, entry.key),
                        version = versions[entry.packageName] ?: 0,
                        artPx = artPx,
                        placeholder = placeholder,
                        page = page,
                        onActivate = onActivate,
                        onActions = onActions,
                        onFocused = onFocused,
                        upTarget = upTarget,
                        downTarget = downTarget,
                    )
                }
            }
        },
        modifier = modifier
            .pointerInput(stage) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        stage.dragStart()
                    },
                    onDragEnd = {
                        val vx = tracker.calculateVelocity().x
                        val width = stage.cardWidthPx * CoverflowMath.FIRST_GAP
                        val cardsPerSecond = if (width > 0f) -vx / width else 0f
                        stage.release(cardsPerSecond, scope)
                        currentOnTouchSelect(stage.selectedIndex)
                    },
                    onDragCancel = {
                        stage.release(0f, scope)
                        currentOnTouchSelect(stage.selectedIndex)
                    },
                ) { change, dx ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    stage.dragBy(CoverflowMath.dragDelta(dx, stage.cardWidthPx))
                }
            }
            .semantics {
                // TalkBack: a scrollable flow, so swiping past the last composed card moves it on.
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { stage.position.coerceAtLeast(0f) },
                    maxValue = { (stage.count - 1).coerceAtLeast(0).toFloat() },
                )
                scrollBy { x, _ ->
                    if (stage.count == 0 || x == 0f) return@scrollBy false
                    val step = sign(x).toInt() * ACCESSIBILITY_PAGE
                    val target = (stage.selectedIndex + step).coerceIn(0, stage.count - 1)
                    if (target == stage.selectedIndex) return@scrollBy false
                    stage.select(target, scope)
                    currentOnTouchSelect(target)
                    true
                }
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else (width * 0.4f).roundToInt()
        val card = cardSizePx(width, height, density)
        stage.cardWidthPx = card.toFloat()
        val fixed = Constraints.fixed(card, card)
        val placeables = measurables.map { it.measure(fixed) to (it.layoutId as? Int ?: 0) }
        val top = ((height - card * (1f + STAGE_REFLECTION_SPACE)) / 2f).roundToInt().coerceAtLeast(0)
        layout(width, height) {
            // Placement only: reading the flow position here re-places cards each frame, no recomposition.
            val position = stage.position
            val pageShift = page.offset() * width
            for ((placeable, index) in placeables) {
                val d = index - position
                val x = width / 2f + CoverflowMath.offset(d) * card - card / 2f + pageShift
                placeable.place(x.roundToInt(), top, zIndex = -abs(d))
            }
        }
    }
}

private const val ACCESSIBILITY_PAGE = 3

/** Card side for a stage of [width] x [height] px: big and front-facing, bounded so neighbours stay visible. */
private fun cardSizePx(width: Int, height: Int, density: Float): Int {
    val byHeight = height / (1f + STAGE_REFLECTION_SPACE)
    val byWidth = width * 0.26f
    val max = 200f * density
    val min = 88f * density
    return minOf(byHeight, byWidth, max).coerceAtLeast(minOf(min, byHeight)).roundToInt().coerceAtLeast(1)
}

@Composable
private fun CoverCard(
    stage: CoverflowState,
    entry: AppEntry,
    index: Int,
    count: Int,
    focusId: String,
    version: Int,
    artPx: Int,
    placeholder: Color,
    page: PageMotion,
    onActivate: (Int, AppEntry) -> Unit,
    onActions: (AppEntry) -> Unit,
    onFocused: (Int, AppEntry) -> Unit,
    upTarget: () -> FocusRequester,
    downTarget: () -> FocusRequester,
) {
    val requester = remember { FocusRequester() }
    val anchor = remember { OriginAnchor() }
    DisposableEffect(stage, entry.key) {
        stage.requesters[entry.key] = requester
        stage.anchors[entry.key] = anchor
        onDispose {
            if (stage.requesters[entry.key] === requester) stage.requesters.remove(entry.key)
            if (stage.anchors[entry.key] === anchor) stage.anchors.remove(entry.key)
        }
    }
    val key = entry.key
    val enabled = entry.isEnabled
    Spacer(
        Modifier
            .layoutId(index)
            .graphicsLayer {
                val t = CoverflowMath.transform(index - stage.position)
                scaleX = t.scale
                scaleY = t.scale
                rotationY = t.rotationY
                cameraDistance = CAMERA_DISTANCE * density
                alpha = (t.alpha * page.alpha()).coerceIn(0f, 1f)
                // Per-card alpha without an offscreen buffer (the card and its reflection never overlap).
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithCache {
                stage.artTick
                val art = ConsoleArtCache.peek(key, artPx, version)
                val base = art?.color?.let(::Color) ?: placeholder
                val top = art?.color?.let { Color(ConsoleColors.darken(it, 0.92f)) } ?: placeholder
                val bottom = art?.color?.let { Color(ConsoleColors.darken(it, 0.42f)) } ?: placeholder
                val w = size.width
                val h = size.height
                val corner = CornerRadius(w * CARD_CORNER_FRACTION)
                val face = Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = h)
                val gloss = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.58f,
                )
                val gap = h * REFLECTION_GAP_FRACTION
                val reflectionH = h * REFLECTION_FRACTION
                val reflectionFace = Brush.verticalGradient(
                    listOf(base.copy(alpha = 0.30f), Color.Transparent),
                    startY = h + gap,
                    endY = h + gap + reflectionH,
                )
                val inset = (w * ICON_INSET_FRACTION).roundToInt()
                val iconSide = (w - inset * 2).roundToInt().coerceAtLeast(1)
                val reflectionImageH = (iconSide * REFLECTION_FRACTION).roundToInt().coerceAtLeast(1)
                val border = Stroke(width = 1.dp.toPx())
                onDrawBehind {
                    // Reflection beneath the card: a faded mirror of the face, then of the icon
                    // (pre-faded bitmap: no mask, no offscreen layer).
                    drawRoundRect(reflectionFace, Offset(0f, h + gap), Size(w, reflectionH), corner)
                    if (art != null) {
                        drawImage(
                            art.reflection,
                            dstOffset = IntOffset(inset, (h + gap).roundToInt() + inset),
                            dstSize = IntSize(iconSide, reflectionImageH),
                            alpha = if (enabled) 1f else 0.5f,
                        )
                    }
                    // The card face: tinted from the icon, the icon, a glossy top light and a fine edge.
                    drawRoundRect(face, cornerRadius = corner)
                    if (art != null) {
                        drawImage(
                            art.icon,
                            dstOffset = IntOffset(inset, inset),
                            dstSize = IntSize(iconSide, iconSide),
                            alpha = if (enabled) 1f else 0.5f,
                        )
                    }
                    drawRoundRect(gloss, cornerRadius = corner)
                    drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = corner, style = border)
                    // Side cards dim with distance (draw-phase read of the flow position).
                    val dim = CoverflowMath.transform(index - stage.position).dim
                    if (dim > 0.01f) {
                        drawRoundRect(Color.Black.copy(alpha = dim), cornerRadius = corner)
                        drawRoundRect(Color.Black.copy(alpha = dim), Offset(0f, h + gap), Size(w, reflectionH), corner)
                    }
                }
            }
            .then(OriginAnchorElement(anchor))
            .focusRequester(requester)
            .focusProperties {
                left = stage.requesterAt(index - 1)
                right = stage.requesterAt(index + 1)
                up = upTarget()
                down = downTarget()
            }
            .controllerFocusable(
                id = focusId,
                onActivate = { onActivate(index, entry) },
                onSecondary = { onActions(entry) },
                secondaryLabel = "Actions for ${entry.label}",
                contentDescription = "${entry.label}, ${index + 1} of $count",
                shape = CardShape,
                onFocused = { onFocused(index, entry) },
            ),
    )
}

// ---------------------------------------------------------------------------------------
// Origin of an app launch, resolved on demand
// ---------------------------------------------------------------------------------------

/**
 * Where a card is on screen, read only when it is activated (no per-frame position
 * callbacks while the flow glides).
 */
internal class OriginAnchor {
    internal var node: OriginAnchorNode? = null

    fun boundsInWindow(): Rect? {
        val n = node ?: return null
        if (!n.isAttached) return null
        val coordinates: LayoutCoordinates = n.requireLayoutCoordinates()
        return if (coordinates.isAttached) coordinates.boundsInWindow() else null
    }
}

private data class OriginAnchorElement(val anchor: OriginAnchor) : ModifierNodeElement<OriginAnchorNode>() {
    override fun create() = OriginAnchorNode(anchor)
    override fun update(node: OriginAnchorNode) {
        if (node.anchor !== anchor) {
            if (node.anchor.node === node) node.anchor.node = null
            node.anchor = anchor
            if (node.isAttached) anchor.node = node
        }
    }
}

internal class OriginAnchorNode(var anchor: OriginAnchor) : Modifier.Node() {
    override fun onAttach() {
        anchor.node = this
    }

    override fun onDetach() {
        if (anchor.node === this) anchor.node = null
    }
}
