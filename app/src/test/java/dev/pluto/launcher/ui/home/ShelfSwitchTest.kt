package dev.pluto.launcher.ui.home

import dev.pluto.launcher.ui.console.CoverflowState
import dev.pluto.launcher.ui.console.ShelfId
import org.junit.Assert.*
import org.junit.Test

class ShelfSwitchTest {
    private fun stage() = CoverflowState(0, emptyList())

    @Test fun shelvesCrossfadeAtFixedPositionsAndSettleFrom108Percent() {
        val switch = ShelfSwitch(ShelfId.Recent, stage())
        switch.begin(ShelfId.Favourites, stage())
        assertEquals(1.08f, switch.incomingMotion.scale(), 0.0001f)
        switch.progress = 0.5f
        assertEquals(0f, switch.incomingMotion.offset(), 0f)
        assertEquals(0f, switch.outgoingMotion.offset(), 0f)
        assertEquals(0.5f, switch.incomingMotion.alpha(), 0.0001f)
        assertEquals(0.5f, switch.outgoingMotion.alpha(), 0.0001f)
        switch.progress = 1f
        assertEquals(1f, switch.incomingMotion.scale(), 0.0001f)
        assertEquals(1f, switch.incomingMotion.alpha(), 0f)
        assertEquals(0f, switch.outgoingMotion.alpha(), 0f)
    }

    @Test fun reversingPreservesBothPagesOpacityAndScale() {
        val original = stage()
        val switch = ShelfSwitch(ShelfId.Recent, original)
        switch.begin(ShelfId.Favourites, stage())
        switch.progress = 0.3f
        val outgoingAlpha = switch.outgoingMotion.alpha()
        val outgoingScale = switch.outgoingMotion.scale()
        val incomingAlpha = switch.incomingMotion.alpha()
        val incomingScale = switch.incomingMotion.scale()
        switch.begin(ShelfId.Recent, stage())
        assertSame(original, switch.stage)
        assertEquals(outgoingAlpha, switch.incomingMotion.alpha(), 0f)
        assertEquals(outgoingScale, switch.incomingMotion.scale(), 0f)
        assertEquals(incomingAlpha, switch.outgoingMotion.alpha(), 0f)
        assertEquals(incomingScale, switch.outgoingMotion.scale(), 0f)
    }

    @Test fun rapidThirdShelfRetainsTheMostVisiblePage() {
        val original = stage()
        val switch = ShelfSwitch(ShelfId.Recent, original)
        switch.begin(ShelfId.Favourites, stage())
        switch.progress = 0.2f
        switch.begin(ShelfId.OfCategory(3), stage())
        assertSame(original, switch.outgoingStage)
        assertEquals(ShelfId.Recent, switch.outgoingShelf)
        assertEquals(0.8f, switch.outgoingMotion.alpha(), 0.0001f)
        assertEquals(0f, switch.incomingMotion.alpha(), 0f)
        switch.progress = 1f
        assertEquals(1f, switch.incomingMotion.alpha(), 0f)
    }
}
