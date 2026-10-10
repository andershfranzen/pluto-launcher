package dev.pluto.launcher.ui.overlay

import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.home.iconSize
import dev.pluto.launcher.ui.components.pressScale
import dev.pluto.launcher.ui.components.AppIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.pluto.launcher.apps.AppShortcut
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.focus.controllerFocusable
import kotlin.math.roundToInt

private val QuickMenuWidth = 296.dp
private val QuickMenuGap = 10.dp
private val QuickMenuMargin = 12.dp
private val QuickMenuShape = RoundedCornerShape(28.dp)
private const val QUICK_SCRIM_ALPHA = 0.45f

/** Where a tile draws its icon inside its bounds (AppTile's top padding). */
private val TileIconInset = 6.dp

/**
 * The touch long-press menu for an app on a phone: a small card beside the tile instead of
 * the full actions sheet. The rest of the screen dims while the app's icon stays bright in
 * place, so it is clear which app the card belongs to. The card has the app's name and
 * More… (the full sheet, [onMore]), the app's own shortcuts, then the everyday actions as
 * labelled icons. Placed above the tile when it fits, else below; [anchor] is the tile in
 * window coordinates.
 */
@Composable
internal fun AppQuickMenu(
    entry: AppEntry,
    anchor: Rect,
    state: LauncherUiState,
    vm: LauncherViewModel,
    onMore: () -> Unit,
) {
    val key = entry.key
    val shortcuts by produceState<List<AppShortcut>?>(null, key) { value = vm.shortcutsFor(key) }
    val pinnedDirectly = state.homeTiles.any { it is HomeTile.App && it.entry.key == key }
    val inDock = state.isInDock(key)
    val isHidden = key in state.hidden
    val iconSize = state.iconSize()
    val origin = remember { floatArrayOf(Float.NaN, Float.NaN) }

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced {
                // The layer's own window position, once: the entrance scale must not move the card.
                if (origin[0].isNaN()) it.positionInWindow().let { p -> origin[0] = p.x; origin[1] = p.y }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = QUICK_SCRIM_ALPHA))
                .clearAndSetSemantics { }
                .pointerTapToDismiss { vm.back() },
        )
        val density = LocalDensity.current
        Layout(
            content = {
                // The pressed app, redrawn above the scrim exactly where its tile shows it.
                AppIcon(entry, iconSize, Modifier.clearAndSetSemantics { })
                QuickMenuCard(
                    entry = entry,
                    shortcuts = shortcuts.orEmpty(),
                    pinned = pinnedDirectly,
                    inDock = inDock,
                    hidden = isHidden,
                    vm = vm,
                    onMore = onMore,
                )
            },
        ) { measurables, constraints ->
            val margin = QuickMenuMargin.roundToPx()
            val width = minOf(QuickMenuWidth.roundToPx(), constraints.maxWidth - 2 * margin)
            val icon = measurables[0].measure(Constraints())
            val card = measurables[1].measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight - 2 * margin))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val o = if (origin[0].isNaN()) Offset.Zero else Offset(origin[0], origin[1])
                val tile = anchor.translate(-o)
                icon.place((tile.center.x - icon.width / 2f).roundToInt(), (tile.top + with(density) { TileIconInset.toPx() }).roundToInt())
                val gap = with(density) { QuickMenuGap.roundToPx() }
                val x = (tile.center.x - card.width / 2f).roundToInt()
                    .coerceIn(margin, (constraints.maxWidth - card.width - margin).coerceAtLeast(margin))
                val above = tile.top.roundToInt() - gap - card.height
                val below = tile.bottom.roundToInt() + gap
                val y = when {
                    above >= margin -> above
                    below + card.height <= constraints.maxHeight - margin -> below
                    else -> (constraints.maxHeight - card.height - margin).coerceAtLeast(margin)
                }
                card.place(x, y)
            }
        }
    }
}

