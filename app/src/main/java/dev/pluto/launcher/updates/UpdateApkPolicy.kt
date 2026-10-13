package dev.pluto.launcher.updates

import dev.pluto.launcher.R

/** Android extracts these identities; the install decision is independently testable. */
internal data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String?, val signers: Set<String>)

internal object UpdateApkPolicy {
    fun validate(current: ApkIdentity, candidate: ApkIdentity, release: UpdateRelease) {
        requireUpdate(candidate.packageName == current.packageName, R.string.settings_update_error_not_pluto)
        requireUpdate(
            candidate.versionCode > current.versionCode && candidate.versionName == release.versionName,
            R.string.settings_update_error_not_newer,
        )
        requireUpdate(
            current.signers.isNotEmpty() && candidate.signers == current.signers,
            R.string.settings_update_error_signing_key,
        )
    }
}
