package dev.pluto.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.pluto.launcher.data.db.RomDao
import dev.pluto.launcher.data.db.RomGameEntity
import dev.pluto.launcher.data.db.RomLibraryEntity
import dev.pluto.launcher.domain.RomFiles
import dev.pluto.launcher.model.RomLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Cached document metadata only: ROMs and cover files are never copied or deleted. */
class RomRepository(context: Context, private val dao: RomDao) {
    private val resolver = context.applicationContext.contentResolver
    private val scanPermits = Semaphore(2)
    val libraries = flow {
        // Each collector owns its previous projection; sorting never runs on the UI thread.
        val projector = RomLibraryProjector()
        dao.observeLibraries().collect { emit(projector.project(it)) }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun save(library: RomLibrary): Long {
        val row = RomLibraryEntity(library.id, library.name.trim().take(40), library.treeUri, library.emulatorPackage, library.launchMode.name)
        require(row.name.isNotEmpty())
        require(row.treeUri.startsWith("content://"))
        return dao.saveLibrary(row)
    }

    suspend fun delete(id: Long) = dao.deleteLibrary(id)

    /** A complete scan replaces the cache atomically. Failed scans keep the last good list. */
    suspend fun scan(id: Long): Int = scanPermits.withPermit { scanDocuments(id) }

    /** Bound provider I/O across shelves; a cancelled waiter never begins scanning. */
    private suspend fun scanDocuments(id: Long): Int = withContext(Dispatchers.IO) {
        val library = dao.library(id) ?: return@withContext 0
        val tree = Uri.parse(library.treeUri)
        data class Document(val uri: Uri, val name: String, val path: String, val modified: Long)
        val pending = ArrayDeque<Pair<String, String>>()
        pending.add(DocumentsContract.getTreeDocumentId(tree) to "")
        val visited = HashSet<String>()
        val files = ArrayList<Document>()
        var documents = 0
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (parent, path) = pending.removeFirst()
            if (!visited.add(parent)) continue
            require(visited.size <= 512) { "This folder has too many subfolders. Choose a smaller folder." }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val cursor = resolver.query(children, columns, null, null, null) ?: error("The ROM folder is unavailable. Choose it again.")
            cursor.use {
                while (it.moveToNext()) {
                    coroutineContext.ensureActive()
                    require(++documents <= 20_000) { "This folder has too many files. Choose a smaller folder." }
                    val documentId = it.getString(0) ?: continue
                    val name = it.getString(1) ?: continue
                    val relative = if (path.isEmpty()) name else "$path/$name"
                    if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(documentId to relative)
                    } else {
                        files += Document(DocumentsContract.buildDocumentUriUsingTree(tree, documentId), name, relative, it.getLong(3))
                    }
                }
            }
        }
        val referenced = HashSet<String>()
        for (file in files.filter { it.name.substringAfterLast('.').lowercase() in setOf("cue", "m3u") }) {
            coroutineContext.ensureActive()
            val text = resolver.openInputStream(file.uri)?.use { stream ->
                val bytes = stream.buffered().takeBytes(1_048_577)
                require(bytes.size <= 1_048_576) { "A playlist is too large to read: ${file.name}" }
                bytes.toString(Charsets.UTF_8)
            } ?: error("Could not read ${file.name}")
            val directory = file.path.substringBeforeLast('/', "")
            RomFiles.referencedFiles(file.name, text).forEach { ref ->
                referenced += (if (directory.isEmpty()) ref else "$directory/$ref").replace('\\', '/').lowercase()
            }
        }
        val covers = files.filter { it.name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp") }
            .associateBy { it.path.substringBeforeLast('.').lowercase() }
        val games = files.filter { RomFiles.supported(it.name) && it.path.lowercase() !in referenced }.map { file ->
            RomGameEntity(id, file.uri.toString(), file.name, RomFiles.title(file.name), file.modified,
                covers[file.path.substringBeforeLast('.').lowercase()]?.uri?.toString())
        }
        coroutineContext.ensureActive()
        dao.replaceGames(id, library.treeUri, games)
        games.size
    }
}

private fun java.io.InputStream.takeBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
