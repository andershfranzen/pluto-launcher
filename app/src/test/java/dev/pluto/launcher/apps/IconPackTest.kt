package dev.pluto.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconPackTest {
    @Test
    fun appfilterComponentsBecomeFlattenedComponentNames() {
        assertEquals("com.app/com.app.Main", IconPack.componentKey("ComponentInfo{com.app/com.app.Main}"))
        assertEquals("com.app/com.app.Main", IconPack.componentKey("ComponentInfo{com.app/.Main}"))
        assertEquals("com.app/com.other.Main", IconPack.componentKey(" ComponentInfo{com.app/com.other.Main} "))
    }

    @Test
    fun barePackagesAreKeptAndBrokenEntriesSkipped() {
        assertEquals("com.app", IconPack.componentKey("ComponentInfo{com.app}"))
        assertNull(IconPack.componentKey("ComponentInfo{}"))
        assertNull(IconPack.componentKey("ComponentInfo{com.app/}"))
        assertNull(IconPack.componentKey("ComponentInfo{/Main}"))
    }
}
