package dev.pluto.launcher.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/** Animation scale Pluto applies: 0 (no animation) with Reduce motion on, else the system's. */
internal fun effectiveMotionScale(reducedMotion: Boolean, systemScale: Float): Float =
    if (reducedMotion) 0f else systemScale.coerceAtLeast(0f)

/**
 * The [MotionDurationScale] every Compose animation in the launcher window runs under (it is
 * part of the window recomposer's context, see MainActivity). Compose skips straight to the
 * end of an animation when the scale is 0, so Reduce motion removes focus/scroll
 * animations, ripples and switch transitions everywhere at once, without touching each list.
 * [systemScale] mirrors Android's animator duration scale ("Remove animations" = 0).
 */
class LauncherMotionDurationScale : MotionDurationScale {
    @Volatile var reducedMotion: Boolean = false
    @Volatile var systemScale: Float = 1f

    override val scaleFactor: Float
        get() = effectiveMotionScale(reducedMotion, systemScale)
}

/**
 * Press feedback without animation, used instead of the ripple when Reduce motion is on:
 * a flat state layer while pressed. Focus is shown separately by the controller focus ring.
 */
internal object ReducedMotionIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = StaticPressNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = javaClass.hashCode()
}

private const val PRESSED_ALPHA = 0.12f

private class StaticPressNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var pressCount = 0

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                val before = pressCount > 0
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                if ((pressCount > 0) != before) invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressCount > 0) drawRect(currentValueOf(LocalContentColor).copy(alpha = PRESSED_ALPHA))
    }
}
