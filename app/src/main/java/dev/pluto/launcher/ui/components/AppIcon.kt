package dev.pluto.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.model.AppEntry

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("IconCache not provided") }

/** App icon loaded off the main thread via [LocalIconCache]; shows a neutral placeholder meanwhile. Decorative (no semantics). */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier): Unit = TODO()

/**
 * Standard app tile: icon + label (max 2 lines, ellipsis), controller-focusable via
 * Modifier.controllerFocusable(id = entry.key.encode(), ...), min 48dp target.
 * Disabled (suspended) apps render dimmed with "(unavailable)" in the a11y label.
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
): Unit = TODO()
