package dev.pluto.launcher.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusOrdersTest {
    @Test
    fun presentIdResolvesToItself() {
        val orders = FocusOrders()
        orders.set("drawer", listOf("a", "b", "c"))
        assertEquals(FocusOrders.Replacement("drawer", "b", 1), orders.replacementFor("b"))
    }

    @Test
    fun vanishedIdResolvesToNextSurvivingNeighbour() {
        val orders = FocusOrders()
        orders.set("home", listOf("gmail", "camera", "files", "maps"))
        orders.set("home", listOf("gmail", "files", "maps"))
        assertEquals(FocusOrders.Replacement("home", "files", 1), orders.replacementFor("camera"))
    }

    @Test
    fun lastItemVanishedFallsBackToPrevious() {
        val orders = FocusOrders()
        orders.set("hh:fav", listOf("a", "b", "c"))
        orders.set("hh:fav", listOf("a", "b"))
        assertEquals("b", orders.replacementFor("c")?.id)
    }

    @Test
    fun resolvesWithinTheSurfaceTheIdBelongedTo() {
        val orders = FocusOrders()
        orders.set("hh:fav", listOf("fav:a", "fav:b", "fav:c"))
        orders.set("hh:cat", listOf("cat:a", "cat:b", "cat:c"))
        orders.set("hh:fav", listOf("fav:a", "fav:c"))
        assertEquals("hh:fav", orders.surfaceOf("fav:b"))
        assertEquals("fav:c", orders.replacementFor("fav:b")?.id)
    }

    @Test
    fun unknownIdHasNoReplacement() {
        val orders = FocusOrders()
        orders.set("drawer", listOf("a"))
        assertNull(orders.replacementFor("dock:2"))
    }

    @Test
    fun emptiedListHasNoReplacement() {
        val orders = FocusOrders()
        orders.set("folder:1", listOf("x"))
        orders.set("folder:1", emptyList())
        assertNull(orders.replacementFor("x"))
    }

    @Test
    fun settingTheSameOrderKeepsThePreviousOne() {
        val orders = FocusOrders()
        orders.set("drawer", listOf("a", "b", "c"))
        orders.set("drawer", listOf("a", "c"))
        orders.set("drawer", listOf("a", "c")) // recomposition with unchanged list
        assertEquals("c", orders.replacementFor("b")?.id)
    }
}
