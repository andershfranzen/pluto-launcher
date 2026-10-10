package dev.pluto.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.GlassPanel
import dev.pluto.launcher.ui.motion.PlutoMotion
import kotlin.math.roundToInt

/** Where the home menu is open (window coordinates of the press), or closed. */
@Stable
internal class HomeMenuState {
    val visible = MutableTransitionState(false)
    var anchor by mutableStateOf(Offset.Zero)
        private set

    /** Window position of the surface the long press is detected on. */
    var origin = Offset.Zero

    fun show(local: Offset) {
        anchor = origin + local
        visible.targetState = true
    }

    fun hide() {
        visible.targetState = false
    }
}

private class HomeMenuEntry(val label: String, val icon: ImageVector, val run: (LauncherViewModel) -> Unit)

private val HomeMenuEntries = listOf(
    HomeMenuEntry("Search apps", Icons.Outlined.Search) { it.openDrawer(withSearch = true) },
    HomeMenuEntry("Add widget", Icons.Outlined.Widgets) { it.openLayer(Layer.WidgetPicker) },
    HomeMenuEntry("Edit home", Icons.Outlined.Edit) { it.openLayer(Layer.Edit) },
    HomeMenuEntry("Launcher settings", Icons.Outlined.Settings) { it.openLayer(Layer.Settings) },
)

/**
 * Press and hold empty space on the home surface to open the home menu at the finger. Only
 * presses nothing else took: tiles and dock slots consume their own down. The same entries
 * are screen-reader custom actions on the surface.
 */
@Composable
internal fun Modifier.homeMenuOnLongPress(menu: HomeMenuState, vm: LauncherViewModel): Modifier {
    val haptics = LocalHapticFeedback.current
    return this
        .semantics {
            customActions = HomeMenuEntries.map { entry -> CustomAccessibilityAction(entry.label) { entry.run(vm); true } }
        }
        .onPlaced { menu.origin = it.positionInWindow() }
        .pointerInput(menu) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val slop = viewConfiguration.touchSlop
                val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                        if (!change.pressed || change.isConsumed || (change.position - down.position).getDistance() > slop) {
                            return@withTimeoutOrNull
                        }
                    }
                } == null
                if (!held) return@awaitEachGesture
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menu.show(down.position)
                // The rest of this press belongs to the menu: no tap or drag underneath.
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                }
            }
        }
}

/** The home menu: a small glass card at the press point (above the finger when it fits). */
@Composable
internal fun HomeMenu(menu: HomeMenuState, vm: LauncherViewModel) {
    if (!menu.visible.currentState && !menu.visible.targetState) return
    val density = LocalDensity.current
    val position = remember(menu.anchor, density) { HomeMenuPosition(menu.anchor, with(density) { 12.dp.roundToPx() }) }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = menu::hide,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = menu.visible,
            enter = fadeIn(PlutoMotion.fadeIn()) + scaleIn(PlutoMotion.spatialFast(), initialScale = 0.85f, transformOrigin = position.origin),
            exit = fadeOut(PlutoMotion.fadeOut()) + scaleOut(PlutoMotion.fadeOut(), targetScale = 0.92f, transformOrigin = position.origin),
        ) {
            val scheme = MaterialTheme.colorScheme
            GlassPanel(
                Modifier.width(232.dp),
                shape = RoundedCornerShape(20.dp),
                fill = SolidColor(scheme.surfaceContainerHigh.copy(alpha = 0.92f)),
            ) {
                Column {
                    HomeMenuEntries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable {
                                    menu.hide()
                                    entry.run(vm)
                                }
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Icon(entry.icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Centres the menu on the press, above the finger if it fits, kept [margin] inside the window. */
private class HomeMenuPosition(private val anchor: Offset, private val margin: Int) : PopupPositionProvider {
    /** Where the card grows from, relative to itself (updated on placement). */
    var origin by mutableStateOf(TransformOrigin(0.5f, 1f))
        private set

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val ax = anchor.x.roundToInt()
        val ay = anchor.y.roundToInt()
        val x = (ax - w / 2).coerceIn(margin, (windowSize.width - w - margin).coerceAtLeast(margin))
        val above = ay - h - margin
        val y = if (above >= margin) above else (ay + margin).coerceAtMost((windowSize.height - h - margin).coerceAtLeast(margin))
        if (w > 0 && h > 0) {
            origin = TransformOrigin(((ax - x).toFloat() / w).coerceIn(0f, 1f), ((ay - y).toFloat() / h).coerceIn(0f, 1f))
        }
        return IntOffset(x, y)
    }
}
