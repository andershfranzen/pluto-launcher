package dev.pluto.launcher.ui.components

import androidx.compose.ui.res.stringResource
import dev.pluto.launcher.R
import dev.pluto.launcher.ui.UiText
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.data.prefs.ButtonMapping
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.motion.PlutoMotion

/** One legend entry: a launcher action and the word describing what it does here. */
data class LegendItem(val action: ControllerAction, val label: String)

/** Common legend sets, so every screen words its actions the same way (read in the current language). */
object Legends {
    val Home: List<LegendItem> get() = listOf(
        LegendItem(ControllerAction.CONFIRM, UiText.get(R.string.common_legend_open)),
        LegendItem(ControllerAction.ACTIONS, UiText.get(R.string.common_legend_actions)),
        LegendItem(ControllerAction.SEARCH, UiText.get(R.string.common_legend_search)),
        LegendItem(ControllerAction.SETTINGS, UiText.get(R.string.common_legend_settings)),
    )
    val Handheld: List<LegendItem> get() = listOf(
        LegendItem(ControllerAction.CONFIRM, UiText.get(R.string.common_legend_open)),
        LegendItem(ControllerAction.ACTIONS, UiText.get(R.string.common_legend_actions)),
        LegendItem(ControllerAction.SEARCH, UiText.get(R.string.common_legend_search)),
        LegendItem(ControllerAction.PREV_CATEGORY, UiText.get(R.string.common_legend_category)),
        LegendItem(ControllerAction.NEXT_CATEGORY, UiText.get(R.string.common_legend_category)),
        LegendItem(ControllerAction.SETTINGS, UiText.get(R.string.common_legend_settings)),
    )
    val Drawer: List<LegendItem> get() = listOf(
        LegendItem(ControllerAction.CONFIRM, UiText.get(R.string.common_legend_open)),
        LegendItem(ControllerAction.ACTIONS, UiText.get(R.string.common_legend_actions)),
        LegendItem(ControllerAction.SEARCH, UiText.get(R.string.common_legend_search)),
        LegendItem(ControllerAction.PREV_CATEGORY, UiText.get(R.string.common_legend_category)),
        LegendItem(ControllerAction.NEXT_CATEGORY, UiText.get(R.string.common_legend_category)),
        LegendItem(ControllerAction.BACK, UiText.get(R.string.common_legend_back)),
    )
    val Folder: List<LegendItem> get() = listOf(
        LegendItem(ControllerAction.CONFIRM, UiText.get(R.string.common_legend_open)),
        LegendItem(ControllerAction.ACTIONS, UiText.get(R.string.common_legend_actions)),
        LegendItem(ControllerAction.BACK, UiText.get(R.string.common_legend_close)),
    )
}

/** The button mapping in effect: the first connected controller's mapping, else the default. */
fun LauncherUiState.activeMapping(): ButtonMapping {
    val controller = controllers.firstOrNull() ?: return settings.defaultMapping
    return settings.mappingFor(controller.descriptor, controller.vendorId, controller.productId)
}

/**
 * Readable name for an Android key code, derived from its KeyEvent name rather than
 * assumed face-button lettering: KEYCODE_BUTTON_A -> "A", KEYCODE_BUTTON_START -> "Start".
 */
fun keyDisplayName(keyCode: Int): String {
    val raw = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").removePrefix("BUTTON_")
    return if (raw.length <= 2) raw else raw.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
}

/** Prompt name for an action: prefers a gamepad button over keyboard keys bound to the same action. */
fun ButtonMapping.promptFor(action: ControllerAction): String? {
    val keys = keysFor(action)
    if (keys.isEmpty()) return null
    val best = keys.minWith(compareBy<Int> { if (KeyEvent.isGamepadButton(it)) 0 else 1 }.thenBy { it })
    return keyDisplayName(best)
}

/**
 * Compact persistent legend of available controller actions, e.g.
 * "A Open · X Actions · Y Search · L1 / R1 Category · B Back". Actions with no bound key
 * are omitted. Consecutive items with the same label are merged ("L1 / R1 Category").
 *
 * Motion: the legend slides up and fades in when it appears (including when the caller
 * starts composing it), and slides down/fades out when [visible] turns false; prefer passing
 * [visible] over removing it from composition so it can animate out. When the set of
 * actions changes, the entries crossfade while the panel resizes.
 */
@Composable
fun ButtonLegend(
    mapping: ButtonMapping,
    items: List<LegendItem>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    quiet: Boolean = false,
) {
    val entries = buildList<Pair<List<String>, String>> {
        for (item in items) {
            val key = mapping.promptFor(item.action) ?: continue
            val last = lastOrNull()
            if (last != null && last.second == item.label) {
                set(lastIndex, (last.first + key) to item.label)
            } else {
                add(listOf(key) to item.label)
            }
        }
    }
    // Starts hidden so the legend animates in even when the caller only now composes it.
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible && entries.isNotEmpty()
    // Keep the last non-empty entries for the exit animation.
    val lastEntries = remember { arrayOf<List<Pair<List<String>, String>>>(emptyList()) }
    if (entries.isNotEmpty()) lastEntries[0] = entries
    val shown = lastEntries[0]
    if (shown.isEmpty()) return
    AnimatedVisibility(
        visibleState = visibility,
        modifier = modifier,
        enter = slideInVertically(PlutoMotion.slideSpring) { it / 2 } + fadeIn(PlutoMotion.fadeIn()),
        exit = slideOutVertically(PlutoMotion.slideSpring) { it / 2 } + fadeOut(PlutoMotion.fadeOut()),
    ) {
        val spoken = stringResource(
            R.string.common_legend_spoken,
            shown.joinToString(", ") { (keys, label) ->
                UiText.get(R.string.common_legend_spoken_entry, keys.reduce { a, b -> UiText.get(R.string.common_legend_spoken_or, a, b) }, label)
            },
        )
        Surface(
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
                .then(if (quiet) Modifier.background(Color(0xFF17191D).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)) else Modifier),
            shape = RoundedCornerShape(16.dp),
            color = if (quiet) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            contentColor = if (quiet) Color.White.copy(alpha = 0.70f) else MaterialTheme.colorScheme.onSurface,
        ) {
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    fadeIn(PlutoMotion.fadeIn())
                        .togetherWith(fadeOut(PlutoMotion.fadeOut()))
                        .using(SizeTransform(clip = false) { _, _ -> PlutoMotion.spatialFast() })
                },
                label = "legendEntries",
            ) { current ->
                LegendEntries(current)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendEntries(entries: List<Pair<List<String>, String>>) {
    FlowRow(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEach { (keys, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                keys.forEachIndexed { index, key ->
                    if (index > 0) {
                        Text("/", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 3.dp))
                    }
                    KeyChip(key)
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
