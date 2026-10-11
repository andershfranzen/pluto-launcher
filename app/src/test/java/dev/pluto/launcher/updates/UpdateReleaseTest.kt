package dev.pluto.launcher.updates

import org.junit.Assert.*
import org.junit.Test

class UpdateReleaseTest {
    @Test fun comparesVersionsNumericallyAndRejectsPreviewTags() {
        assertTrue(ReleaseVersion.parse("v0.1.10")!! > ReleaseVersion.parse("0.1.9")!!)
        assertTrue(ReleaseVersion.parse("1.0.0")!! > ReleaseVersion.parse("0.99.99")!!)
        assertEquals(0, ReleaseVersion.parse("v0.1.3")!!.compareTo(ReleaseVersion.parse("0.1.3")!!))
        listOf("v0.1.4-beta", "latest", "0.1", "v999999999999.1.0", "v0.1.4/../bad").forEach { assertNull(ReleaseVersion.parse(it)) }
    }

    private fun release(page: String = "${UpdateRelease.REPO_URL}/releases/tag/v0.1.4",
                        apk: String = "${UpdateRelease.REPO_URL}/releases/download/v0.1.4/app-release.apk",
                        size: Long = 5_000_000, digest: String = "sha256:" + "a".repeat(64)) =
        UpdateRelease.validated("v0.1.4", page, apk, size, digest)

    @Test fun acceptsOnlyTheRepositoryApkWithAUsableChecksumAndBoundedSize() {
        assertEquals("0.1.4", release().versionName)
        assertEquals("a".repeat(64), release(digest = "sha256:" + "A".repeat(64)).sha256)
        val invalid = listOf<() -> Unit>(
            { release(page = "https://example.com/releases/tag/v0.1.4") },
            { release(apk = "${UpdateRelease.REPO_URL}/releases/download/v0.1.4/other.apk") },
            { release(apk = "http://github.com/andershfranzen/pluto-launcher/releases/download/v0.1.4/app-release.apk") },
            { release(size = 0) }, { release(size = UpdateRelease.MAX_APK_BYTES + 1) },
            { release(digest = "") }, { release(digest = "sha256:short") },
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test fun downloadRedirectsStayOnHttpsGithubAssetHosts() {
        assertTrue(UpdateRelease.allowsDownloadRedirect("https://release-assets.githubusercontent.com/github-production-release-asset/123?token=x"))
        assertTrue(UpdateRelease.allowsDownloadRedirect("https://objects.githubusercontent.com/asset"))
        listOf("http://github.com/a", "https://github.com.evil.test/a", "https://github.com@evil.test/a",
            "https://evil.test@github.com/a", "https://github.com:444/a", "file:///tmp/update.apk").forEach {
            assertFalse(it, UpdateRelease.allowsDownloadRedirect(it))
        }
    }
    @Test fun installPolicyPreservesDataAndRejectsWrongSignersPackagesAndVersions() {
        val current = ApkIdentity("dev.pluto.launcher", 4, "0.1.3", setOf("release-key"))
        val candidate = current.copy(versionCode = 5, versionName = "0.1.4")
        UpdateApkPolicy.validate(current, candidate, release())
        listOf(candidate.copy(signers = setOf("new-key")), candidate.copy(signers = emptySet()),
            candidate.copy(packageName = "other.app"), candidate.copy(versionCode = 4), candidate.copy(versionCode = 3),
            candidate.copy(versionName = "0.1.5")).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { UpdateApkPolicy.validate(current, invalid, release()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateApkPolicy.validate(current.copy(signers = emptySet()), candidate.copy(signers = emptySet()), release())
        }
    }

}
