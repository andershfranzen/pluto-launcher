package dev.pluto.launcher.ui.console

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverDecodeSizeTest {
    @Test
    fun largeCoverRetainsOnlyDisplayPixelsAndAspectRatio() {
        assertEquals(192 to 256, CoverDecodeSize.fit(3000, 4000, 256))
        assertEquals(256 to 192, CoverDecodeSize.fit(4000, 3000, 256))
        assertEquals(256 to 256, CoverDecodeSize.fit(1024, 1024, 256))
    }

    @Test
    fun smallAndExtremelyNarrowCoversRemainValid() {
        assertEquals(30 to 40, CoverDecodeSize.fit(30, 40, 256))
        assertEquals(1 to 256, CoverDecodeSize.fit(1, 4000, 256))
    }
}