@Composable
private fun QuickMenuCard(
    entry: AppEntry,
    shortcuts: List<AppShortcut>,
    pinned: Boolean,
    inDock: Boolean,
    hidden: Boolean,
    vm: LauncherViewModel,
    onMore: () -> Unit,
) {
    val key = entry.key
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .shadow(16.dp, QuickMenuShape)
            .background(scheme.surfaceContainerHigh, QuickMenuShape)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), QuickMenuShape)
            .semantics { paneTitle = entry.label }
            .animateContentSize(PlutoMotion.spatialFast())
            .padding(vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PlutoIconButton("quick:more", Icons.Rounded.MoreVert, OverlayText.MORE_ACTIONS, onMore)
        }
        if (shortcuts.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                shortcuts.forEach { shortcut ->
                    ShortcutRow(shortcut, vm) { vm.launchShortcut(key, shortcut) }
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = scheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            QuickAction(
                "quick:pin",
                if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                if (pinned) OverlayText.QUICK_UNPIN else OverlayText.QUICK_PIN,
                if (pinned) OverlayText.UNPIN else OverlayText.PIN,
            ) { if (pinned) vm.unpin(key) else vm.pin(key) }
            QuickAction(
                "quick:dock",
                if (inDock) Icons.Rounded.RemoveCircleOutline else Icons.Rounded.AddCircleOutline,
                if (inDock) OverlayText.QUICK_UNDOCK else OverlayText.QUICK_DOCK,
                if (inDock) OverlayText.REMOVE_FROM_DOCK else OverlayText.ADD_TO_DOCK,
            ) {
                if (inDock) vm.removeFromDock(key) else vm.addToDock(key, onDockFull = { vm.showMessage(OverlayText.DOCK_FULL) })
            }
            QuickAction("quick:info", Icons.Outlined.Info, OverlayText.QUICK_INFO, OverlayText.APP_INFO) {
                vm.back()
                vm.openAppInfo(key)
            }
            QuickAction(
                "quick:hide",
                if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                if (hidden) OverlayText.QUICK_SHOW else OverlayText.QUICK_HIDE,
                if (hidden) OverlayText.UNHIDE else OverlayText.HIDE,
            ) {
                if (hidden) {
                    vm.unhide(key)
                } else {
                    vm.back()
                    vm.hide(key)
                }
            }
            QuickAction("quick:uninstall", Icons.Outlined.Delete, OverlayText.QUICK_UNINSTALL, OverlayText.UNINSTALL, destructive = true) {
                vm.back()
                vm.requestUninstall(key)
            }
        }
    }
}

/** An everyday action: an icon on a soft disc with a one-word label under it. */
@Composable
private fun QuickAction(
    id: String,
    icon: ImageVector,
    label: String,
    description: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (destructive) scheme.error else scheme.onSurface
    val press = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(56.dp)
            .pressScale(press)
            .controllerFocusable(
                id = id,
                onActivate = onClick,
                contentDescription = description,
                shape = RoundedCornerShape(16.dp),
                interactionSource = press,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).background(tint.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (destructive) scheme.error else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** One app shortcut: its own icon (loaded off the main thread) and its short label. */
@Composable
internal fun ShortcutRow(shortcut: AppShortcut, vm: LauncherViewModel, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .controllerFocusable(
                id = "shortcut:${shortcut.id}",
                onActivate = onClick,
                contentDescription = shortcut.label,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(scheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { ShortcutIcon(shortcut, vm) }
        Spacer(Modifier.width(14.dp))
        Text(
            shortcut.label,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ShortcutIcon(shortcut: AppShortcut, vm: LauncherViewModel) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { ShortcutIconSize.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, shortcut.id) {
        val dpi = context.resources.displayMetrics.densityDpi
        value = vm.shortcutIcon(shortcut, dpi)?.let { runCatching { it.toBitmap(px, px).asImageBitmap() }.getOrNull() }
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = Modifier.size(ShortcutIconSize))
    } else {
        Spacer(Modifier.size(ShortcutIconSize))
    }
}

private val ShortcutIconSize = 24.dp

private fun Modifier.pointerTapToDismiss(onTap: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures { onTap() } }
