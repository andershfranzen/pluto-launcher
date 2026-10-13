package dev.pluto.launcher.updates

import androidx.annotation.StringRes
import dev.pluto.launcher.R
import dev.pluto.launcher.ui.UiText
import java.net.URI

/**
 * An update refused for a reason the user sees in Settings. The message is read in the current
 * language only when it is shown, so validation itself stays free of Android resources.
 */
class UpdateRejected(@StringRes val reason: Int) : IllegalArgumentException() {
    override val message: String get() = UiText.get(reason)
}

/** Like [require], but the failure carries a translated, user-facing [reason]. */
internal fun requireUpdate(condition: Boolean, @StringRes reason: Int) {
    if (!condition) throw UpdateRejected(reason)
}

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
            requireUpdate(ReleaseVersion.parse(tag) != null, R.string.settings_update_error_unsupported_version)
            requireUpdate(pageUrl == "$REPO_URL/releases/tag/$tag", R.string.settings_update_error_not_pluto_repo)
            requireUpdate(apkUrl == "$REPO_URL/releases/download/$tag/app-release.apk", R.string.settings_update_error_no_supported_apk)
            requireUpdate(size in 1..MAX_APK_BYTES, R.string.settings_update_error_unexpected_size)
            requireUpdate(digest.matches(Regex("sha256:[a-fA-F0-9]{64}")), R.string.settings_update_error_no_checksum)
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
