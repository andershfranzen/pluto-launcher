package dev.pluto.launcher.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
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
                                require(output.size() + read <= MAX_METADATA_BYTES) { "The release response was too large." }
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        require(bytes.size <= MAX_METADATA_BYTES) { "The release response was too large." }
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
            _state.update { it.copy(task = UpdateTask.NONE, error = userError(e, "Could not check GitHub. Check your connection and try again.")) }
        } finally { gate.unlock() }
    }

    internal fun parseRelease(json: String): UpdateRelease? {
        val release = JSONObject(json)
        require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "No stable release is available." }
        val tag = release.getString("tag_name")
        val version = ReleaseVersion.parse(tag) ?: error("The release has an unsupported version.")
        val current = ReleaseVersion.parse(installed.versionName.orEmpty()) ?: error("This build cannot compare release versions.")
        if (version <= current) return null
        val assets = release.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name") == "app-release.apk" }
            ?: error("This release has no APK yet. Try again later.")
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
                        require(count <= release.size) { "The downloaded APK does not match the release." }
                        output.write(buffer, 0, read)
                        _state.update { it.copy(downloadedBytes = count) }
                    }
                } }
                require(count == release.size) { "The download was incomplete. Try again." }
            } finally { connection.disconnect() }
            _state.update { it.copy(task = UpdateTask.VERIFYING) }
            verifyApk(downloading, release)
            val destination = apk(release)
            require(downloading.renameTo(destination)) { "Could not save the update. Check free storage and try again." }
            _state.update { it.copy(task = UpdateTask.NONE, readyToInstall = true) }
        } catch (e: CancellationException) {
            _state.update { it.copy(task = UpdateTask.NONE, error = null) }; throw e
        } catch (e: Exception) {
            _state.update { it.copy(task = UpdateTask.NONE, error = userError(e, "Could not download the update. Check your connection and free storage, then try again.")) }
        } finally { partial?.delete(); gate.unlock() }
    }

    /** Android still performs its own signature verification and asks the user to approve. */
    internal fun verifyApk(file: File, release: UpdateRelease) {
        require(file.length() == release.size) { "The downloaded APK does not match the release." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val read = input.read(buffer); if (read == -1) break; digest.update(buffer, 0, read) }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "The APK checksum does not match. Download it again." }
        val candidate = app.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("The download is not a valid Android APK.")
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
            _state.update { it.copy(error = userError(e, "Android could not open the installer. Try again.")) }
        } finally { _state.update { it.copy(task = UpdateTask.NONE) }; gate.unlock() }
    }

    suspend fun resumeInstallationIfAllowed() {
        if (!returningFromPermission) return
        returningFromPermission = false
        prefs.edit().remove("pending_install").apply()
        if (app.packageManager.canRequestPackageInstalls()) install()
        else _state.update { it.copy(error = "To install this update, allow Pluto to install apps when Android asks. Your download is ready to retry.") }
    }

    internal fun installIntent(file: File): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(FileProvider.getUriForFile(app, "${app.packageName}.updates", file), "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun apk(release: UpdateRelease) = File(directory, "pluto-${release.versionName}.apk")
    private fun userError(e: Exception, fallback: String) = if (e is IOException) fallback else e.message ?: fallback

    private fun open(url: String, metadata: Boolean = false): HttpURLConnection {
        var target = url
        repeat(6) {
            require(if (metadata) target == UpdateRelease.API_URL else UpdateRelease.allowsDownloadRedirect(target)) { "The download redirected outside GitHub." }
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 20_000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Pluto-Launcher/${installed.versionName}")
                if (metadata) { setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("X-GitHub-Api-Version", "2022-11-28") }
            }
            try {
                when (connection.responseCode) {
                    200 -> return connection
                    301, 302, 303, 307, 308 -> target = URL(URL(target), connection.getHeaderField("Location") ?: error("Missing download location.")).toString()
                    403, 429 -> error("GitHub is limiting requests. Try again later.")
                    else -> throw IOException("GitHub returned ${connection.responseCode}")
                }
            } catch (e: Exception) { connection.disconnect(); throw e }
            connection.disconnect()
        }
        error("The download redirected too many times.")
    }

    companion object {
        private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L
        private const val MAX_METADATA_BYTES = 256 * 1024
    }
}
