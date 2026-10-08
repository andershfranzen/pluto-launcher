package dev.pluto.launcher.ui.motion

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo

/**
 * The visual state of one moving surface for the current frame, filled in by a
 * [drawMotion] block. Defaults are the identity (fully shown, in place).
 */
class MotionFrame internal constructor() {
    var alpha = 1f
    var translationX = 0f
    var translationY = 0f
    var scaleX = 1f
    var scaleY = 1f
    var transformOrigin = TransformOrigin.Center

    /**
     * Nothing below this y (px, untransformed) is drawn: the part of a surface hidden under an
     * opaque panel above it costs no GPU time.
     */
    var clipBottom = Float.POSITIVE_INFINITY

    /**
     * Apply [alpha] to each drawing operation instead of to an offscreen copy of the whole
     * surface. Much cheaper for a large surface that stays translucent for a long time (home
     * receded under the open drawer); overlapping content inside shows through itself
     * slightly, which is invisible on a dimmed, receding background.
     */
    var modulateAlpha = false

    /** Size of the surface in px and the density, for size-relative motion. */
    var width = 0f
        internal set
    var height = 0f
        internal set
    var density = 1f
        internal set

    internal fun reset(width: Float, height: Float, density: Float) {
        alpha = 1f
        translationX = 0f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        transformOrigin = TransformOrigin.Center
        clipBottom = Float.POSITIVE_INFINITY
        modulateAlpha = false
        this.width = width
        this.height = height
        this.density = density
    }

    internal val isIdentity: Boolean
        get() = alpha >= 1f && translationX == 0f && translationY == 0f && scaleX == 1f && scaleY == 1f
}

/**
 * Moves, scales and fades content in the draw phase only. [block] runs every frame it
 * reads State from (progress values), like a graphicsLayer lambda.
 *
 * Unlike Modifier.graphicsLayer, the transform is invisible to Compose's layout system:
 * moving a graphicsLayer makes Compose recompute the window bounds of every node in the
 * subtree (and fire onGloballyPositioned for each) on every frame, which for the drawer
 * (dozens of tiles) or home under it cost more than drawing. Here the content is drawn
 * through a static child layer recorded once, and only a lightweight render-node transform
 * changes per frame.
 *
 * Visual only: hit testing, focus bounds and positions reported to OriginRegistry stay at
 * the resting layout position. Callers block pointer input on surfaces that are leaving,
 * and use it only for motion that ends at rest (identity) or fully hidden.
 */
fun Modifier.drawMotion(block: MotionFrame.() -> Unit): Modifier =
    this then DrawMotionElement(block) then Modifier.graphicsLayer()

private class DrawMotionElement(val block: MotionFrame.() -> Unit) : ModifierNodeElement<DrawMotionNode>() {
    override fun create() = DrawMotionNode(block)

    override fun update(node: DrawMotionNode) {
        node.block = block
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawMotion"
    }

    // A new lambda each recomposition is a new block; draw is invalidated in update.
    override fun equals(other: Any?): Boolean = other is DrawMotionElement && other.block === block
    override fun hashCode(): Int = block.hashCode()
}

private class DrawMotionNode(var block: MotionFrame.() -> Unit) : Modifier.Node(), DrawModifierNode {
    private val frame = MotionFrame()
    private var layer: GraphicsLayer? = null

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun ContentDrawScope.draw() {
        frame.reset(size.width, size.height, density)
        frame.block()
        if (frame.alpha <= 0f || frame.clipBottom <= 0f) return
        if (frame.clipBottom < size.height) {
            val content = this
            clipRect(bottom = frame.clipBottom) { content.drawFrame() }
        } else {
            drawFrame()
        }
    }

    private fun ContentDrawScope.drawFrame() {
        if (frame.isIdentity) {
            drawContent()
            return
        }
        val l = layer ?: requireGraphicsContext().createGraphicsLayer().also { layer = it }
        l.record { this@drawFrame.drawContent() }
        l.alpha = frame.alpha.coerceAtMost(1f)
        val strategy = if (frame.modulateAlpha) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
        if (l.compositingStrategy != strategy) l.compositingStrategy = strategy
        l.translationX = frame.translationX
        l.translationY = frame.translationY
        l.scaleX = frame.scaleX
        l.scaleY = frame.scaleY
        l.pivotOffset = Offset(size.width * frame.transformOrigin.pivotFractionX, size.height * frame.transformOrigin.pivotFractionY)
        drawLayer(l)
    }
}
