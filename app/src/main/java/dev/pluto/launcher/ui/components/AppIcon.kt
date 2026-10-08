package dev.pluto.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.ui.FolderUi
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.motion.LocalOriginRegistry
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.motion.reportOrigin
import dev.pluto.launcher.ui.theme.PlutoDimens
import dev.pluto.launcher.ui.theme.overWallpaper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("IconCache not provided") }

private val DesaturateFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** App icon loaded off the main thread via [LocalIconCache]; shows a neutral placeholder meanwhile. Decorative (no semantics). */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val cache = LocalIconCache.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    // A package update keeps the AppKey, so the cache's per-package version is part of the key:
    // after an update (or a decode that failed mid-install) the tile peeks and loads again.
    // Mapped per package so one package's update only recomposes that package's icons.
    val packageName = entry.packageName
    val version by remember(cache, packageName) {
        cache.versions.map { it[packageName] ?: 0 }.distinctUntilChanged()
    }.collectAsState(initial = cache.versionOf(packageName))
    // peek() is a cheap memory-cache lookup; decoding only ever happens in load() off the main thread.
    var bitmap by remember(entry.key, sizePx, version) { mutableStateOf(safePeek(cache, entry, sizePx)) }
    // A cached icon shows at once; only an icon that had to be loaded fades in over the placeholder.
    // Kept across version bumps (app updates) so a re-load of a visible icon does not flash.
    val reveal = remember(entry.key, sizePx) { Animatable(if (bitmap != null) 1f else 0f) }
    LaunchedEffect(entry.key, sizePx, version) {
        if (bitmap == null) {
            val loaded = safeLoad(cache, entry, sizePx)
            if (loaded != null) {
                bitmap = loaded
                reveal.animateTo(1f, PlutoMotion.fadeIn())
            }
        }
    }
    // Suspended / disabled apps dim smoothly when their availability changes.
    val enabledAlpha = animateFloatAsState(if (entry.isEnabled) 1f else 0.5f, PlutoMotion.fadeIn(), label = "iconDim")
    val image = bitmap
    if (image == null) {
        IconPlaceholder(size, modifier)
        return
    }
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PlaceholderAlpha)
    Image(
        bitmap = image,
        contentDescription = null,
        colorFilter = if (entry.isEnabled) null else DesaturateFilter,
        modifier = modifier
            .size(size)
            .drawBehind {
                // The placeholder fades out underneath while the icon fades in (no extra node).
                val r = reveal.value
                if (r < 1f) {
                    val radius = this.size.minDimension * PlaceholderCorner
                    drawRoundRect(placeholderColor, cornerRadius = CornerRadius(radius, radius), alpha = 1f - r)
                }
            }
            .graphicsLayer {
                val r = reveal.value
                alpha = enabledAlpha.value * r
                // Settles from slightly smaller as it fades in (visual only).
                val s = 0.92f + 0.08f * r
                scaleX = s
                scaleY = s
            },
    )
}

private const val PlaceholderAlpha = 0.6f
private const val PlaceholderCorner = 0.3f

@Composable
private fun IconPlaceholder(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * PlaceholderCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PlaceholderAlpha)),
    )
}

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

/**
 * Standard app tile: icon + label (max 2 lines, ellipsis), controller-focusable via
 * Modifier.controllerFocusable(id = entry.key.encode(), ...), min 48dp target.
 * Disabled (suspended) apps render dimmed with "(unavailable)" in the a11y label.
 * [onActions] runs for X, a long press and the screen-reader action; [actionsOnTap] tells
 * screen readers that a tap opens the actions (the drawer's Actions mode).
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
    Column(
        modifier
            // Unscaled bounds: launches and folders grow out of the tile's resting rectangle.
            .reportOrigin(LocalOriginRegistry.current, focusId)
            .pressScale(press)
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(
                id = focusId,
                onActivate = onLaunch,
                onSecondary = onActions,
                secondaryLabel = "App actions",
                contentDescription = appContentDescription(entry) + if (actionsOnTap) ", opens app actions" else "",
                shape = RoundedCornerShape(PlutoDimens.TileCorner),
                onFocused = onFocused,
                interactionSource = press,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(entry, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            TileLabel(entry.label, dimmed = !entry.isEnabled)
        }
    }
}

/**
 * Two-line, centred tile label readable over the wallpaper. Semantics come from the tile itself.
 *
 * At enlarged text a single long word ("Settings", "Calendar") can be wider than the tile and
 * would be broken mid-word. The label then shrinks just enough for its longest word to fit,
 * but never below [MinLabelShrink] of the user's text size; anything longer still ellipsizes.
 */
@Composable
fun TileLabel(text: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val baseStyle = MaterialTheme.typography.labelMedium.overWallpaper()
    val labelAlpha = animateFloatAsState(if (dimmed) 0.6f else 1f, PlutoMotion.fadeIn(), label = "labelDim")
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val style = remember(text, baseStyle, maxWidthPx) {
            val longest = text.split(' ').maxByOrNull { it.length }.orEmpty()
            if (longest.isEmpty() || maxWidthPx <= 0 || maxWidthPx == Constraints.Infinity) {
                baseStyle
            } else {
                val wordWidth = measurer.measure(longest, baseStyle, maxLines = 1, softWrap = false).size.width
                if (wordWidth <= maxWidthPx) {
                    baseStyle
                } else {
                    val factor = (maxWidthPx.toFloat() / wordWidth).coerceAtLeast(MinLabelShrink)
                    baseStyle.copy(
                        fontSize = baseStyle.fontSize * factor,
                        lineHeight = if (baseStyle.lineHeight.isSpecified) baseStyle.lineHeight * factor else baseStyle.lineHeight,
                    )
                }
            }
        }
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = labelAlpha.value }
                .clearAndSetSemantics { },
        )
    }
}

/** Smallest fraction of the label's text size used to fit a long word on one line. */
private const val MinLabelShrink = 0.7f

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
    val count = folder.apps.size
    val description = "Folder ${folder.folder.name}, " + when (count) {
        0 -> "empty"
        1 -> "1 app"
        else -> "$count apps"
    }
    Column(
        modifier
            .reportOrigin(LocalOriginRegistry.current, focusId)
            .pressScale(press)
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(
                id = focusId,
                onActivate = onOpen,
                onSecondary = onEdit,
                secondaryLabel = "Edit folder",
                contentDescription = description,
                shape = RoundedCornerShape(PlutoDimens.TileCorner),
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
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            TileLabel(folder.folder.name)
        }
    }
}
