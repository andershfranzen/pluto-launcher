package dev.pluto.launcher.ui.console

import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.staticCompositionLocalOf
import dev.pluto.launcher.data.prefs.CarouselStyle
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
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
/** Every console card's focus id starts with this. */
internal const val CARD_ID_PREFIX = "hh:card:"

internal fun cardFocusId(shelfKey: String, key: AppKey): String = "$CARD_ID_PREFIX$shelfKey:${key.encode()}"

/** Card shape (also the focus ring's shape). */
private val CardShape = RoundedCornerShape(26.dp)
private const val CARD_CORNER_FRACTION = 0.15f

/** The shared card material: neutral frosted dark glass, the same for every app. */
private val CardFaceTop = Color(0xE02A2E37)
private val CardFaceBottom = Color(0xE01B1E25)

/**
 * Room under the card for its reflection, in card heights: the gap and the visible depth of
 * the mirror.
 */
private const val STAGE_REFLECTION_SPACE = CardLook.REFLECTION_GAP + CardLook.REFLECTION_DEPTH

/** Clear space between the end of the reflection and whatever sits under the stage (the title). */
private val ReflectionClearance = 8.dp

/**
 * Camera distance in card widths: far enough that a tilted card is a gently foreshortened
 * plane (its icon keeps its shape) rather than a strongly keystoned trapezoid.
 */
private const val CAMERA_DISTANCE_CARDS = 7.5f

/** White focus glow around the centre card while it holds controller focus: (distance dp, alpha). */
private val FocusGlowSteps = floatArrayOf(3f, 0.30f, 7f, 0.14f, 12f, 0.06f, 18f, 0.025f)

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
 * How present the centre card is: [emphasis] 1 while the flow (or nothing) holds focus, 0
 * while controller focus is up in the header, so A clearly won't open it; [lift] is a short
 * extra scale when focus comes back down; [focus] 1 while the flow holds controller focus
 * (the centre card then glows white around its ring). All are read in layer/draw only.
 */
@Stable
internal class HeroEmphasis {
    var emphasis by mutableFloatStateOf(1f)
    var lift by mutableFloatStateOf(0f)
    var focus by mutableFloatStateOf(0f)

