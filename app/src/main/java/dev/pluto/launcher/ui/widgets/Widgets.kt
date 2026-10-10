package dev.pluto.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.HomeWidget
import dev.pluto.launcher.ui.DragSource
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.home.DragLook
import dev.pluto.launcher.ui.home.LocalHomeDrag
import dev.pluto.launcher.ui.home.dragFeedback
import dev.pluto.launcher.ui.overlay.ActionRow
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.LayerScaffold
import dev.pluto.launcher.ui.overlay.ModalPanel
import dev.pluto.launcher.ui.overlay.SectionHeader
import dev.pluto.launcher.ui.overlay.StepperRow
import dev.pluto.launcher.ui.overlay.rememberScreenFocus
import dev.pluto.launcher.widgets.LocalWidgetBinder
import dev.pluto.launcher.widgets.LocalWidgetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A widget on the home grid, spanning the row, [rowHeight] per row it takes. Press and hold
 * opens its options (resize, remove); keep holding and move to drag it elsewhere on Home.
 * Taps and scrolls inside the widget go to the widget as usual.
 */
@Composable
fun HomeWidgetView(widget: HomeWidget, rowHeight: Dp, vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val host = LocalWidgetHost.current ?: return
    val id = widget.appWidgetId
    val info = remember(id, widget.provider) { host.info(id) }
    val label = rememberWidgetLabel(info)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .height(rowHeight * widget.rows)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .dragFeedback(HomeItem.Widget(id).id)
            .widgetGestures(
                onHold = { vm.openLayer(Layer.WidgetActions(id)) },
                source = { DragSource.Home(HomeItem.Widget(id)) },
                look = { DragLook.Widget(label) },
            )
            .clip(RoundedCornerShape(20.dp)),
    ) {
        if (info == null) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(WidgetText.UNAVAILABLE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val hostView = remember { arrayOfNulls<AppWidgetHostView>(1) }
            AndroidView(
                factory = { context -> host.createView(context, id, info).also { hostView[0] = it } },
                modifier = Modifier.fillMaxSize().onSizeChanged { size = it },
            )
            // Tell the widget the size it actually has, so it lays itself out for it.
            LaunchedEffect(size) {
                val view = hostView[0] ?: return@LaunchedEffect
                if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
                val w = size.width / density.density
                val h = size.height / density.density
                if (Build.VERSION.SDK_INT >= 31) {
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
                } else {
                    @Suppress("DEPRECATION")
                    view.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
                }
            }
        }
    }
}

/** The provider's name, resolved once. */
@Composable
private fun rememberWidgetLabel(info: AppWidgetProviderInfo?): String {
    val context = LocalContext.current
    return remember(info) { info?.loadLabel(context.packageManager)?.takeIf { it.isNotBlank() } ?: WidgetText.WIDGET }
}

/**
 * Press and hold opens the options ([onHold]); moving afterwards lifts the widget as a drag.
 * Taps and moves before the hold are left to the widget. After the hold every event is
 * consumed, so the widget sees a cancel instead of a click.
 */
@Composable
private fun Modifier.widgetGestures(onHold: () -> Unit, source: () -> DragSource, look: () -> DragLook): Modifier {
    val drag = LocalHomeDrag.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onPlaced { coords[0] = it }
        .pointerInput(drag) {
            val slop = viewConfiguration.touchSlop
            val hold = viewConfiguration.longPressTimeoutMillis
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val early = withTimeoutOrNull(hold) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!change.pressed || (change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE")
                    true
                }
                if (early != null) return@awaitEachGesture
                onHold()
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    if (!dragging && drag != null && (change.position - down.position).getDistance() > slop) {
                        val c = coords[0]?.takeIf { it.isAttached } ?: continue
                        drag.start(source(), look(), down.id, c.localToWindow(change.position))
                        dragging = true
                    }
                }
            }
        }
}

