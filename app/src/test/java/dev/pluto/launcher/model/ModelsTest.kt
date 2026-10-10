package dev.pluto.launcher.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    private val key = AppKey("com.example.app/com.example.app.MainActivity", 0)

    @Test
    fun appKeyEncodeDecodeRoundTrip() {
        assertEquals("0|com.example.app/com.example.app.MainActivity", key.encode())
        assertEquals(key, AppKey.decode(key.encode()))
        val work = AppKey("com.example.app/.Main", 11)
        assertEquals(work, AppKey.decode(work.encode()))
        assertEquals(work.encode(), work.toString())
    }

    @Test
    fun appKeyPackageName() {
        assertEquals("com.example.app", key.packageName)
        assertEquals("com.example.app", AppEntry(key, "App").packageName)
    }

    @Test
    fun appKeyDecodeRejectsMalformed() {
        assertNull(AppKey.decode(""))
        assertNull(AppKey.decode("|com.a/.B"))
        assertNull(AppKey.decode("0|"))
        assertNull(AppKey.decode("abc|com.a/.B"))
        assertNull(AppKey.decode("0|com.a.B"))
        assertNull(AppKey.decode("com.a/.B"))
    }

    @Test
    fun appKeyDecodeKeepsPipesInComponent() {
        assertEquals(AppKey("com.a/.B|C", 3), AppKey.decode("3|com.a/.B|C"))
    }

    @Test
    fun homeItemIdsRoundTrip() {
        val app = HomeItem.App(key)
        val folder = HomeItem.FolderRef(42)
        assertEquals("app:" + key.encode(), app.id)
        assertEquals("folder:42", folder.id)
        assertEquals(app, HomeItem.decode(app.id))
        assertEquals(folder, HomeItem.decode(folder.id))
    }

    @Test
    fun homeItemDecodeRejectsMalformed() {
        assertNull(HomeItem.decode(""))
        assertNull(HomeItem.decode("folder:abc"))
        assertNull(HomeItem.decode("app:not-a-key"))
        assertNull(HomeItem.decode("gadget:1"))
    }

    @Test
    fun organizationDefaultsHaveFiveEmptyDockSlots() {
        val org = Organization()
        assertEquals(Organization.DOCK_SLOTS, org.dock.size)
        assertEquals(List(5) { null }, org.dock)
    }

    @Test
    fun widgetItemsRoundTrip() {
        val item = HomeItem.Widget(42)
        org.junit.Assert.assertEquals("widget:42", item.id)
        org.junit.Assert.assertEquals(item, HomeItem.decode(item.id))
        org.junit.Assert.assertNull(HomeItem.decode("widget:x"))
    }
}
