package dev.pluto.launcher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.motion.PlutoMotion
import java.text.Normalizer

/**
 * Sections of an alphabetical list for [FastScrollRail]: the rail's letters, the first item
 * index of each letter, and the section of every item. Built once per list, off the hot path.
 */
@Immutable
class ScrollSections(
    val letters: List<String>,
    private val firstIndex: IntArray,
    private val sectionOfItem: IntArray,
) {
    val size: Int get() = letters.size

    fun firstItemOf(section: Int): Int = firstIndex[section.coerceIn(0, firstIndex.size - 1)]

    fun sectionOf(item: Int): Int = if (item in sectionOfItem.indices) sectionOfItem[item] else -1

    companion object {
        /** Sections for [labels] in list order; a letter seen again later joins its first section. */
        fun of(labels: List<String>): ScrollSections {
            val letters = ArrayList<String>()
            val first = ArrayList<Int>()
            val byLetter = HashMap<String, Int>()
            val ofItem = IntArray(labels.size)
            labels.forEachIndexed { index, label ->
                val letter = sectionLetter(label)
                val section = byLetter.getOrPut(letter) {
                    letters += letter
                    first += index
                    letters.size - 1
                }
                ofItem[index] = section
            }
            return ScrollSections(letters, first.toIntArray(), ofItem)
        }

        /** The rail letter of a label: its first letter without accents, upper case, or "#". */
        fun sectionLetter(label: String): String {
            val c = label.trimStart().firstOrNull() ?: return "#"
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: return "#"
            return if (base.isLetter()) base.uppercaseChar().toString() else "#"
        }
    }
}

@Stable
private class RailState {
    var active by mutableStateOf(false)
    var touchY by mutableFloatStateOf(0f)
    var section by mutableIntStateOf(-1)

    /** The rail's top within the overlay, so the bubble lines up with the finger. */
    var railTop by mutableFloatStateOf(0f)
}

/** Width of the rail's touch strip; the scrolling list keeps this much free at its end. */
val FastScrollRailWidth = 32.dp

/** Tallest a letter row gets; short lists keep a compact, centred rail. */
private val MaxRowHeight = 22.dp
private val BubbleSize = 60.dp

/**
 * Alphabet fast-scroll rail for an alphabetical grid, placed in a [BoxScope] over the
 * grid's area: letters along the end edge, the current section highlighted while
 * scrolling. Touching or dragging along it jumps the grid to that letter (with a light
 * haptic tick per letter) and shows the letter in a bubble beside the finger.
 *
 * A touch shortcut only: it is hidden from accessibility services and not controller
 * focusable, as the grid itself stays fully reachable both ways. Drawn in one node; while
 * scrolling only its draw phase runs (to move the highlight).
 */
@Composable
fun BoxScope.FastScrollRail(
    sections: ScrollSections,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    if (sections.size == 0) return
    val state = remember { RailState() }
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val currentSections by rememberUpdatedState(sections)
    val letterStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val letterColor = MaterialTheme.colorScheme.onSurfaceVariant
    val activeColor = MaterialTheme.colorScheme.onPrimaryContainer
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Box(
        modifier
            .align(Alignment.CenterEnd)
            // Only as tall as its letters need: the rest of the edge keeps scrolling the grid.
            .heightIn(max = MaxRowHeight * sections.size)
            .fillMaxHeight()
            .onPlaced { state.railTop = it.positionInParent().y }
            .width(FastScrollRailWidth)
            .clearAndSetSemantics { }
            .pointerInput(gridState) {
                fun select(y: Float) {
                    val s = currentSections
                    val n = s.size
                    val row = minOf(size.height.toFloat() / n, MaxRowHeight.toPx())
                    val top = (size.height - row * n) / 2f
                    val index = ((y - top) / row).toInt().coerceIn(0, n - 1)
                    state.touchY = state.railTop + top + row * (index + 0.5f)
                    if (index != state.section) {
                        state.section = index
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        gridState.requestScrollToItem(s.firstItemOf(index))
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    state.section = -1
                    state.active = true
                    select(down.position.y)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            select(change.position.y)
                        }
                    } finally {
                        state.active = false
                    }
                }
            }
            .drawWithCache {
                val n = sections.size
                val row = minOf(size.height / n, MaxRowHeight.toPx())
                val top = (size.height - row * n) / 2f
                val layouts = sections.letters.map { measurer.measure(it, letterStyle) }
                val radius = minOf(row, size.width) / 2f
                // In a short window (landscape) rows can get smaller than a letter: draw every
                // step-th letter then (touch still reaches every letter).
                val letterHeight = layouts.maxOfOrNull { it.size.height } ?: 1
                val step = kotlin.math.ceil(letterHeight / row).toInt().coerceAtLeast(1)
                onDrawBehind {
                    val active = state.active
                    if (active) {
                        drawRoundRect(track, cornerRadius = CornerRadius(size.width / 2f, size.width / 2f))
                    }
                    // The section of the first visible item (read here, so scrolling only redraws the rail).
                    val current = if (active) state.section else sections.sectionOf(gridState.firstVisibleItemIndex)
                    layouts.forEachIndexed { i, layout ->
                        val cy = top + row * (i + 0.5f)
                        val selected = i == current
                        if (!selected && i % step != 0) return@forEachIndexed
                        if (selected) drawCircle(highlight, radius = radius, center = Offset(size.width / 2f, cy))
                        drawText(
                            layout,
                            color = if (selected) activeColor else letterColor,
                            topLeft = Offset((size.width - layout.size.width) / 2f, cy - layout.size.height / 2f),
                        )
                    }
                }
            },
    )
    RailBubble(state, sections, Modifier.align(Alignment.TopEnd).padding(end = FastScrollRailWidth + 12.dp))
}

/** The letter bubble beside the finger; it grows out of the rail and follows in the layer phase. */
@Composable
private fun RailBubble(state: RailState, sections: ScrollSections, modifier: Modifier) {
    val shown = animateFloatAsState(if (state.active) 1f else 0f, PlutoMotion.spatialFast(), label = "railBubble")
    val index = state.section
    Box(
        modifier
            .size(BubbleSize)
            .clearAndSetSemantics { }
            .graphicsLayer {
                val s = shown.value
                alpha = s.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * s
                scaleY = 0.6f + 0.4f * s
                transformOrigin = TransformOrigin(1f, 0.5f)
                translationY = state.touchY - size.height / 2f
            }
            .background(MaterialTheme.colorScheme.primaryContainer, BubbleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (index >= 0) {
            Text(
                sections.letters.getOrElse(index) { "" },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** A round bubble with its tip towards the rail (bottom end corner). */
private val BubbleShape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 50)
