package dev.pluto.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.ui.FolderUi
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.motion.LocalOriginRegistry
import dev.pluto.launcher.ui.motion.OriginRegistry
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.theme.PlutoDimens
import dev.pluto.launcher.ui.theme.overWallpaper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("IconCache not provided") }

private val DesaturateFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Alpha of an app that cannot currently be opened (suspended, disabled). */
private const val DisabledAlpha = 0.5f

/**
 * One snapshot-state mirror of [IconCache.versions] for the whole process, collected once,
 * instead of a flow and a coroutine per icon. Icons read the per-package counter in
 * composition; a package event (rare) recomposes the icons on screen.
 */
private object IconVersions {
    private var cache: IconCache? = null
    private var job: Job? = null
    private var state: MutableState<Map<String, Int>> = mutableStateOf(emptyMap())

    /** Main thread only (composition). */
    fun of(cache: IconCache): State<Map<String, Int>> {
        if (this.cache !== cache) {
            this.cache = cache
            job?.cancel()
            val mirror = mutableStateOf(cache.versions.value)
            state = mirror
            job = MainScope().launch { cache.versions.collect { mirror.value = it } }
        }
        return state
    }
}

/**
 * App icon from [LocalIconCache]. A cached icon is drawn synchronously by a single draw
 * node (no state, no coroutine, no animation); an icon that still has to be decoded shows a
 * neutral placeholder and fades in once it is ready (decoding happens off the main thread).
 * Decorative: no semantics (the tile carries the label).
 */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val cache = LocalIconCache.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    // A package update keeps the AppKey, so the cache's per-package version is part of the key:
    // after an update (or a decode that failed mid-install) the icon peeks and loads again.
    val version = IconVersions.of(cache).value[entry.packageName] ?: 0
    // peek() is a cheap memory-cache lookup; decoding only ever happens in load() off the main thread.
    val cached = remember(entry.key, sizePx, version) { safePeek(cache, entry, sizePx) }
    if (cached != null) {
        Spacer(modifier.size(size).then(IconImageElement(cached, enabled = entry.isEnabled)))
    } else {
        LoadingIcon(entry, size, sizePx, version, modifier)
    }
}

/** Draws a ready bitmap at the node's size; disabled apps are desaturated and dimmed. */
private data class IconImageElement(val image: ImageBitmap, val enabled: Boolean) : ModifierNodeElement<IconImageNode>() {
    override fun create() = IconImageNode(image, enabled)
    override fun update(node: IconImageNode) {
        node.image = image
        node.enabled = enabled
        node.invalidateDraw()
    }
}

private class IconImageNode(var image: ImageBitmap, var enabled: Boolean) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            alpha = if (enabled) 1f else DisabledAlpha,
            colorFilter = if (enabled) null else DesaturateFilter,
        )
    }
}

/**
 * The rare path: the icon is not cached yet. A placeholder shows at once; the decoded icon
 * fades in over it while settling from slightly smaller. All of it is drawn by one draw
 * lambda reading state, so loading never recomposes the tile.
 */
@Composable
private fun LoadingIcon(entry: AppEntry, size: Dp, sizePx: Int, version: Int, modifier: Modifier) {
    val cache = LocalIconCache.current
    val bitmap = remember(entry.key, sizePx, version) { mutableStateOf<ImageBitmap?>(null) }
    val reveal = remember(entry.key, sizePx) { Animatable(0f) }
    LaunchedEffect(entry.key, sizePx, version) {
        if (bitmap.value == null) {
            val loaded = safeLoad(cache, entry, sizePx)
            if (loaded != null) {
                // Uploads the texture ahead of the first draw instead of during it.
                loaded.prepareToDraw()
                bitmap.value = loaded
                reveal.animateTo(1f, PlutoMotion.fadeIn())
            }
        }
    }
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PlaceholderAlpha)
    val enabled = entry.isEnabled
    Spacer(
        modifier
            .size(size)
            .drawBehind {
                val r = reveal.value
                if (r < 1f) {
                    val radius = this.size.minDimension * PlaceholderCorner
                    drawRoundRect(placeholderColor, cornerRadius = CornerRadius(radius, radius), alpha = 1f - r)
                }
                val image = bitmap.value ?: return@drawBehind
                val s = 0.92f + 0.08f * r
                scale(s) {
                    drawImage(
                        image = image,
                        dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                        alpha = r * (if (enabled) 1f else DisabledAlpha),
                        colorFilter = if (enabled) null else DesaturateFilter,
                    )
                }
            },
    )
}