/** Resize or remove a widget. */
@Composable
fun WidgetActionsSheet(appWidgetId: Int, state: LauncherUiState, vm: LauncherViewModel) {
    val widget = state.homeTiles.firstNotNullOfOrNull { (it as? HomeTile.Widget)?.widget?.takeIf { w -> w.appWidgetId == appWidgetId } }
    val host = LocalWidgetHost.current
    val label = rememberWidgetLabel(remember(appWidgetId) { host?.info(appWidgetId) })
    val isTop = state.session.topLayer == Layer.WidgetActions(appWidgetId)
    rememberScreenFocus("widget:rows:inc")
    ModalPanel(title = label, idPrefix = "widget", onDismiss = { vm.back() }, trapFocus = isTop) {
        if (widget != null) {
            StepperRow(
                idPrefix = "widget:rows",
                label = WidgetText.HEIGHT,
                valueText = WidgetText.rows(widget.rows),
                onDecrease = if (widget.rows > HomeWidget.MIN_ROWS) ({ vm.setWidgetRows(appWidgetId, widget.rows - 1) }) else null,
                onIncrease = if (widget.rows < HomeWidget.MAX_ROWS) ({ vm.setWidgetRows(appWidgetId, widget.rows + 1) }) else null,
            )
        }
        BodyText(WidgetText.MOVE_HINT)
        ActionRow(
            id = "widget:remove",
            label = WidgetText.REMOVE,
            icon = Icons.Rounded.Delete,
            destructive = true,
            onClick = { vm.removeWidget(appWidgetId) },
        )
    }
}

/** Every widget the installed apps offer, by app, with previews; choosing one adds it to Home. */
@Composable
fun WidgetPickerScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val host = LocalWidgetHost.current
    val binder = LocalWidgetBinder.current
    val context = LocalContext.current
    val isTop = state.session.topLayer == Layer.WidgetPicker
    val groups by produceState<List<Pair<String, List<AppWidgetProviderInfo>>>?>(null, host) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            host?.providers().orEmpty()
                .groupBy { it.provider.packageName }
                .map { (pkg, list) ->
                    val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    appLabel to list.sortedBy { it.loadLabel(pm).lowercase() }
                }
                .sortedBy { it.first.lowercase() }
        }
    }
    rememberScreenFocus("widgets:close")
    LayerScaffold(
        title = WidgetText.ADD_WIDGET,
        idPrefix = "widgets",
        onClose = { vm.back() },
        trapFocus = isTop,
        fullScreen = true,
    ) {
        val list = groups
        when {
            list == null -> BodyText(WidgetText.LOADING)
            list.isEmpty() -> BodyText(WidgetText.NONE)
            else -> list.forEach { (app, providers) ->
                SectionHeader(app)
                providers.forEach { info ->
                    WidgetChoice(info, onPick = { binder?.add(info) })
                }
            }
        }
    }
}

@Composable
private fun WidgetChoice(info: AppWidgetProviderInfo, onPick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))
                    ?.toBitmap(with(density) { 120.dp.roundToPx() }, with(density) { 80.dp.roundToPx() })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    val label = remember(info) { info.loadLabel(context.packageManager) ?: WidgetText.WIDGET }
    val cells = remember(info) { widgetCells(info, density.density) }
    ActionRow(
        id = "widgets:${info.provider.flattenToString()}",
        label = label,
        supporting = cells,
        leading = {
            Box(
                Modifier
                    .size(width = 72.dp, height = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                val image = preview
                if (image != null) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
        },
        onClick = onPick,
    )
}

/** "4 × 2": the widget's size in launcher cells, from its target or minimum size. */
private fun widgetCells(info: AppWidgetProviderInfo, density: Float): String {
    val w = if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) info.targetCellWidth else ((info.minWidth / density + 30) / 70).toInt().coerceAtLeast(1)
    val h = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight else ((info.minHeight / density + 30) / 70).toInt().coerceAtLeast(1)
    return "$w × $h"
}

internal object WidgetText {
    const val WIDGET = "Widget"
    const val ADD_WIDGET = "Add widget"
    const val LOADING = "Looking for widgets…"
    const val NONE = "None of your apps offer widgets."
    const val UNAVAILABLE = "This widget isn't available"
    const val HEIGHT = "Height"
    const val REMOVE = "Remove widget"
    const val MOVE_HINT = "To move it, press and hold the widget, then drag."
    fun rows(n: Int) = if (n == 1) "1 row" else "$n rows"
}
