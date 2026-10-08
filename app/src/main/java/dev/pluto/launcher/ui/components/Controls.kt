package dev.pluto.launcher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.ui.UserMessage
import dev.pluto.launcher.ui.focus.LocalFocusInert
import dev.pluto.launcher.ui.focus.focusInert
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.theme.PlutoDimens
import dev.pluto.launcher.ui.theme.overWallpaper
import dev.pluto.launcher.ui.theme.panelColor
import dev.pluto.launcher.ui.theme.sheetRimBrush
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Standard panel used by sheets, folders, the dock and menus: the brand-tinted surface
 * ([panelColor]) with a faint light rim along its top edge, so every surface reads as one family.
 */
@Composable
fun PlutoPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(PlutoDimens.PanelCorner)
    Surface(
        modifier = modifier,
        shape = shape,
        color = panelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, sheetRimBrush()),
        content = content,
    )
}

/**
 * Icon button with a screen-reader [label]; when [showLabel] is true the label is also
 * shown next to the icon (pill style). Always at least 48dp. [onSecondary] (optional) runs
 * for a long press, the controller's X and an accessibility custom action named [secondaryLabel].
 */
@Composable
fun PlutoIconButton(
    id: String,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    onWallpaper: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
) {
    val shape = if (showLabel) RoundedCornerShape(24.dp) else CircleShape
    val background = if (onWallpaper) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(press)
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(
                id = id,
                onActivate = onClick,
                onSecondary = onSecondary,
                secondaryLabel = secondaryLabel,
                contentDescription = label,
                shape = shape,
                onFocused = onFocused,
                interactionSource = press,
            )
            .clip(shape)
            .background(background)
            // A hairline edge, so the disc reads the same over light and dark parts of the
            // wallpaper (over a light area the translucent disc alone disappeared).
            .then(if (onWallpaper) Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape) else Modifier)
            .padding(horizontal = if (showLabel) 14.dp else 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** Text button in Pluto style (tonal pill), controller-focusable. */
@Composable
fun PlutoTextButton(
    id: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
    keyHint: String? = null,
    outline: Color? = null,
) {
    val shape = RoundedCornerShape(24.dp)
    val container = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(press)
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(id = id, onActivate = onClick, contentDescription = text, shape = shape, interactionSource = press)
            .clip(shape)
            .background(container)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (keyHint != null) {
            // The controller button that does the same (console: "Ⓐ Open"), in place of the icon.
            KeyChip(keyHint)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Section title, marked as a heading for screen readers. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, onWallpaper: Boolean = true) {
    val base = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    Text(
        text = text,
        style = if (onWallpaper) base.overWallpaper() else base,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

/** Small rounded chip showing a controller key name, e.g. "A" or "L1". */
@Composable
fun KeyChip(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
        )
    }
}

/**
 * Category tabs. Touch-selectable; L1/R1 switching is handled by the caller (VM).
 * Resting tabs are outline-only chips; the selection is a filled pill that slides between them
 * (one shared indicator drawn behind the row) with a bolder label, so the state is carried
 * by shape and weight, not colour alone.
 * [prevKey] / [nextKey] optionally show the bound shoulder buttons at either end.
 */
@Composable
fun CategoryTabs(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit,
    idPrefix: String,
    modifier: Modifier = Modifier,
    prevKey: String? = null,
    nextKey: String? = null,
    onFocused: ((Category) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    // Each tab's layout bounds inside the scrolling row's content (inside its padding).
    val tabBounds = remember { mutableStateMapOf<Long, Rect>() }
    val indicator = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val indicatorAlpha = remember { Animatable(0f) }
    val indicatorPlaced = remember { mutableStateOf(false) }
    val selectedId = selected?.id
    val density = LocalDensity.current
    val contentPaddingPx = with(density) { TabRowPadding.toPx() }
    val edgeMarginPx = with(density) { TabScrollMargin.toPx() }

    LaunchedEffect(selectedId) {
        if (selectedId == null) {
            indicatorAlpha.animateTo(0f, PlutoMotion.fadeOut())
            return@LaunchedEffect
        }
        snapshotFlow { tabBounds[selectedId] }.filterNotNull().collectLatest { target ->
            coroutineScope {
                if (!indicatorPlaced.value || indicatorAlpha.value == 0f) {
                    // First placement (or reappearing): no slide in from nowhere.
                    indicator.snapTo(target)
                    indicatorPlaced.value = true
                } else {
                    launch { indicator.animateTo(target, PlutoMotion.spatialFast()) }
                }
                launch { indicatorAlpha.animateTo(1f, PlutoMotion.fadeIn()) }
                // Bring the selected tab into view (centred) when it is not comfortably visible.
                val viewport = scrollState.viewportSize
                if (viewport > 0) {
                    val left = target.left + contentPaddingPx
                    val right = target.right + contentPaddingPx
                    val visibleStart = scrollState.value + edgeMarginPx
                    val visibleEnd = scrollState.value + viewport - edgeMarginPx
                    if (left < visibleStart || right > visibleEnd) {
                        val centred = (left + right) / 2f - viewport / 2f
                        val to = centred.roundToInt().coerceIn(0, scrollState.maxValue)
                        launch { scrollState.animateScrollTo(to, PlutoMotion.spatial()) }
                    }
                }
            }
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val pillColor = MaterialTheme.colorScheme.primaryContainer

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (prevKey != null) {
            KeyChip(prevKey, Modifier.padding(horizontal = 4.dp))
        }
        Row(
            Modifier
                .weight(1f, fill = false)
                .horizontalScroll(scrollState)
                .padding(TabRowPadding)
                .drawWithContent {
                    val corner = TabCorner.toPx()
                    val border = TabBorder.toPx()
                    val inset = border / 2f
                    // Resting (outlined) chips, then the sliding selection pill, then the labels.
                    tabBounds.values.forEach { r ->
                        val radius = minOf(corner, r.height / 2f)
                        // Outline only at rest: next to the filled selection pill the state
                        // reads at a glance (two fills, light blue vs grey, were too alike).
                        drawRoundRect(
                            color = outlineColor,
                            topLeft = Offset(r.left + inset, r.top + inset),
                            size = Size(r.width - border, r.height - border),
                            cornerRadius = CornerRadius(radius - inset, radius - inset),
                            style = Stroke(border),
                        )
                    }
                    val a = indicatorAlpha.value
                    if (a > 0f && indicatorPlaced.value) {
                        val r = indicator.value
                        val radius = minOf(corner, r.height / 2f)
                        drawRoundRect(pillColor, r.topLeft, r.size, CornerRadius(radius, radius), alpha = a)
                    }
                    drawContent()
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEach { category ->
                key(category.id) {
                    CategoryTab(
                        category = category,
                        isSelected = category.id == selectedId,
                        id = "$idPrefix:${category.id}",
                        onClick = { onSelect(category) },
                        onFocused = onFocused?.let { { it(category) } },
                        onBounds = { tabBounds[category.id] = it },
                        onGone = { tabBounds.remove(category.id) },
                    )
                }
            }
        }
        if (nextKey != null) {
            KeyChip(nextKey, Modifier.padding(horizontal = 4.dp))
        }
    }
}

private val TabRowPadding = 4.dp
private val TabCorner = 20.dp
private val TabBorder = 1.dp

/** Space kept between a newly selected tab and the row's edge before scrolling to it. */
private val TabScrollMargin = 24.dp

/** Tabs are small; they dip a little less than tiles when pressed. */
private const val TabPressedScale = 0.95f

@Composable
private fun CategoryTab(
    category: Category,
    isSelected: Boolean,
    id: String,
    onClick: () -> Unit,
    onFocused: (() -> Unit)?,
    onBounds: (Rect) -> Unit,
    onGone: () -> Unit,
) {
    val shape = RoundedCornerShape(TabCorner)
    val contentColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        PlutoMotion.fadeIn(),
        label = "tabLabel",
    )
    val press = remember { MutableInteractionSource() }
    val currentOnBounds by rememberUpdatedState(onBounds)
    val currentOnGone by rememberUpdatedState(onGone)
    DisposableEffect(Unit) { onDispose { currentOnGone() } }
    val lastBounds = remember { arrayOf(Rect.Zero) }
    Column(
        Modifier
            .onPlaced { coordinates ->
                // Layout bounds within the row (the press scale below is visual only).
                val rect = Rect(coordinates.positionInParent(), coordinates.size.toSize())
                if (rect != lastBounds[0]) {
                    lastBounds[0] = rect
                    currentOnBounds(rect)
                }
            }
            .pressScale(press, pressedScale = TabPressedScale)
            .defaultMinSize(minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(
                id = id,
                onActivate = onClick,
                contentDescription = "${category.name} category",
                shape = shape,
                onFocused = onFocused,
                interactionSource = press,
            )
            .semantics { selected = isSelected }
            .clip(shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            category.name,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Snackbar-style message with a controller-reachable Dismiss button. Auto-dismisses after
 * [timeoutMillis]; announced politely to screen readers.
 */
@Composable
fun MessageBar(
    message: UserMessage?,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
    timeoutMillis: Long = 6_000,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message?.id) {
        val id = message?.id ?: return@LaunchedEffect
        delay(timeoutMillis)
        currentOnDismiss(id)
    }
    // The last message stays drawn while the bar slides away after dismissal.
    val shown = remember { arrayOfNulls<UserMessage>(1) }
    if (message != null) shown[0] = message
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = message != null
    if (!visibility.currentState && !visibility.targetState && visibility.isIdle) return
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(PlutoMotion.slideSpring) { it } + fadeIn(PlutoMotion.fadeIn()),
        exit = slideOutVertically(PlutoMotion.slideSpring) { it } + fadeOut(PlutoMotion.fadeOut()),
    ) {
        val leaving = transition.targetState != EnterExitState.Visible
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            val current = message ?: shown[0] ?: return@Surface
            // A replacing message slides up into place while the previous one fades away.
            AnimatedContent(
                targetState = current,
                contentKey = { it.id },
                transitionSpec = {
                    (slideInVertically(PlutoMotion.slideSpring) { it / 2 } + fadeIn(PlutoMotion.fadeIn()))
                        .togetherWith(slideOutVertically(PlutoMotion.slideSpring) { -it / 3 } + fadeOut(PlutoMotion.fadeOut()))
                        .using(SizeTransform(clip = true) { _, _ -> PlutoMotion.spatialFast() })
                },
                label = "message",
            ) { msg ->
                // Departing content can't take focus (the Dismiss id belongs to the live message).
                val inert = leaving || transition.targetState != EnterExitState.Visible
                CompositionLocalProvider(LocalFocusInert provides focusInert(inert)) {
                    MessageBarContent(msg, onDismiss = { currentOnDismiss(msg.id) })
                }
            }
        }
    }
}

@Composable
private fun MessageBarContent(message: UserMessage, onDismiss: () -> Unit) {
    Row(
        Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        val shape = RoundedCornerShape(20.dp)
        val press = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(press)
                .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
                .controllerFocusable(
                    id = "message:dismiss",
                    onActivate = onDismiss,
                    contentDescription = "Dismiss message",
                    shape = shape,
                    interactionSource = press,
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Dismiss",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inversePrimary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * Centred empty-state block: an optional [icon] on a small planet-and-orbit mark (a light
 * illustration, decorative), a title, optional detail and optional actions.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onWallpaper: Boolean = false,
    icon: ImageVector? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            val disc = MaterialTheme.colorScheme.primaryContainer
            val orbit = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            Box(
                Modifier
                    .size(EmptyIllustrationSize)
                    .drawBehind {
                        val r = size.minDimension / 2f
                        drawCircle(orbit, radius = r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                        drawCircle(disc, radius = r * 0.7f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        val titleStyle = MaterialTheme.typography.titleMedium
        Text(title, style = if (onWallpaper) titleStyle.overWallpaper() else titleStyle, textAlign = TextAlign.Center)
        if (detail != null) {
            val detailStyle = MaterialTheme.typography.bodyMedium
            Text(
                detail,
                style = if (onWallpaper) detailStyle.overWallpaper() else detailStyle,
                color = if (onWallpaper) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/** Size of the empty-state mark (orbit ring and disc). */
private val EmptyIllustrationSize = 88.dp