private const val PlaceholderAlpha = 0.6f
private const val PlaceholderCorner = 0.3f

private fun safePeek(cache: IconCache, entry: AppEntry, sizePx: Int): ImageBitmap? =
    try {
        cache.peek(entry.key, sizePx)
    } catch (e: RuntimeException) {
        null
    }

private suspend fun safeLoad(cache: IconCache, entry: AppEntry, sizePx: Int): ImageBitmap? =
    try {
        cache.load(entry.key, sizePx)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A vanished or misbehaving package must never take the launcher down.
        null
    }

/** Screen-reader label for an app, noting when it cannot currently be opened. */
fun appContentDescription(entry: AppEntry): String =
    if (entry.isEnabled) entry.label else "${entry.label} (unavailable)"

private val TileShape = RoundedCornerShape(PlutoDimens.TileCorner)
private val TileLabelGap = 4.dp

/**
 * Where a tile was last laid out, reported to [OriginRegistry] only when it matters: when
 * the tile is activated (launch, open, actions), and from then on whenever it moves (so a
 * layer it opened can return to it). Tiles nobody activated do no per-frame work while a
 * grid scrolls.
 */
internal class TileOrigin {
    var registry: OriginRegistry? = null
    var id: String = ""
    private var coordinates: LayoutCoordinates? = null
    private var tracking = false

    fun report() {
        val c = coordinates?.takeIf { it.isAttached } ?: return
        registry?.report(id, c.boundsInWindow())
        tracking = true
    }

    fun placed(c: LayoutCoordinates) {
        coordinates = c
        if (tracking) report()
    }
}

private data class TileOriginElement(val origin: TileOrigin) : ModifierNodeElement<TileOriginNode>() {
    override fun create() = TileOriginNode(origin)
    override fun update(node: TileOriginNode) {
        node.origin = origin
    }
}

private class TileOriginNode(var origin: TileOrigin) : Modifier.Node(), LayoutAwareModifierNode {
    override fun onPlaced(coordinates: LayoutCoordinates) = origin.placed(coordinates)
}

@Composable
private fun rememberTileOrigin(id: String): TileOrigin {
    val origin = remember { TileOrigin() }
    origin.registry = LocalOriginRegistry.current
    origin.id = id
    return origin
}

/**
 * Standard app tile: icon + label (max 2 lines, ellipsis), controller-focusable via
 * Modifier.controllerFocusable(id = entry.key.encode(), ...), min 48dp target.
 * Disabled (suspended) apps render dimmed with "(unavailable)" in the a11y label.
 * [onActions] runs for X, a long press and the screen-reader action; [actionsOnTap] tells
 * screen readers that a tap opens the actions (the drawer's actions mode).
 *
 * Kept deliberately light, since a fast fling composes several rows in one frame: the icon
 * and the label are single nodes measured once (no subcomposition), and the tile's
 * position is only read when it is activated.
 */
