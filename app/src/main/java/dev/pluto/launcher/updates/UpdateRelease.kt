package dev.pluto.launcher.updates

import java.net.URI

/** Stable numeric release versions; previews never participate in automatic updates. */
data class ReleaseVersion(val parts: List<Int>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val result = (parts.getOrElse(i) { 0 }).compareTo(other.parts.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }
    companion object {
        fun parse(value: String): ReleaseVersion? {
            if (!value.matches(Regex("v?\\d+\\.\\d+\\.\\d+"))) return null
            val parts = value.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }
            return ReleaseVersion(parts)
        }
    }
}

data class UpdateRelease(val tag: String, val pageUrl: String, val apkUrl: String, val size: Long, val sha256: String) {
    val versionName: String get() = tag.removePrefix("v")
    companion object {
        const val REPO_URL = "https://github.com/andershfranzen/pluto-launcher"
        const val API_URL = "https://api.github.com/repos/andershfranzen/pluto-launcher/releases/latest"
        const val MAX_APK_BYTES = 50L * 1024 * 1024

        fun validated(tag: String, pageUrl: String, apkUrl: String, size: Long, digest: String): UpdateRelease {
            require(ReleaseVersion.parse(tag) != null) { "The release has an unsupported version." }
            require(pageUrl == "$REPO_URL/releases/tag/$tag") { "The release is not from Pluto's GitHub repository." }
            require(apkUrl == "$REPO_URL/releases/download/$tag/app-release.apk") { "The release has no supported Pluto APK." }
            require(size in 1..MAX_APK_BYTES) { "The release APK has an unexpected size." }
            require(digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "The release has no verified APK checksum yet. Try again later." }
            return UpdateRelease(tag, pageUrl, apkUrl, size, digest.removePrefix("sha256:").lowercase())
        }

        fun allowsDownloadRedirect(url: String): Boolean = runCatching {
            val uri = URI(url)
            uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
                uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        }.getOrDefault(false)
    }
}

enum class UpdateTask { NONE, CHECKING, DOWNLOADING, VERIFYING }
data class UpdateState(
    val release: UpdateRelease? = null,
    val task: UpdateTask = UpdateTask.NONE,
    val downloadedBytes: Long = 0,
    val readyToInstall: Boolean = false,
    val lastChecked: Long = 0,
    val error: String? = null,
) {
    val busy: Boolean get() = task != UpdateTask.NONE
}
