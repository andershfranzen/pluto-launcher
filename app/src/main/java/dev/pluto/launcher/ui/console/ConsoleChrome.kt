package dev.pluto.launcher.ui.console

import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.os.BatteryManager
import android.content.IntentFilter
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.core.Animatable
import android.hardware.BatteryState
import android.os.Build
import android.view.InputDevice
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Animatable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.ui.components.KeyChip
import dev.pluto.launcher.ui.components.LegendItem
import dev.pluto.launcher.ui.components.pressScale
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.theme.ConsoleScrimAlpha
import dev.pluto.launcher.ui.theme.LocalDarkTheme
import dev.pluto.launcher.ui.theme.PlutoDimens
import dev.pluto.launcher.ui.theme.overWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The console's legend: shoulder buttons switch shelves here. */
val ConsoleLegend = listOf(
    LegendItem(ControllerAction.CONFIRM, "Open"),
    LegendItem(ControllerAction.ACTIONS, "Actions"),
    LegendItem(ControllerAction.SEARCH, "Search"),
    LegendItem(ControllerAction.PREV_CATEGORY, "Shelf"),
    LegendItem(ControllerAction.NEXT_CATEGORY, "Shelf"),
    LegendItem(ControllerAction.SETTINGS, "Settings"),
)

/** The legend on an empty shelf: nothing to open or act on. */
val ConsoleLegendEmpty = ConsoleLegend.filter { it.action != ControllerAction.CONFIRM && it.action != ControllerAction.ACTIONS }

internal const val TAB_ID_PREFIX = "hh:tab:"

internal fun shelfTabId(id: ShelfId): String = "$TAB_ID_PREFIX${id.key}"

/** Focus targets the console links explicitly (the flow's overlapping cards defeat spatial search). */
@Stable
internal class ConsoleFocusLinks {
    val tabs = HashMap<String, FocusRequester>()
    var activeShelfKey: String? = null

    fun activeTab(): FocusRequester = activeShelfKey?.let(tabs::get) ?: FocusRequester.Default
}

// ---------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------

private val MinInlineTabsWidth = 320.dp
private val HeaderGap = 12.dp

/**
 * Header with the shelf tabs, the status cluster (clock, controller battery) and the icon
 * buttons. The buttons always keep their 48dp targets; the status gets what is left. The
 * tabs share the row only while they fit (or get at least [MinInlineTabsWidth] to scroll
 * in); otherwise, e.g. at large text sizes, they move to their own full-width row below.
 */
@Composable
internal fun ConsoleTopBar(
    modifier: Modifier,
    tabs: @Composable () -> Unit,
    status: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
) {
    Layout(contents = listOf(tabs, status, buttons), modifier = modifier) { (tabsM, statusM, buttonsM), constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = HeaderGap.roundToPx()
        // Any slot may be empty (the console keeps its buttons at the bottom).
        val buttonsP = buttonsM.firstOrNull()?.measure(loose)
        val buttonsW = buttonsP?.width ?: 0
        val buttonsGap = if (buttonsP != null) gap else 0
        val statusP = statusM.firstOrNull()?.measure(loose.copy(maxWidth = (width - buttonsW - buttonsGap).coerceAtLeast(0)))
        val statusW = statusP?.width ?: 0
        val tabsMeasurable = tabsM.first()
        val inlineWidth = width - buttonsW - statusW - buttonsGap - gap
        val tabsWanted = tabsMeasurable.maxIntrinsicWidth(constraints.maxHeight.takeIf { it != Int.MAX_VALUE } ?: 0)
        val inline = inlineWidth > 0 && inlineWidth >= minOf(tabsWanted, MinInlineTabsWidth.roundToPx())
        val tabsP = tabsMeasurable.measure(loose.copy(maxWidth = if (inline) inlineWidth else width))
        val statusH = statusP?.height ?: 0
        val buttonsH = buttonsP?.height ?: 0
        if (inline) {
            val height = maxOf(tabsP.height, statusH, buttonsH)
            layout(width, height) {
                tabsP.place(0, (height - tabsP.height) / 2)
                statusP?.place(width - buttonsW - buttonsGap - statusW, (height - statusH) / 2)
                buttonsP?.place(width - buttonsW, (height - buttonsH) / 2)
            }
        } else {
            val top = maxOf(statusH, buttonsH)
            layout(width, top + tabsP.height) {
                statusP?.place(if (buttonsP == null) width - statusW else 0, (top - statusH) / 2)
                buttonsP?.place(width - buttonsW, (top - buttonsH) / 2)
                tabsP.place(0, top)
            }
        }
    }
}

