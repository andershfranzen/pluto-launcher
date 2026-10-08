package dev.pluto.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Returns the instance passed on an earlier composition while [value] is still equal to it.
 *
 * The launcher state is rebuilt as a whole on every change (selection, scroll anchor, search
 * text...), so lists such as the drawer's apps arrive as new but equal instances. Compose
 * compares unstable parameters by identity, so a child given a new equal list cannot skip.
 * Passing it through here hands children the same instance and lets them skip; the equality
 * check (a list of small data classes) costs far less than recomposing a grid.
 */
@Composable
fun <T> rememberEqual(value: T): T {
    val holder = remember { EqualHolder(value) }
    if (holder.value !== value && holder.value != value) holder.value = value
    return holder.value
}

private class EqualHolder<T>(var value: T)
