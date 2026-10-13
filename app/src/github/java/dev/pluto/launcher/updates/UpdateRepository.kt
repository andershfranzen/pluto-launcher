package dev.pluto.launcher.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import dev.pluto.launcher.R
import dev.pluto.launcher.ui.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Explicit downloads only. APKs remain private until Android's installer receives a read grant. */
class UpdateRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("github_updates", Context.MODE_PRIVATE)
    private val installed = app.packageManager.getPackageInfo(app.packageName, 0)
    private val directory = File(app.filesDir, "updates")
    private val gate = Mutex()
    private var returningFromPermission = prefs.getBoolean("pending_install", false)
    private val _state = MutableStateFlow(UpdateState(lastChecked = prefs.getLong("checked", 0)))
    val state = _state.asStateFlow()

    suspend fun restore() = withContext(Dispatchers.IO) {
        if (!gate.tryLock()) return@withContext
        try {
            val release = prefs.getString("release", null)?.let { runCatching { parseRelease(it) }.getOrNull() }
            _state.update { it.copy(release = release, readyToInstall = release != null && apk(release).isFile) }
            directory.listFiles()?.filter { it.name.endsWith(".part") || (release == null || it != apk(release)) }?.forEach { it.delete() }
        } finally { gate.unlock() }
    }

    /** Failed automatic requests also back off, so an offline Home return never hammers the network. */
    suspend fun check(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!gate.tryLock()) return@withContext
        try {
            val now = System.currentTimeMillis()
            val attempt = prefs.getLong("attempt", 0)
            if (!force && now >= attempt && now - attempt < CHECK_INTERVAL) return@withContext
            prefs.edit().putLong("attempt", now).apply()
            _state.update { it.copy(task = UpdateTask.CHECKING, error = null) }
            val json = open(UpdateRelease.API_URL, metadata = true).let { connection ->
                try {
                    connection.inputStream.use { input ->
                        val bytes = java.io.ByteArrayOutputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                requireUpdate(output.size() + read <= MAX_METADATA_BYTES, R.string.settings_update_error_response_too_large)
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        requireUpdate(bytes.size <= MAX_METADATA_BYTES, R.string.settings_update_error_response_too_large)
                        String(bytes, Charsets.UTF_8)
                    }
                } finally { connection.disconnect() }
            }
            currentCoroutineContext().ensureActive()
            val release = parseRelease(json)
            prefs.edit().putString("release", json).putLong("checked", now).apply()
            _state.update { it.copy(release = release, task = UpdateTask.NONE, lastChecked = now,
                readyToInstall = release != null && apk(release).isFile, downloadedBytes = 0) }
        } catch (e: CancellationException) {
            _state.update { it.copy(task = UpdateTask.NONE) }; throw e
        } catch (e: Exception) {
            _state.update { it.copy(task = UpdateTask.NONE, error = userError(e, R.string.settings_update_error_check)) }
        } finally { gate.unlock() }
    }

    internal fun parseRelease(json: String): UpdateRelease? {
        val release = JSONObject(json)
        requireUpdate(!release.optBoolean("draft") && !release.optBoolean("prerelease"), R.string.settings_update_error_no_stable)
        val tag = release.getString("tag_name")
        val version = ReleaseVersion.parse(tag) ?: throw UpdateRejected(R.string.settings_update_error_unsupported_version)
        val current = ReleaseVersion.parse(installed.versionName.orEmpty()) ?: throw UpdateRejected(R.string.settings_update_error_cannot_compare)
        if (version <= current) return null
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name") == "app-release.apk" }
            ?: throw UpdateRejected(R.string.settings_update_error_no_apk_yet)
        return UpdateRelease.validated(tag, release.getString("html_url"), asset.getString("browser_download_url"),
            asset.getLong("size"), asset.optString("digest"))
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (!gate.tryLock()) return@withContext
        var partial: File? = null
        try {
            val release = _state.value.release ?: return@withContext
            directory.mkdirs()
            val downloading = File(directory, "download.part")
            partial = downloading
            _state.update { it.copy(task = UpdateTask.DOWNLOADING, downloadedBytes = 0, readyToInstall = false, error = null) }
            val connection = open(release.apkUrl)
            try {
                var count = 0L
                connection.inputStream.use { input -> downloading.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        count += read
                        requireUpdate(count <= release.size, R.string.settings_update_error_apk_mismatch)
                        output.write(buffer, 0, read)
                        _state.update { it.copy(downloadedBytes = count) }
                    }
                } }
                requireUpdate(count == release.size, R.string.settings_update_error_incomplete)
            } finally { connection.disconnect() }
            _state.update { it.copy(task = UpdateTask.VERIFYING) }
            verifyApk(downloading, release)
            val destination = apk(release)
            requireUpdate(downloading.renameTo(destination), R.string.settings_update_error_save)
            _state.update { it.copy(task = UpdateTask.NONE, readyToInstall = true) }
        } catch (e: CancellationException) {
            _state.update { it.copy(task = UpdateTask.NONE, error = null) }; throw e
        } catch (e: Exception) {
            _state.update { it.copy(task = UpdateTask.NONE, error = userError(e, R.string.settings_update_error_download)) }
        } finally { partial?.delete(); gate.unlock() }
    }

    /** Android still performs its own signature verification and asks the user to approve. */
    internal fun verifyApk(file: File, release: UpdateRelease) {
        requireUpdate(file.length() == release.size, R.string.settings_update_error_apk_mismatch)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val read = input.read(buffer); if (read == -1) break; digest.update(buffer, 0, read) }
        }
        requireUpdate(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256, R.string.settings_update_error_checksum)
        val candidate = app.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateRejected(R.string.settings_update_error_invalid_apk)
        val current = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        fun identity(info: android.content.pm.PackageInfo) = ApkIdentity(info.packageName, info.longVersionCode, info.versionName,
            info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty())
        UpdateApkPolicy.validate(identity(current), identity(candidate), release)
    }

    suspend fun install() {
        if (!gate.tryLock()) return
        try {
            val release = _state.value.release ?: return
            if (!_state.value.readyToInstall) return
            _state.update { it.copy(task = UpdateTask.VERIFYING, error = null) }
            withContext(Dispatchers.IO) { verifyApk(apk(release), release) }
            if (!app.packageManager.canRequestPackageInstalls()) {
                returningFromPermission = true
                prefs.edit().putBoolean("pending_install", true).apply()
                app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else app.startActivity(installIntent(apk(release)))
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            returningFromPermission = false
            prefs.edit().remove("pending_install").apply()
            _state.update { it.copy(error = userError(e, R.string.settings_update_error_installer)) }
        } finally { _state.update { it.copy(task = UpdateTask.NONE) }; gate.unlock() }
    }

    suspend fun resumeInstallationIfAllowed() {
        if (!returningFromPermission) return
        returningFromPermission = false
        prefs.edit().remove("pending_install").apply()
        if (app.packageManager.canRequestPackageInstalls()) install()
        else _state.update { it.copy(error = UiText.get(R.string.settings_update_error_permission)) }
    }

    internal fun installIntent(file: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(app, "${app.packageName}.updates", file), "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun apk(release: UpdateRelease) = File(directory, "pluto-${release.versionName}.apk")
    /** Network failures get [fallback]; anything else shows its own (usually [UpdateRejected]) message. */
    private fun userError(e: Exception, @StringRes fallback: Int): String =
        (if (e is IOException) null else e.message) ?: UiText.get(fallback)

    private fun open(url: String, metadata: Boolean = false): HttpURLConnection {
        var target = url
        repeat(6) {
            requireUpdate(if (metadata) target == UpdateRelease.API_URL else UpdateRelease.allowsDownloadRedirect(target), R.string.settings_update_error_redirect_outside)
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 20_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Pluto-Launcher/${installed.versionName}")
                if (metadata) { setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("X-GitHub-Api-Version", "2022-11-28") }
            }
            try {
                when (connection.responseCode) {
                    200 -> return connection
                    301, 302, 303, 307, 308 -> target = URL(URL(target), connection.getHeaderField("Location") ?: throw UpdateRejected(R.string.settings_update_error_missing_location)).toString()
                    403, 429 -> throw UpdateRejected(R.string.settings_update_error_rate_limited)
                    else -> throw IOException("GitHub returned ${connection.responseCode}")
                }
            } catch (e: Exception) { connection.disconnect(); throw e }
            connection.disconnect()
        }
        throw UpdateRejected(R.string.settings_update_error_too_many_redirects)
    }

    companion object {
        private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L
        private const val MAX_METADATA_BYTES = 256 * 1024
    }
}