/**
 * The shelf tabs: Recent launches · Favourites · categories. Touch, L1/R1, or moving
 * controller focus along the row (focus is selection there). The selected tab is a filled
 * pill with a short underline (not colour alone; every tab keeps one weight so selecting
 * never changes widths); a focused other tab gets a translucent fill under its ring. The
 * selected tab scrolls into view. Down goes to the flow's selected card ([downTarget]).
 */
@Composable
internal fun ShelfTabs(
    shelves: List<Pair<ShelfId, String>>,
    active: ShelfId,
    onSelect: (ShelfId) -> Unit,
    links: ConsoleFocusLinks,
    downTarget: () -> FocusRequester,
    prevKey: String?,
    nextKey: String?,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val bounds = remember { HashMap<String, IntRangeHolder>() }
    links.activeShelfKey = active.key
    val marginPx = with(LocalDensity.current) { TabScrollMargin.roundToPx() }
    LaunchedEffect(active, scroll) {
        // Once the tab is placed, centre it when it isn't comfortably visible.
        withFrameNanos { }
        val b = bounds[active.key] ?: return@LaunchedEffect
        val viewport = scroll.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        if (b.start < scroll.value + marginPx || b.end > scroll.value + viewport - marginPx) {
            val to = ((b.start + b.end) / 2 - viewport / 2).coerceIn(0, scroll.maxValue)
            scroll.animateScrollTo(to, PlutoMotion.spatial())
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (prevKey != null) KeyChip(prevKey, Modifier.padding(horizontal = 4.dp))
        val fadePx = with(LocalDensity.current) { TabEdgeFade.toPx() }
        Row(
            Modifier
                .weight(1f, fill = false)
                // Tabs scrolled past an edge fade out there instead of being cut mid-word.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val start = if (scroll.value > 0) fadePx else 0f
                    val end = if (scroll.value < scroll.maxValue) fadePx else 0f
                    if (start > 0f) {
                        drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = start), blendMode = BlendMode.DstIn)
                    }
                    if (end > 0f) {
                        drawRect(Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - end, endX = size.width), blendMode = BlendMode.DstIn)
                    }
                }
                .horizontalScroll(scroll)
                .padding(4.dp),
            // Room for the focus ring's outset on both neighbours (it never overlaps the selected pill).
            horizontalArrangement = Arrangement.spacedBy(TabGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shelves.forEachIndexed { index, (id, title) ->
                key(id.key) {
                    ShelfTab(
                        id = id,
                        title = title,
                        position = "${index + 1} of ${shelves.size}",
                        isSelected = id == active,
                        onClick = { onSelect(id) },
                        links = links,
                        downTarget = downTarget,
                        onBounds = { start, end -> bounds[id.key] = IntRangeHolder(start, end) },
                    )
                }
            }
        }
        if (nextKey != null) KeyChip(nextKey, Modifier.padding(horizontal = 4.dp))
    }
}

internal data class IntRangeHolder(val start: Int, val end: Int)

private val TabShape = RoundedCornerShape(20.dp)
private val TabGap = 12.dp
private val TabUnderlineWidth = 18.dp
private val TabUnderlineHeight = 2.dp

/** Fill of a tab that holds controller focus but is not the selected shelf (the ring alone was too faint). */
private val FocusedTabFill = Color.White.copy(alpha = 0.18f)

/** Width of the fade where the tab row is scrolled past an edge. */
private val TabEdgeFade = 40.dp

/** Space kept between the selected tab and the row's edge before scrolling to it. */
private val TabScrollMargin = 24.dp

