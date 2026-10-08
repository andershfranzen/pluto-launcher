package dev.pluto.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import dev.pluto.launcher.ui.motion.PlutoMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shrinks the element to [pressedScale] while [interactionSource] reports a press and springs
 * back on release/cancel. Visual only: drawn through a graphics layer, so layout geometry
 * and neighbours never move, and nothing recomposes per frame. Interruptible (spring).
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = PlutoMotion.PRESSED_SCALE,
): Modifier = this then PressScaleElement(interactionSource, pressedScale)

private data class PressScaleElement(
    val interactionSource: InteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(interactionSource, pressedScale)

    override fun update(node: PressScaleNode) {
        node.pressedScale = pressedScale
        if (node.interactionSource !== interactionSource) {
            node.interactionSource = interactionSource
            node.restart()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["pressedScale"] = pressedScale
    }
}

private class PressScaleNode(
    var interactionSource: InteractionSource,
    var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {
    private val scale = Animatable(1f)
    private var collector: Job? = null
    private var presses = 0

    // Allocated once; reads the animated value in the layer phase only.
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val s = scale.value
        scaleX = s
        scaleY = s
    }

    override fun onAttach() {
        restart()
    }

    override fun onDetach() {
        collector = null
        presses = 0
    }

    fun restart() {
        if (!isAttached) return
        collector?.cancel()
        presses = 0
        collector = coroutineScope.launch {
            // A recycled (lazy) item must never come back shrunk.
            scale.snapTo(1f)
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val target = if (presses > 0) pressedScale else 1f
                launch { scale.animateTo(target, PlutoMotion.spatialFast()) }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }
}
