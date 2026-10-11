package dev.pluto.launcher.updates

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateRepositoryTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val testContext = object : ContextWrapper(app) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("update-test-$name", mode)
    }
    private fun metadata(tag: String = "v99.0.0", digest: String = "sha256:" + "a".repeat(64)) = JSONObject()
        .put("tag_name", tag).put("html_url", "${UpdateRelease.REPO_URL}/releases/tag/$tag")
        .put("draft", false).put("prerelease", false).put("assets", JSONArray().put(JSONObject()
            .put("name", "app-release.apk").put("browser_download_url", "${UpdateRelease.REPO_URL}/releases/download/$tag/app-release.apk")
            .put("size", 100).put("digest", digest)))

    @Test fun githubMetadataAcceptsStableNewerReleaseButRejectsPreviewsAndUnverifiedAssets() {
        val repo = UpdateRepository(testContext)
        assertEquals("99.0.0", repo.parseRelease(metadata().toString())!!.versionName)
        assertNull(repo.parseRelease(metadata("v0.0.0").toString()))
        assertThrows(IllegalArgumentException::class.java) { repo.parseRelease(metadata().put("prerelease", true).toString()) }
        assertThrows(IllegalArgumentException::class.java) { repo.parseRelease(metadata(digest = "").toString()) }
    }

    @Test fun installerGetsOnlyAReadOnlyContentUriForPrivateUpdateFiles() {
        val file = File(app.filesDir, "updates/test-installer.apk").apply { parentFile!!.mkdirs(); writeText("fixture") }
        try {
            val intent = UpdateRepository(testContext).installIntent(file)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("content", intent.data!!.scheme)
            assertEquals("${app.packageName}.updates", intent.data!!.authority)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals("fixture", app.contentResolver.openInputStream(intent.data!!)!!.bufferedReader().use { it.readText() })
        } finally { file.delete() }
    }

    @Test fun apkVerificationRejectsChecksumMismatchAndReinstallOfCurrentVersion() {
        val repo = UpdateRepository(testContext)
        val file = File(app.applicationInfo.sourceDir)
        val installed = app.packageManager.getPackageInfo(app.packageName, 0)
        val tag = "v${installed.versionName}"
        val release = UpdateRelease(tag, "", "", file.length(), "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { repo.verifyApk(file, release) }
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val error = assertThrows(IllegalArgumentException::class.java) { repo.verifyApk(file, release.copy(sha256 = sha)) }
        assertTrue(error.message!!.contains("newer"))
    }

    @Test fun automaticChecksRespectDailyBackoffWithoutContactingGithub() = runBlocking {
        val prefs = testContext.getSharedPreferences("github_updates", Context.MODE_PRIVATE)
        prefs.edit().clear().putLong("attempt", System.currentTimeMillis()).commit()
        try {
            val repo = UpdateRepository(testContext)
            repo.check()
            assertEquals(UpdateTask.NONE, repo.state.value.task)
            assertEquals(0L, repo.state.value.lastChecked)
            assertNull(repo.state.value.error)
        } finally { prefs.edit().clear().commit() }
    }
}
