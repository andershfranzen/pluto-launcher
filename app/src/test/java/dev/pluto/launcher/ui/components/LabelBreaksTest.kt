package dev.pluto.launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelBreaksTest {
    @Test
    fun breaksAfterPunctuationBetweenLetters() {
        assertEquals("Brotato:​Premium", withBreakOpportunities("Brotato:Premium"))
        assertEquals("F-​Droid", withBreakOpportunities("F-Droid"))
    }

    @Test
    fun leavesOtherTextAlone() {
        assertEquals("GTA: SA", withBreakOpportunities("GTA: SA"))
        assertEquals("Clock", withBreakOpportunities("Clock"))
        assertEquals("v1.2", withBreakOpportunities("v1.2").replace("​", ""))
    }
}