    companion object {
        val Static = HeroEmphasis()
        const val IDLE_SCALE = 0.92f
        const val IDLE_DIM = 0.32f
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
    hero: HeroEmphasis,
    onActivate: (index: Int, entry: AppEntry) -> Unit,
    onActions: (entry: AppEntry) -> Unit,
    onFocused: (index: Int, entry: AppEntry) -> Unit,
    onTouchSelect: (index: Int) -> Unit,
    upTarget: () -> FocusRequester,
    downTarget: () -> FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val style = LocalCarouselStyle.current
    // Right-to-left: the flow runs the other way (next card on the left), mirrored as a whole.
    val mirror = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val artPx = with(LocalDensity.current) { ConsoleArtSize.roundToPx() }
    val window by remember(stage) {
        derivedStateOf { CoverflowMath.window(stage.position, stage.selectedIndex, stage.count) }
    }
    val currentVersions by rememberUpdatedState(versions)
    val currentOnTouchSelect by rememberUpdatedState(onTouchSelect)

    // Card art: nearest cards first, then a little ahead either side; conflated so a held
    // D-pad never piles up work (only the newest window is prepared next). Restarted when an
    // app's icon version changes (an update), so its card gets new art without a move.
    LaunchedEffect(stage, artPx, icons, versions) {
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
    // The composed cards, resolved before the loop: key() must be the loop body's only
    // content. With a `?: continue` ahead of it, each iteration got its own positional group
    // and the keyed card sat inside it, so when the window's first index advanced every card
    // was disposed and recreated, including the one that had just taken focus; Compose then
    // cleared focus and re-entered at the first tab (a held D-pad wandered into the header).
    val visible = remember(window, apps) { window.mapNotNull { i -> apps.getOrNull(i)?.let { IndexedValue(i, it) } } }
    Layout(
        content = {
            for ((index, entry) in visible) {
                key(entry.key) {
                    CoverCard(
                        stage = stage,
                        entry = entry,
                        index = index,
                        count = count,
                        mirror = mirror,
                        focusId = cardFocusId(shelfKey, entry.key),
                        version = versions[entry.packageName] ?: 0,
                        artPx = artPx,
                        placeholder = placeholder,
                        page = page,
                        hero = hero,
                        onActivate = onActivate,
                        onActions = onActions,
                        onFocused = onFocused,
                        upTarget = upTarget,
                        downTarget = downTarget,
                    )
                }
            }
            // A short shelf still reads as a shelf: faint empty slots after its last card.
            for (slot in CoverflowMath.ghostSlots(count)) {
                key(GhostKey(slot)) { GhostCard(stage, slot, page, mirror) }
            }
        },
        modifier = modifier
            .pointerInput(stage, mirror) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        stage.dragStart()
                    },
                    onDragEnd = {
                        val vx = tracker.calculateVelocity().x
                        val width = stage.cardWidthPx * CoverflowMath.FIRST_GAP
                        val cardsPerSecond = if (width > 0f) -mirror * vx / width else 0f
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
                    stage.dragBy(CoverflowMath.dragDelta(mirror * dx, stage.cardWidthPx))
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
        val clearance = ReflectionClearance.roundToPx()
        // Breathing room under the top bar: the flow never butts against the shelf tabs.
        val headroom = StageHeadroom.roundToPx()
        val card = cardSizePx(width, height - clearance - headroom, density)
        stage.cardWidthPx = card.toFloat()
        val fixed = Constraints.fixed(card, card)
        val placeables = measurables.map { it.measure(fixed) to (it.layoutId as? Int ?: 0) }
        // Card plus its reflection plus the clearance, a little below centre (the free space is
        // shared [STAGE_VERTICAL_BIAS] above, the rest below): the mirror never reaches the title.
        val free = height - headroom - clearance - card * (1f + STAGE_REFLECTION_SPACE)
        val top = headroom + (free * STAGE_VERTICAL_BIAS).roundToInt().coerceAtLeast(0)
        layout(width, height) {
            // Placement only: reading the flow position here re-places cards each frame, no recomposition.
            val position = stage.position
            val pageShift = page.offset() * width
            for ((placeable, index) in placeables) {
                val d = index - position
                val x = width / 2f + mirror * CoverflowMath.offset(d, style) * card - card / 2f + pageShift
                placeable.place(x.roundToInt(), top, zIndex = if (style == CarouselStyle.COVERFLOW) -abs(d) else CoverflowMath.transform(d, style).z)
            }
        }
    }
}

private const val ACCESSIBILITY_PAGE = 3

/** Share of the stage's free height above the cards (0.5 = centred); the flow sits a bit low. */
private const val STAGE_VERTICAL_BIAS = 0.7f

/** Space kept free above the cards, below the top bar (small: the cards get the height). */
private val StageHeadroom = 16.dp

/** Card side for a stage of [width] x [height] px: big and front-facing, bounded so neighbours stay visible. */
private fun cardSizePx(width: Int, height: Int, density: Float): Int {
    val byHeight = height / (1f + STAGE_REFLECTION_SPACE)
    val byWidth = width * 0.26f
    val max = 220f * density
    val min = 88f * density
    return minOf(byHeight, byWidth, max).coerceAtLeast(minOf(min, byHeight)).roundToInt().coerceAtLeast(1)
}

@Composable
private fun CoverCard(
    stage: CoverflowState,
    entry: AppEntry,
    index: Int,
    count: Int,
    mirror: Float,
    focusId: String,
    version: Int,
    artPx: Int,
    placeholder: Color,
    page: PageMotion,
    hero: HeroEmphasis,
    onActivate: (Int, AppEntry) -> Unit,
    onActions: (AppEntry) -> Unit,
    onFocused: (Int, AppEntry) -> Unit,
    upTarget: () -> FocusRequester,
    downTarget: () -> FocusRequester,
) {
    val style = LocalCarouselStyle.current
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
    fun heroDim(i: Int): Float = if (i == stage.selectedIndex) (1f - hero.emphasis) * HeroEmphasis.IDLE_DIM else 0f
    Spacer(
        Modifier
            .layoutId(index)
            .graphicsLayer {
                val t = CoverflowMath.transform(index - stage.position, style)
                val heroScale = if (index == stage.selectedIndex) {
                    HeroEmphasis.IDLE_SCALE + (1f - HeroEmphasis.IDLE_SCALE) * hero.emphasis + hero.lift
                } else {
                    1f
                }
                applyCardLayer(t, heroScale, page, mirror)
            }
            .drawWithCache {
                stage.artTick
                val art = ConsoleArtCache.peek(key, artPx, version)
                val look = CardPaint(size.width, size.height, this)
                val iconAlpha = if (enabled) 1f else 0.5f
                onDrawBehind {
                    // Side cards dim with distance, and the hero dims while focus is up in the
                    // header (draw-phase reads of the flow position and the emphasis).
                    val dim = maxOf(CoverflowMath.transform(index - stage.position, style).dim, heroDim(index))
                    val glow = if (index == stage.selectedIndex) hero.focus * hero.emphasis else 0f
                    if (glow > 0.01f) look.drawGlow(this, glow)
                    look.drawReflection(this, art, (1f - dim) * iconAlpha)
                    look.drawFace(this)
                    if (art != null) {
                        drawImage(art.icon, dstOffset = look.iconOffset, dstSize = look.iconSize, alpha = iconAlpha)
                    } else {
                        drawCircle(placeholder, radius = look.iconSize.width * 0.42f)
                    }
                    look.drawRim(this)
                    if (dim > 0.01f) drawRoundRect(Color.Black.copy(alpha = dim), cornerRadius = look.corner)
                }
            }
            .then(OriginAnchorElement(anchor))
            .focusRequester(requester)
            .focusProperties {
                // The D-pad follows the screen: in right-to-left the next card is on the left.
                val step = if (mirror < 0f) -1 else 1
                left = stage.requesterAt(index - step)
                right = stage.requesterAt(index + step)
                up = upTarget()
                down = downTarget()
            }
            .controllerFocusable(
                id = focusId,
                onActivate = { onActivate(index, entry) },
                onSecondary = { onActions(entry) },
                secondaryLabel = "Actions for ${entry.label}",
                contentDescription = "${entry.label}${if (enabled) "" else ", unavailable"}, ${index + 1} of $count",
                shape = CardShape,
                onFocused = { onFocused(index, entry) },
            ),
    )
}

/** The console carousel's layout style; provided by the console from settings. */
internal val LocalCarouselStyle = staticCompositionLocalOf { CarouselStyle.COVERFLOW }

/** Layer properties shared by real and ghost cards: the whole card turns as one plane about its centre. */
private fun GraphicsLayerScope.applyCardLayer(t: CoverflowMath.CardTransform, extraScale: Float, page: PageMotion, mirror: Float) {
    scaleX = t.scale * extraScale
    scaleY = t.scale * extraScale
    rotationY = t.rotationY * mirror
    rotationZ = t.rotationZ * mirror
    translationY = t.offsetY * size.width
    // In camera units (72 px each), proportional to the card so every screen sees the same tilt.
    cameraDistance = CAMERA_DISTANCE_CARDS * size.width / 72f
    alpha = (t.alpha * page.alpha()).coerceIn(0f, 1f)
    // Per-card alpha without an offscreen buffer (the card and its reflection never overlap).
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/**
 * The card material for one card size, built once in drawWithCache: the neutral dark-glass
 * face with a subtle top highlight and rim, the centred icon's place, the mirrored face with
 * its eased fade, and the white focus glow.
 */
private class CardPaint(w: Float, h: Float, density: Density) {
    val corner = CornerRadius(w * CARD_CORNER_FRACTION)
    private val size = Size(w, h)
    private val face = Brush.verticalGradient(listOf(CardFaceTop, CardFaceBottom), startY = 0f, endY = h)
    private val highlight = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.02f), Color.Transparent),
        startY = 0f,
        endY = h * 0.45f,
    )
    private val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f)),
        startY = 0f,
        endY = h * 0.4f,
    )
    private val rimStroke = Stroke(width = with(density) { 1.dp.toPx() })
    private val inset = (w * CardLook.ICON_INSET).roundToInt()
    val iconOffset = IntOffset(inset, inset)
    val iconSize = IntSize((w - inset * 2).roundToInt().coerceAtLeast(1), (h - inset * 2).roundToInt().coerceAtLeast(1))
    private val reflectionTop = h * (1f + CardLook.REFLECTION_GAP)
    private val reflectionDepth = h * CardLook.REFLECTION_DEPTH

    /** The mirrored face: the card's bottom edge (and its corners) on top, eased out to nothing. */
    private val mirroredFace = Brush.verticalGradient(
        0f to CardFaceBottom.copy(alpha = CardLook.fade(0f)),
        0.25f to CardFaceBottom.copy(alpha = CardLook.fade(CardLook.REFLECTION_DEPTH * 0.25f)),
        0.5f to CardFaceBottom.copy(alpha = CardLook.fade(CardLook.REFLECTION_DEPTH * 0.5f)),
        0.75f to CardFaceBottom.copy(alpha = CardLook.fade(CardLook.REFLECTION_DEPTH * 0.75f)),
        1f to Color.Transparent,
        startY = reflectionTop,
        endY = reflectionTop + reflectionDepth,
    )
    private val mirroredRim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
        startY = reflectionTop,
        endY = reflectionTop + reflectionDepth * 0.5f,
    )
    private val reflectedIconOffset = IntOffset(inset, (reflectionTop + inset).roundToInt())
    private val reflectedIconHeight = ((h - inset * 2) * CardLook.ICON_REFLECTED).roundToInt().coerceAtLeast(1)
    private val glowReach = FloatArray(FocusGlowSteps.size / 2) { with(density) { FocusGlowSteps[it * 2].dp.toPx() } }

    fun drawFace(scope: DrawScope) = with(scope) {
        drawRoundRect(face, cornerRadius = corner)
        drawRoundRect(highlight, cornerRadius = corner)
    }

    fun drawRim(scope: DrawScope) = with(scope) {
        drawRoundRect(rim, cornerRadius = corner, style = rimStroke)
    }

    /** The whole face mirrored below the card: the face (with its edge), then the icon's pre-faded strip. */
    fun drawReflection(scope: DrawScope, art: ConsoleArt?, alpha: Float) = with(scope) {
        if (alpha <= 0.01f) return@with
        drawRoundRect(mirroredFace, Offset(0f, reflectionTop), size, corner, alpha = alpha)
        drawRoundRect(mirroredRim, Offset(0f, reflectionTop), size, corner, style = rimStroke, alpha = alpha)
        val strip = art?.reflection ?: return@with
        drawImage(
            strip,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(strip.width, strip.height),
            dstOffset = reflectedIconOffset,
            dstSize = IntSize(iconSize.width, reflectedIconHeight),
            alpha = alpha,
        )
    }

    /** Soft white glow just outside the card (the gliding ring itself is drawn by the focus overlay). */
    fun drawGlow(scope: DrawScope, strength: Float) = with(scope) {
        var reach = 0f
        for (i in glowReach.indices) {
            val width = glowReach[i] - reach
            val centre = reach + width / 2f
            drawRoundRect(
                Color.White.copy(alpha = FocusGlowSteps[i * 2 + 1] * strength),
                topLeft = Offset(-centre, -centre),
                size = Size(size.width + centre * 2f, size.height + centre * 2f),
                cornerRadius = CornerRadius(corner.x + centre),
                style = Stroke(width),
            )
            reach = glowReach[i]
        }
    }
}

/** Identity of a ghost slot in the stage's keyed content (never equal to an app's key). */
private data class GhostKey(val slot: Int)

/** A placeholder slot after a short shelf's last card: a faint outline on the same plane, never focusable. */
@Composable
private fun GhostCard(stage: CoverflowState, slot: Int, page: PageMotion, mirror: Float) {
    val style = LocalCarouselStyle.current
    Spacer(
        Modifier
            .layoutId(slot)
            .graphicsLayer {
                val t = CoverflowMath.transform(slot - stage.position, style)
                applyCardLayer(t, 1f, page, mirror)
                alpha *= GHOST_ALPHA
            }
            .drawWithCache {
                val corner = CornerRadius(size.width * CARD_CORNER_FRACTION)
                val fill = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.015f)),
                    startY = 0f,
                    endY = size.height,
                )
                val outline = Stroke(width = 1.5.dp.toPx())
                onDrawBehind {
                    drawRoundRect(fill, cornerRadius = corner)
                    drawRoundRect(Color.White.copy(alpha = 0.20f), cornerRadius = corner, style = outline)
                }
            },
    )
}

/** Ghost slots are this much fainter than a card at the same place. */
private const val GHOST_ALPHA = 0.55f

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
