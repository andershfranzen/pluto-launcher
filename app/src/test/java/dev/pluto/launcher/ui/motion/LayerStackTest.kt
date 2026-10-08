package dev.pluto.launcher.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerStackTest {
    private fun p(key: String) = StackSlot(key, present = true)
    private fun gone(key: String) = StackSlot(key, present = false)

    @Test
    fun pushAppendsPresentSlot() {
        assertEquals(listOf(p("drawer"), p("settings")), LayerStack.reconcile(listOf(p("drawer")), listOf("drawer", "settings")))
    }

    @Test
    fun popKeepsLeavingSlotOnTop() {
        assertEquals(
            listOf(p("drawer"), gone("settings")),
            LayerStack.reconcile(listOf(p("drawer"), p("settings")), listOf("drawer")),
        )
    }

    @Test
    fun closeAllKeepsEveryLayerLeavingInOrder() {
        assertEquals(
            listOf(gone("drawer"), gone("settings"), gone("hidden")),
            LayerStack.reconcile(listOf(p("drawer"), p("settings"), p("hidden")), emptyList()),
        )
    }

    @Test
    fun layerPushedWhileAnotherLeavesIsDrawnAbove() {
        // Back from Settings, then Edit opened before the exit finished.
        val afterPop = LayerStack.reconcile(listOf(p("drawer"), p("settings")), listOf("drawer"))
        assertEquals(
            listOf(p("drawer"), gone("settings"), p("edit")),
            LayerStack.reconcile(afterPop, listOf("drawer", "edit")),
        )
        // Replaced at the bottom: the leaving layer sits below the new one.
        assertEquals(listOf(gone("a"), p("b")), LayerStack.reconcile(listOf(p("a")), listOf("b")))
    }

    @Test
    fun keyReturningWhileLeavingBecomesPresentAgain() {
        val leaving = listOf(p("drawer"), gone("settings"))
        assertEquals(listOf(p("drawer"), p("settings")), LayerStack.reconcile(leaving, listOf("drawer", "settings")))
    }

    @Test
    fun reconcileIsStableForUnchangedInput() {
        val slots = listOf(p("drawer"), gone("folder:1"), p("settings"))
        assertEquals(slots, LayerStack.reconcile(slots, listOf("drawer", "settings")))
    }

    @Test
    fun finishedLeavingSlotsAreDropped() {
        val slots = listOf(p("drawer"), gone("settings"), gone("hidden"))
        assertEquals(listOf(p("drawer"), gone("hidden")), LayerStack.withoutFinished(slots, setOf("settings")))
        // A present slot is never dropped even if its key is listed.
        assertEquals(slots.take(1), LayerStack.withoutFinished(slots.take(1), setOf("drawer")))
    }

    @Test
    fun composedRangeStartsAtTopmostSettledCover() {
        val covers = setOf("settings", "hidden")
        val slots = listOf(p("drawer"), p("settings"), p("folder:2"))
        assertEquals(1, LayerStack.firstComposed(slots) { it.key in covers })
        assertFalse(LayerStack.baseComposed(slots) { it.key in covers })
        // Nothing settled yet (e.g. Settings still entering): everything, incl. base, composed.
        assertEquals(0, LayerStack.firstComposed(slots) { false })
        assertTrue(LayerStack.baseComposed(slots) { false })
        // A leaving cover no longer hides what is beneath.
        val leaving = listOf(p("drawer"), gone("settings"))
        assertEquals(0, LayerStack.firstComposed(leaving) { it.key in covers })
        assertTrue(LayerStack.baseComposed(leaving) { it.key in covers })
    }

    @Test
    fun hiddenLeavingSlotsBelowCoverAreDropped() {
        val slots = listOf(gone("folder:1"), p("drawer"), gone("actions:x"), p("settings"))
        assertEquals(listOf(p("drawer"), p("settings")), LayerStack.withoutHiddenLeaving(slots, 3))
        assertEquals(slots, LayerStack.withoutHiddenLeaving(slots, 0))
    }

    @Test
    fun topAndCover() {
        val slots = listOf(p("drawer"), p("folder:1"), p("settings"), gone("hidden"))
        assertEquals(2, LayerStack.topPresent(slots))
        val fullScreen = setOf("drawer", "settings", "hidden")
        assertEquals(0, LayerStack.coverOf(slots, -1) { it.key in fullScreen })
        assertEquals(2, LayerStack.coverOf(slots, 0) { it.key in fullScreen })
        assertEquals(3, LayerStack.coverOf(slots, 2) { it.key in fullScreen })
        assertEquals(-1, LayerStack.coverOf(slots, 3) { it.key in fullScreen })
        assertEquals(-1, LayerStack.topPresent(listOf(gone("a"))))
    }
}