@Composable
private fun ShelfTab(
    id: ShelfId,
    title: String,
    position: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    links: ConsoleFocusLinks,
    downTarget: () -> FocusRequester,
    onBounds: (Int, Int) -> Unit,
) {
    val requester = remember { FocusRequester() }
    val focus = LocalControllerFocus.current
    DisposableEffect(id.key, requester) {
        links.tabs[id.key] = requester
        onDispose { if (links.tabs[id.key] === requester) links.tabs.remove(id.key) }
    }
    val selectedNow by rememberUpdatedState(isSelected)
    val isSelectedNow = { selectedNow }
    var hasFocus by remember { mutableStateOf(false) }
    val showFocus = hasFocus && focus.inputMode == InputMode.CONTROLLER
    // Switch/PS5 style: the selected shelf is a solid light pill with dark text and an
    // underline; a focused other tab gets a translucent fill under its ring; the rest are
    // just their light text.
    val container by animateColorAsState(
        when {
            isSelected -> Color.White
            showFocus -> FocusedTabFill
            else -> Color.Transparent
        },
        PlutoMotion.fadeIn(),
        label = "shelfTab",
    )
    val content by animateColorAsState(
        if (isSelected) Color(0xFF14161C) else Color.White.copy(alpha = 0.72f),
        PlutoMotion.fadeIn(),
        label = "shelfTabText",
    )
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .onPlaced { c ->
                val x = c.positionInParent().x.roundToInt()
                onBounds(x, x + c.size.width)
            }
            .pressScale(press, pressedScale = 0.95f)
            .defaultMinSize(minHeight = PlutoDimens.MinTouchTarget)
            .focusRequester(requester)
            .focusProperties { down = downTarget() }
            .onFocusChanged { hasFocus = it.hasFocus }
            .controllerFocusable(
                id = shelfTabId(id),
                onActivate = onClick,
                contentDescription = "$title shelf, $position",
                shape = TabShape,
                interactionSource = press,
                // Focus is selection along the row (PS5 / Xbox): stepping from one tab to the
                // next switches the shelf, as L1/R1 do. Focus arriving from elsewhere (UP from
                // the flow, a restore) never switches anything.
                onFocused = {
                    val from = focus.previousFocusedId
                    if (!isSelectedNow() && focus.inputMode == InputMode.CONTROLLER && from != null &&
                        from.startsWith(TAB_ID_PREFIX) && from != shelfTabId(id)
                    ) {
                        onClick()
                    }
                },
            )
            .semantics { selected = isSelected }
            .clip(TabShape)
            .drawBehind {
                drawRect(container)
                if (isSelected) {
                    // Not colour alone: the selected tab is also underlined.
                    val w = TabUnderlineWidth.toPx()
                    val h = TabUnderlineHeight.toPx()
                    drawRoundRect(
                        content,
                        topLeft = Offset((size.width - w) / 2f, size.height - h - 4.dp.toPx()),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                }
            }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            // One weight for every tab: selecting never changes the tab's width.
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Status: controller battery
// ---------------------------------------------------------------------------------------

/**
 * The controller's battery level (InputDevice.batteryState, API 31+), only when the
 * controller reports one; hidden otherwise. Polled once a minute while the launcher is started,
 * off the main thread (the query is a binder call).
 */
@Composable
internal fun ControllerBattery(deviceId: Int?, modifier: Modifier = Modifier) {
    if (deviceId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    var reading by remember(deviceId) { mutableStateOf<BatteryReading?>(null) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(deviceId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                reading = withContext(Dispatchers.IO) { readBattery(deviceId) }
                delay(BATTERY_POLL_MS)
            }
        }
    }
    val r = reading ?: return
    val icon = when {
        r.charging -> Icons.Outlined.BatteryChargingFull
        r.percent <= 15 -> Icons.Outlined.BatteryAlert
        else -> Icons.Outlined.BatteryStd
    }
    val spoken = "Controller battery ${r.percent} percent" + if (r.charging) ", charging" else ""
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text("${r.percent}%", style = MaterialTheme.typography.labelLarge.overWallpaper(), maxLines = 1)
    }
}

/**
 * The phone's own battery (console mode hides the status bar): level and charging state
 * from the sticky ACTION_BATTERY_CHANGED broadcast, updated as it changes (no polling).
 */
@Composable
internal fun PhoneBattery(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<BatteryReading?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                reading = intent?.let(::batteryFrom) ?: reading
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let(::batteryFrom)?.let { reading = it }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val r = reading ?: return
    val icon = when {
        r.charging -> Icons.Outlined.BatteryChargingFull
        r.percent <= 15 -> Icons.Outlined.BatteryAlert
        else -> Icons.Outlined.BatteryStd
    }
    val spoken = "Phone battery ${r.percent} percent" + if (r.charging) ", charging" else ""
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text("${r.percent}%", style = MaterialTheme.typography.labelLarge.overWallpaper(), maxLines = 1)
    }
}

private fun batteryFrom(intent: Intent): BatteryReading? {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryReading((level * 100f / scale).roundToInt().coerceIn(0, 100), charging)
}

private data class BatteryReading(val percent: Int, val charging: Boolean)

private const val BATTERY_POLL_MS = 60_000L

@RequiresApi(Build.VERSION_CODES.S)
private fun readBattery(deviceId: Int): BatteryReading? = try {
    val state = InputDevice.getDevice(deviceId)?.batteryState
    val capacity = state?.capacity ?: Float.NaN
    if (state == null || !state.isPresent || capacity.isNaN() || capacity < 0f) {
        null
    } else {
        BatteryReading((capacity * 100f).roundToInt().coerceIn(0, 100), state.status == BatteryState.STATUS_CHARGING)
    }
} catch (e: RuntimeException) {
    null
}

