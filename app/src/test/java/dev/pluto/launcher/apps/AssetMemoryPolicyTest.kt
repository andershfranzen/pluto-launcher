package dev.pluto.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetMemoryPolicyTest {
    @Test fun backgroundAndCriticalPressureReleaseCacheOwnership() {
        listOf(15, 40, 60, 80).forEach { assertEquals(0, AssetMemoryPolicy.targetBytes(1000, it)) }
    }
    @Test fun HiddenOrLowPressureKeepsHalfAndNormalPressureDoesNothing() {
        listOf(10, 20).forEach { assertEquals(500, AssetMemoryPolicy.targetBytes(1000, it)) }
        listOf(0, 5).forEach { assertNull(AssetMemoryPolicy.targetBytes(1000, it)) }
    }
}
