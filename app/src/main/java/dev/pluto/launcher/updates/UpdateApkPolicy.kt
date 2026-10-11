package dev.pluto.launcher.updates

/** Android extracts these identities; the install decision is independently testable. */
internal data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String?, val signers: Set<String>)

internal object UpdateApkPolicy {
    fun validate(current: ApkIdentity, candidate: ApkIdentity, release: UpdateRelease) {
        require(candidate.packageName == current.packageName) { "This APK is not Pluto." }
        require(candidate.versionCode > current.versionCode && candidate.versionName == release.versionName) { "This APK is not a newer Pluto release." }
        require(current.signers.isNotEmpty() && candidate.signers == current.signers) {
            "This update uses a different signing key and cannot preserve your layout. Keep your current installation; do not uninstall it."
        }
    }
}
