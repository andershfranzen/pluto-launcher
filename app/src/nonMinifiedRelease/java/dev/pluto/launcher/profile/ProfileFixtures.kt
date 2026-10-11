package dev.pluto.launcher.profile

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.pluto.launcher.ui.console.ConsoleArtCache
import dev.pluto.launcher.ui.console.ConsoleEntry
import dev.pluto.launcher.model.RomGame
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import dev.pluto.launcher.LauncherApplication
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.RotationPreference
import dev.pluto.launcher.model.RomLaunchMode
import dev.pluto.launcher.model.AppKey
import java.io.File
import kotlinx.coroutines.runBlocking

/** Deterministic data for profile generation, excluded from every shipping APK. */
class ProfileFixtures : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        if (uri.lastPathSegment == "children") queryChildDocuments(DocumentsContract.getDocumentId(uri), projection, sortOrder)
        else queryDocument(DocumentsContract.getDocumentId(uri), projection)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        openDocument(DocumentsContract.getDocumentId(uri), mode, null)
    override fun getType(uri: Uri) = if (uri.toString().endsWith(".png")) "image/png" else "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only fixture")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only fixture")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only fixture")


    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "assertCover") return coverStatus(requireNotNull(arg))
        if (method !in setOf("seed", "phone", "console")) return super.call(method, arg, extras)
        val app = context!!.applicationContext as LauncherApplication
        runBlocking {
            app.container.settings.update { it.copy(onboardingComplete = true, updateCheckChoiceMade = true,
                automaticUpdateChecks = false,
                rotation = RotationPreference.PORTRAIT,
                handheldAppearance = if (method == "console") HandheldAppearance.EVERYWHERE else HandheldAppearance.NEVER) }
            if (method == "seed") {
                val serial = context!!.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
                val keys = List(12) { index ->
                    AppKey(ComponentName(context!!, "${context!!.packageName}.profile.App${index.toString().padStart(2, '0')}").flattenToString(), serial)
                }
                // Skip onboarding while retaining a representative, populated first-run Home.
                app.container.organization.seedDefaultLayout(dock = keys.drop(8), home = keys.take(8))
                val authority = "${context!!.packageName}.profile-fixtures"
                for (index in 1..2) {
                    val tree = DocumentsContract.buildTreeDocumentUri(authority, "shelf$index").toString()
                    // Profile installs are disposable; stable IDs make repeated rule iterations idempotent.
                    val dao = app.container.database.romDao()
                    if (dao.library(index.toLong()) == null) dao.insertLibrary(
                        dev.pluto.launcher.data.db.RomLibraryEntity(index.toLong(), "Profile shelf $index", tree,
                            context!!.packageName, RomLaunchMode.AUTOMATIC.name))
                    app.container.roms.scan(index.toLong())
                }
                val manager = context!!.getSystemService(ShortcutManager::class.java)
                check(manager.addDynamicShortcuts(listOf(ShortcutInfo.Builder(context!!, "profile-action")
                    .setShortLabel("Profile shortcut action")
                    .setActivity(ComponentName(context!!, MainActivity::class.java))
                    .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)).build())))
                createArtwork()
            }
        }
        return Bundle().apply { putString("status", "ready") }
    }

    private fun createArtwork(): File = File(context!!.cacheDir, "profile-cover.png").also { file ->
        if (!file.exists()) {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(56, 92, 148))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun coverStatus(argument: String): Bundle {
        val libraryId = argument.substringBefore(':').toLong()
        val dp = argument.substringAfter(':').toInt()
        require(dp in setOf(128, 112))
        val density = context!!.resources.displayMetrics.density
        val authority = "${context!!.packageName}.profile-fixtures"
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "shelf$libraryId")
        val size = (dp * density + 0.5f).toInt()
        var hits = 0
        val ready = run {
            (0..47).any { index ->
                val title = "Profile game ${index.toString().padStart(2, '0')}"
                val game = RomGame(DocumentsContract.buildDocumentUriUsingTree(tree, "shelf$libraryId/$title.iso").toString(),
                    "$title.iso", title, 1L,
                    DocumentsContract.buildDocumentUriUsingTree(tree, "shelf$libraryId/$title.png").toString())
                val art = ConsoleArtCache.peek(ConsoleEntry.Rom(libraryId, game), size, 0)
                if (art != null) hits++
                art != null && art.icon.asAndroidBitmap().let { bitmap ->
                    bitmap.width == size && bitmap.height == size && bitmap.getPixel(0, 0) == Color.rgb(56, 92, 148)
                }
            }
        }
        return Bundle().apply { putString("status", if (ready) "ready" else "pending"); putInt("size", size); putInt("cacheHits", hits) }
    }

    private fun documents(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE))

    private fun MatrixCursor.addDocument(id: String) {
        val directory = !id.contains('/')
        val values = mapOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID to id,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME to if (directory) id else id.substringAfter('/'),
            DocumentsContract.Document.COLUMN_MIME_TYPE to if (directory) DocumentsContract.Document.MIME_TYPE_DIR
                else if (id.endsWith(".png")) "image/png" else "application/octet-stream",
            DocumentsContract.Document.COLUMN_LAST_MODIFIED to 1L,
            DocumentsContract.Document.COLUMN_FLAGS to 0,
            DocumentsContract.Document.COLUMN_SIZE to 0L,
        )
        addRow(columnNames.map { values[it] })
    }

    fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        documents(projection).apply { addDocument(documentId) }

    fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        documents(projection).apply {
            if (parentDocumentId in setOf("shelf1", "shelf2")) repeat(48) { index ->
                val title = "Profile game ${index.toString().padStart(2, '0')}"
                addDocument("$parentDocumentId/$title.iso")
                addDocument("$parentDocumentId/$title.png")
            }
        }

    fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        require(mode == "r")
        val file = if (documentId.endsWith(".png")) createArtwork() else File(context!!.cacheDir, "profile-rom.iso")
            .apply { if (!exists()) writeBytes(byteArrayOf(0)) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
