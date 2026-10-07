package dev.pluto.launcher.domain

import dev.pluto.launcher.model.ReorderOp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    private val list = listOf("a", "b", "c", "d")

    @Test
    fun upSwapsWithPrevious() {
        assertEquals(listOf("a", "c", "b", "d"), Reorder.apply(list, "c", ReorderOp.Up))
    }

    @Test
    fun upAtStartIsNoOp() {
        assertEquals(list, Reorder.apply(list, "a", ReorderOp.Up))
    }

    @Test
    fun downSwapsWithNext() {
        assertEquals(listOf("b", "a", "c", "d"), Reorder.apply(list, "a", ReorderOp.Down))
    }

    @Test
    fun downAtEndIsNoOp() {
        assertEquals(list, Reorder.apply(list, "d", ReorderOp.Down))
    }

    @Test
    fun beforeMovesForwardAndBackward() {
        assertEquals(listOf("d", "a", "b", "c"), Reorder.apply(list, "d", ReorderOp.Before("a")))
        assertEquals(listOf("b", "c", "a", "d"), Reorder.apply(list, "a", ReorderOp.Before("d")))
        assertEquals(listOf("a", "c", "b", "d"), Reorder.apply(list, "c", ReorderOp.Before("b")))
    }

    @Test
    fun beforeImmediateSuccessorIsUnchangedOrder() {
        assertEquals(list, Reorder.apply(list, "a", ReorderOp.Before("b")))
    }

    @Test
    fun afterMovesForwardAndBackward() {
        assertEquals(listOf("b", "c", "d", "a"), Reorder.apply(list, "a", ReorderOp.After("d")))
        assertEquals(listOf("a", "d", "b", "c"), Reorder.apply(list, "d", ReorderOp.After("a")))
        assertEquals(listOf("a", "c", "b", "d"), Reorder.apply(list, "b", ReorderOp.After("c")))
    }

    @Test
    fun afterImmediatePredecessorIsUnchangedOrder() {
        assertEquals(list, Reorder.apply(list, "b", ReorderOp.After("a")))
    }

    @Test
    fun missingItemIsNoOp() {
        assertEquals(list, Reorder.apply(list, "x", ReorderOp.Up))
        assertEquals(list, Reorder.apply(list, "x", ReorderOp.Down))
        assertEquals(list, Reorder.apply(list, "x", ReorderOp.Before("a")))
        assertEquals(list, Reorder.apply(list, "x", ReorderOp.After("a")))
    }

    @Test
    fun missingTargetIsNoOp() {
        assertEquals(list, Reorder.apply(list, "a", ReorderOp.Before("x")))
        assertEquals(list, Reorder.apply(list, "a", ReorderOp.After("x")))
    }

    @Test
    fun targetEqualToItemIsNoOp() {
        assertEquals(list, Reorder.apply(list, "b", ReorderOp.Before("b")))
        assertEquals(list, Reorder.apply(list, "b", ReorderOp.After("b")))
    }

    @Test
    fun emptyAndSingletonLists() {
        assertEquals(emptyList<String>(), Reorder.apply(emptyList(), "a", ReorderOp.Up))
        assertEquals(listOf("a"), Reorder.apply(listOf("a"), "a", ReorderOp.Up))
        assertEquals(listOf("a"), Reorder.apply(listOf("a"), "a", ReorderOp.Down))
    }

    @Test
    fun doesNotMutateInput() {
        val input = mutableListOf("a", "b", "c")
        val result = Reorder.apply(input, "a", ReorderOp.After("c"))
        assertEquals(listOf("a", "b", "c"), input)
        assertEquals(listOf("b", "c", "a"), result)
    }

    @Test
    fun preservesSizeAndElements() {
        val ops = listOf<ReorderOp<String>>(ReorderOp.Up, ReorderOp.Down, ReorderOp.Before("a"), ReorderOp.After("d"))
        for (item in list) for (op in ops) {
            val result = Reorder.apply(list, item, op)
            assertEquals(list.sorted(), result.sorted())
        }
    }
}
