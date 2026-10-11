package dev.pluto.launcher.ui.console

import androidx.compose.ui.graphics.asAndroidBitmap

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asImageBitmap
import dev.pluto.launcher.LauncherApplication
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.model.AppKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class ConsoleArtCacheTest {
    @Test
    fun wideColorArtCountsItsFullAllocationAgainstTheMemoryBudget() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.RGBA_F16)
        try {
            val art = ConsoleArt(bitmap.asImageBitmap(), null, Color.BLUE)
            assertEquals(16 * 16 * 8, art.bytes)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun prefetchUsesCurrentIconGenerationAfterPackageInvalidation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icons = (context.applicationContext as LauncherApplication).container.icons
        val user = Process.myUserHandle()
        val info = context.getSystemService(LauncherApps::class.java).getActivityList(null, user).first()
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        val key = AppKey(info.componentName.flattenToString(), serial)
        icons.prefetch(listOf(key), listOf(97))
        assertNotNull(icons.peek(key, 97))
        icons.invalidatePackage(key.packageName)
        icons.prefetch(listOf(key), listOf(97))
        assertNotNull(icons.peek(key, 97))
        assertSame(icons.peek(key, 97), icons.load(key, 97))
    }

    @Test
    fun concurrentCoverRequestsShareArtAndRetainOnlyDisplayPixels() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icons = (context.applicationContext as LauncherApplication).container.icons
        val file = File.createTempFile("cover-decode-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val entry = ConsoleEntry.Rom(-1, RomGame(file.toURI().toString(), "cover.cue", "Cover", 1,
                Uri.fromFile(file).toString()))
            val arts = List(8) { async { ConsoleArtCache.load(context, icons, entry, 256, 0) } }.awaitAll()
            val art = arts.first()!!
            assertEquals(256, art.icon.width)
            assertEquals(192, art.icon.height)
            assertNotNull(art.reflection)
            arts.forEach { assertSame(art, it) }
            assertSame(art, ConsoleArtCache.peek(entry, 256, 0))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            file.delete()
        }
    }

    @Test
    fun unreadableCoverUsesCachedDiscWithoutRepeatingProviderRequests() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icons = (context.applicationContext as LauncherApplication).container.icons
        val entry = ConsoleEntry.Rom(-1, RomGame("content://missing/game", "fallback.cue", "Fallback", 1,
            "content://missing-provider/cover"))
        val art = ConsoleArtCache.load(context, icons, entry, 128, 0)!!
        assertEquals(128, art.icon.width)
        assertEquals(128, art.icon.height)
        assertSame(art, ConsoleArtCache.load(context, icons, entry, 128, 0))
    }
    @Test
    fun memoryTrimReleasesCachesWithoutRecyclingDisplayedArt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icons = (context.applicationContext as LauncherApplication).container.icons
        val user = Process.myUserHandle()
        val info = context.getSystemService(LauncherApps::class.java).getActivityList(null, user).first()
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        val key = AppKey(info.componentName.flattenToString(), serial)
        val version = icons.versionOf(key.packageName)
        val art = ConsoleArtCache.load(icons, key, 101, version)!!
        val bitmap = art.icon.asAndroidBitmap()
        val pixel = bitmap.getPixel(0, 0)
        icons.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        ConsoleArtCache.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(null, icons.peek(key, 101))
        assertEquals(null, ConsoleArtCache.peek(key, 101, version))
        org.junit.Assert.assertFalse(bitmap.isRecycled)
        assertEquals(pixel, bitmap.getPixel(0, 0))
        assertNotNull(ConsoleArtCache.load(icons, key, 101, version))
    }

}
