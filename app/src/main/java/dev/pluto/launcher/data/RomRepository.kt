package dev.pluto.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.pluto.launcher.R
import dev.pluto.launcher.data.db.RomDao
import dev.pluto.launcher.data.db.RomGameEntity
import dev.pluto.launcher.data.db.RomLibraryEntity
import dev.pluto.launcher.domain.RomArtwork
import dev.pluto.launcher.domain.RomFiles
import dev.pluto.launcher.domain.RomMetadata
import dev.pluto.launcher.domain.RomScanReport
import dev.pluto.launcher.domain.RomScanSummary
import dev.pluto.launcher.domain.RomSystems
import dev.pluto.launcher.model.RomLibrary
import dev.pluto.launcher.ui.UiText
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
    suspend fun scan(id: Long): RomScanReport = scanPermits.withPermit { scanDocuments(id) }

    /** Bound provider I/O across shelves; a cancelled waiter never begins scanning. */
    private suspend fun scanDocuments(id: Long): RomScanReport = withContext(Dispatchers.IO) {
        val library = dao.library(id) ?: return@withContext EmptyReport
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
            require(visited.size <= 512) { UiText.get(R.string.rom_scan_too_many_folders) }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val cursor = resolver.query(children, columns, null, null, null) ?: error(UiText.get(R.string.rom_scan_folder_unavailable))
            cursor.use {
                while (it.moveToNext()) {
                    coroutineContext.ensureActive()
                    require(++documents <= 20_000) { UiText.get(R.string.rom_scan_too_many_files) }
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

        // Lookups below are keyed by the same normalized form, so case and "./" never matter.
        val uriByPath = HashMap<String, String>(files.size)
        files.forEach { uriByPath[RomMetadata.normalize(it.path)] = it.uri.toString() }

        // Sheets describe the tracks and discs an emulator should open; those files stay hidden.
        val sheets = files.filter { it.name.substringAfterLast('.').lowercase() in RomFiles.sheets }
        val referenced = HashSet<String>()
        for (file in sheets) {
            coroutineContext.ensureActive()
            val text = resolver.openInputStream(file.uri)?.use { stream ->
                val bytes = stream.buffered().takeBytes(1_048_577)
                require(bytes.size <= 1_048_576) { UiText.get(R.string.rom_scan_playlist_too_large, file.name) }
                bytes.toString(Charsets.UTF_8)
            } ?: error(UiText.get(R.string.rom_scan_playlist_unreadable, file.name))
            val directory = file.path.substringBeforeLast('/', "")
            RomFiles.referencedFiles(file.name, text).forEach { ref ->
                referenced += RomMetadata.normalize(if (directory.isEmpty()) ref else "$directory/$ref")
            }
        }

        // EmulationStation gamelists kept in the ROM tree (RetroPie, Batocera) supply names
        // and art paths that the folder layout alone cannot express.
        var gamelistEntries = 0
        val metadata = HashMap<String, Pair<RomMetadata.Entry, String>>()
        for (list in files.filter { it.name.equals(RomMetadata.GAMELIST, ignoreCase = true) }) {
            coroutineContext.ensureActive()
            // Optional extras: an unreadable list is skipped like an oversized one, never fatal to the scan.
            val text = runCatching {
                resolver.openInputStream(list.uri)?.use { stream ->
                    val bytes = stream.buffered().takeBytes(GAMELIST_LIMIT + 1)
                    if (bytes.size > GAMELIST_LIMIT) null else bytes.toString(Charsets.UTF_8)
                }
            }.getOrNull() ?: continue
            val baseDir = list.path.substringBeforeLast('/', "")
            RomMetadata.gamelist(text).forEach { entry ->
                metadata.putIfAbsent(RomMetadata.normalize(RomMetadata.resolve(baseDir, entry.path)), entry to baseDir)
                gamelistEntries++
            }
        }

        val artwork = RomArtwork.index(files.map { RomMetadata.normalize(it.path) })
        // Battery saves and emulator states sit beside the game under the same base name.
        val savesByBase = HashMap<String, Long>()
        files.filter { RomFiles.isSave(it.name) }.forEach { file ->
            savesByBase.merge(file.name.substringBeforeLast('.').lowercase(), file.modified, ::maxOf)
        }
        val gameFiles = files.filter { RomFiles.supported(it.name) && RomMetadata.normalize(it.path) !in referenced }
        val games = gameFiles.map { file ->
            val key = RomMetadata.normalize(file.path)
            val entry = metadata[key]
            val cover = entry?.first?.image
                ?.let { uriByPath[RomMetadata.normalize(RomMetadata.resolve(entry.second, it))] }
                ?: artwork.art(key, file.name)?.let { uriByPath[it] }
            RomGameEntity(id, file.uri.toString(), file.name, entry?.first?.name ?: RomFiles.title(file.name),
                file.modified, cover)
        }
        // A shelf that spans systems (one /ROMs tree) can be browsed a system at a time.
        val systems = gameFiles.map { RomSystems.ofPath(it.path) }
        val skipped = files.filter { file ->
            val key = RomMetadata.normalize(file.path)
            !RomFiles.supported(file.name) && !RomArtwork.isImage(file.name) && !RomFiles.isSave(file.name) &&
                file.name.substringAfterLast('.').lowercase() !in RomFiles.sheets &&
                !file.name.equals(RomMetadata.GAMELIST, ignoreCase = true) && key !in referenced
        }
        coroutineContext.ensureActive()
        dao.replaceGames(id, library.treeUri, games)
        RomScanReport(
            documents = documents,
            games = games.size,
            gamesWithArt = games.count { it.coverUri != null },
            skipped = skipped.size,
            skippedExtensions = RomScanSummary.extensions(skipped.map { it.name }),
            sheets = sheets.size,
            missingTracks = referenced.count { it !in uriByPath },
            gamelistEntries = gamelistEntries,
            at = System.currentTimeMillis(),
            saves = gameFiles.mapNotNull { file ->
                savesByBase[file.name.substringBeforeLast('.').lowercase()]?.let { file.uri.toString() to it }
            }.toMap(),
            systems = if (systems.distinct().size > 1) {
                gameFiles.mapIndexed { index, file -> file.uri.toString() to systems[index] }.toMap()
            } else {
                emptyMap()
            },
        )
    }

    private companion object {
        /** gamelist.xml files are small; anything larger is not a front-end list. */
        const val GAMELIST_LIMIT = 4 * 1024 * 1024
        val EmptyReport = RomScanReport(0, 0, 0, 0, emptyList(), 0, 0, 0, System.currentTimeMillis())
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