// ---------------------------------------------------------------------------------------
// Title under the stage, ambient backdrop
// ---------------------------------------------------------------------------------------

/**
 * The selected app's name, large, with the shelf and position under it. Reads the
 * selection itself, so a D-pad step recomposes only this. Only the name crossfades (and
 * only while steps come slower than the fade: a held D-pad just swaps it); the shelf and the
 * counter under it update in place, so the block never flickers. Screen readers get the
 * same from the focused card.
 */
@Composable
internal fun ConsoleTitle(stage: CoverflowState, modifier: Modifier = Modifier) {
    val entry = stage.selectedEntry
    val title = entry?.label.orEmpty()
    val lastChange = remember { longArrayOf(0L) }
    val fast = remember { booleanArrayOf(false) }
    val previousTitle = remember { arrayOfNulls<String>(1) }
    if (previousTitle[0] != title) {
        val now = android.os.SystemClock.uptimeMillis()
        fast[0] = previousTitle[0] != null && now - lastChange[0] < TITLE_FADE_MIN_INTERVAL_MS
        lastChange[0] = now
        previousTitle[0] = title
    }
    // One text, always the newest name: a quick fade-up on a calm change, an instant swap
    // while scrolling fast. Never two names on screen at once (a crossfade overlapped them).
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(title) {
        if (fast[0]) {
            alpha.snapTo(1f)
        } else {
            alpha.snapTo(TITLE_FADE_FROM)
            alpha.animateTo(1f, tween(PlutoMotion.SHORT_MS, easing = PlutoMotion.EmphasizedDecelerate))
        }
    }
    Column(modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold).overWallpaper(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        )
    }
}

/** "3 of 9": where the selected card is in the shelf, for the bottom left corner. Decorative (cards announce their position). */
@Composable
internal fun ConsoleCounter(stage: CoverflowState, modifier: Modifier = Modifier) {
    if (stage.selectedEntry == null) return
    Text(
        "${stage.selectedIndex + 1} of ${stage.count}",
        style = MaterialTheme.typography.bodyMedium.overWallpaper(),
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { },
    )
}

/** Opacity a calmly changed title fades up from. */
private const val TITLE_FADE_FROM = 0.25f

/** Title changes closer together than this (a held D-pad, a fling) swap without a fade. */
private const val TITLE_FADE_MIN_INTERVAL_MS = 2L * PlutoMotion.SHORT_MS

/** Added over the scrim at the top: 1 - (1 - 0.7) * (1 - 0.67) ≈ 0.9. */
private const val TopScrimExtra = 0.67f

/**
 * Ambient glow behind the stage, tinted from the selected app's icon colour and crossfading
 * on change. Animated and drawn without recomposition (snapshotFlow into an Animatable, read
 * in draw), in its own layer so the redraw stays local.
 */
@Composable
internal fun ConsoleBackdrop(stage: () -> CoverflowState?, artPx: Int, versions: () -> Map<String, Int>, modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val neutral = MaterialTheme.colorScheme.primary
    val glow = remember { Animatable(Color.Transparent) }
    LaunchedEffect(glow, artPx) {
        snapshotFlow {
            val s = stage()
            s?.artTick
            val entry = s?.selectedEntry
            if (entry == null) {
                null
            } else {
                ConsoleArtCache.peek(entry.key, artPx, versions()[entry.packageName] ?: 0)?.color
            }
        }.collectLatest { color ->
            val target = color?.let { Color(it) } ?: neutral.copy(alpha = 0.5f)
            glow.animateTo(target, tween(PlutoMotion.LONG_MS, easing = PlutoMotion.Standard))
        }
    }
    val strength = if (dark) 0.55f else 0.38f
    Spacer(
        modifier
            .graphicsLayer { }
            .drawBehind {
                // An immersive stage: the wallpaper recedes behind a dark scrim, the spotlight
                // in the selected app's colour sits on top of it.
                drawRect(Color.Black.copy(alpha = ConsoleScrimAlpha))
                // Deeper behind the tabs (about 90% at the top), so a light wallpaper never
                // shows through the header.
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = TopScrimExtra), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.32f,
                    ),
                )
                val c = glow.value
                if (c.alpha <= 0f) return@drawBehind
                val center = Offset(size.width / 2f, size.height * 0.46f)
                val radius = maxOf(size.width, size.height) * 0.55f
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(c.copy(alpha = c.alpha * strength), c.copy(alpha = c.alpha * strength * 0.35f), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                )
            },
    )
}