@Composable
fun AppTile(
    entry: AppEntry,
    iconSize: Dp,
    onLaunch: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier,
    focusId: String = entry.key.encode(),
    showLabel: Boolean = true,
    onFocused: (() -> Unit)? = null,
    actionsOnTap: Boolean = false,
) {
    val press = remember { MutableInteractionSource() }
    val origin = rememberTileOrigin(focusId)
    Column(
        modifier
            // Unscaled bounds: launches and folders grow out of the tile's resting rectangle.
            .then(TileOriginElement(origin))
            .pressScale(press)
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(
                id = focusId,
                onActivate = {
                    origin.report()
                    onLaunch()
                },
                onSecondary = {
                    origin.report()
                    onActions()
                },
                secondaryLabel = "App actions",
                contentDescription = if (actionsOnTap) appContentDescription(entry) + ", opens app actions" else appContentDescription(entry),
                shape = TileShape,
                onFocused = onFocused,
                interactionSource = press,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(entry, iconSize)
        if (showLabel) TileLabel(entry.label, Modifier.padding(top = TileLabelGap), dimmed = !entry.isEnabled)
    }
}

/**
 * Two-line, centred tile label readable over the wallpaper. Semantics come from the tile itself.
 *
 * At enlarged text a single long word ("Settings", "Calendar") can be wider than the tile and
 * would be broken mid-word. The label then shrinks just enough for its longest word to fit,
 * but never below [MinLabelShrink] of the user's text size; anything longer still ellipsizes.
 *
 * One layout node does all of it: it lays the text out once in its measure pass (a second
 * time only when a word really had to be split) and draws the result, so a tile costs no
 * subcomposition and no per-tile text measurer.
 */
@Composable
fun TileLabel(text: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val style = MaterialTheme.typography.labelMedium.overWallpaper().copy(textAlign = TextAlign.Center)
    Spacer(modifier.fillMaxWidth().then(TileLabelElement(text, style, if (dimmed) DimmedLabelAlpha else 1f)))
}

/** Smallest fraction of the label's text size used to fit a long word on one line. */
private const val MinLabelShrink = 0.7f
private const val DimmedLabelAlpha = 0.6f
private const val LabelMaxLines = 2

private data class TileLabelElement(val text: String, val style: TextStyle, val alpha: Float) : ModifierNodeElement<TileLabelNode>() {
    override fun create() = TileLabelNode(text, style, alpha)
    override fun update(node: TileLabelNode) = node.update(text, style, alpha)
}

private class TileLabelNode(
    private var text: String,
    private var style: TextStyle,
    private var alpha: Float,
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var result: TextLayoutResult? = null
    private var resultWidth = -1
    private var resultDensity: Density? = null

    fun update(text: String, style: TextStyle, alpha: Float) {
        if (text != this.text || style != this.style) {
            this.text = text
            this.style = style
            result = null
            invalidateMeasurement()
        }
        if (alpha != this.alpha) {
            this.alpha = alpha
            invalidateDraw()
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        // The composition's density object (with the user's text scale and Android's
        // non-linear font scaling); equal objects let the shared layout cache hit across tiles.
        val density = currentValueOf(LocalDensity)
        val cachedResult = result
        val r = if (cachedResult != null && resultWidth == maxWidth && resultDensity == density &&
            cachedResult.layoutInput.layoutDirection == layoutDirection
        ) {
            cachedResult
        } else {
            val resolver = currentValueOf(LocalFontFamilyResolver)
            layoutLabel(text, style, maxWidth, sharedMeasurer(resolver, density), density).also {
                result = it
                resultWidth = maxWidth
                resultDensity = density
            }
        }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.constrainWidth(r.size.width)
        val height = constraints.constrainHeight(r.size.height)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        return layout(width, height) { placeable.place(0, 0) }
    }

    override fun ContentDrawScope.draw() {
        val r = result ?: return
        val color = if (style.color.isSpecified) style.color else Color.Black
        drawText(r, color = color, alpha = alpha, shadow = style.shadow)
    }
}

/**
 * One measurer for every tile label: its layout cache also makes tiles that scroll back
 * into view (new nodes, same text) cheap.
 */
private var labelMeasurer: TextMeasurer? = null
private var labelMeasurerResolver: FontFamily.Resolver? = null

private fun MeasureScope.sharedMeasurer(resolver: FontFamily.Resolver, density: Density): TextMeasurer {
    val existing = labelMeasurer
    if (existing != null && labelMeasurerResolver === resolver) return existing
    return TextMeasurer(resolver, density, layoutDirection, cacheSize = LabelCacheSize).also {
        labelMeasurer = it
        labelMeasurerResolver = resolver
    }
}

private const val LabelCacheSize = 256

private fun MeasureScope.layoutLabel(
    text: String,
    style: TextStyle,
    maxWidth: Int,
    measurer: TextMeasurer,
    density: Density,
): TextLayoutResult {
    val constraints = if (maxWidth == Constraints.Infinity) Constraints() else Constraints(minWidth = maxWidth, maxWidth = maxWidth)
    fun measure(s: TextStyle) = measurer.measure(
        text = text,
        style = s,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        maxLines = LabelMaxLines,
        constraints = constraints,
        layoutDirection = layoutDirection,
        density = density,
    )
    val first = measure(style)
    if (maxWidth == Constraints.Infinity || !mayHaveSplitWord(text, first)) return first
    val longest = text.split(' ').maxByOrNull { it.length }.orEmpty()
    if (longest.isEmpty()) return first
    val wordWidth = measurer.measure(
        text = longest,
        style = style,
        maxLines = 1,
        softWrap = false,
        layoutDirection = layoutDirection,
        density = density,
    ).size.width
    if (wordWidth <= maxWidth) return first
    val factor = (maxWidth.toFloat() / wordWidth).coerceAtLeast(MinLabelShrink)
    return measure(
        style.copy(
            fontSize = style.fontSize * factor,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * factor else style.lineHeight,
        ),
    )
}

/**
 * True when the label may have broken a word: a line ends inside a word, or the last line
 * was ellipsized (the cut word might fit when shrunk). Only then is the longest word measured.
 */
private fun mayHaveSplitWord(text: String, r: TextLayoutResult): Boolean {
    val lines = r.lineCount
    for (i in 0 until lines - 1) {
        val end = r.getLineEnd(i)
        if (end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace() && text[end - 1] != '-') {
            return true
        }
    }
    return lines > 0 && r.isLineEllipsized(lines - 1)
}

/**
 * Folder tile: a rounded container previewing up to four member icons, with the folder
 * name below. [onOpen] opens the folder; [onEdit] (X / custom action) edits it.
 */
@Composable
fun FolderTile(
    folder: FolderUi,
    iconSize: Dp,
    focusId: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onFocused: (() -> Unit)? = null,
) {
    val press = remember { MutableInteractionSource() }
    val origin = rememberTileOrigin(focusId)
    val count = folder.apps.size
    val description = "Folder ${folder.folder.name}, " + when (count) {
        0 -> "empty"
        1 -> "1 app"
        else -> "$count apps"
    }
    Column(
        modifier
            .then(TileOriginElement(origin))
            .pressScale(press)
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(
                id = focusId,
                onActivate = {
                    origin.report()
                    onOpen()
                },
                onSecondary = {
                    origin.report()
                    onEdit()
                },
                secondaryLabel = "Edit folder",
                contentDescription = description,
                shape = TileShape,
                onFocused = onFocused,
                interactionSource = press,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * 0.3f))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = PlutoDimens.TileBackdropAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            val preview = folder.apps.take(4)
            if (preview.isEmpty()) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(iconSize * 0.55f),
                )
            } else {
                val mini = iconSize * 0.38f
                Column(verticalArrangement = Arrangement.spacedBy(iconSize * 0.06f)) {
                    preview.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(iconSize * 0.06f)) {
                            row.forEach { AppIcon(it, mini) }
                            if (row.size == 1) Spacer(Modifier.size(mini))
                        }
                    }
                }
            }
        }
        if (showLabel) TileLabel(folder.folder.name, Modifier.padding(top = TileLabelGap))
    }
}
