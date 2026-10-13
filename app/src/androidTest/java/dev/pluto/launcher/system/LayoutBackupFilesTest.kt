package dev.pluto.launcher.system

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LayoutBackupFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = LayoutBackupFiles(context)

    private fun cacheFile(name: String) = File(context.cacheDir, name)

    @Test
    fun aWrittenFileReadsBackUnchanged() {
        val file = cacheFile("round-trip.txt")
        val text = "pluto-layout=1\nhome.0=app:0|com.a/.A\nfolder.7.name=Games = fun\n"
        files.write(Uri.fromFile(file), text)
        assertEquals(text, files.read(Uri.fromFile(file)))
    }

    @Test
    fun writingAgainReplacesTheOldContentInsteadOfAppending() {
        val file = cacheFile("replace.txt")
        files.write(Uri.fromFile(file), "a much longer first backup")
        files.write(Uri.fromFile(file), "second")
        assertEquals("second", files.read(Uri.fromFile(file)))
    }

    @Test
    fun aFileTooBigToBeALayoutIsRefused() {
        val file = cacheFile("huge.txt").apply { writeText("x".repeat(LayoutBackupFiles.MAX_BYTES + 1)) }
        val error = runCatching { files.read(Uri.fromFile(file)) }.exceptionOrNull()
        assertTrue("expected a refusal, got $error", error != null)
    }
}
