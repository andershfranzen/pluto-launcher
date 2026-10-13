package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.BuiltInCategory
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization

/**
 * A copy of everything the user organised, in a device-independent text form. Restoring it
 * brings the home grid, dock, folders, categories and hidden apps back after a reinstall or on
 * another device; nothing in it names a file path, so it travels.
 *
 * Two things are deliberately left out. Recents are this device's own history. Widgets are
 * identified by host-assigned ids that mean nothing elsewhere, so an import leaves the widgets
 * already on the device where they are.
 */
data class LayoutBackup(
    /** Home grid order; folder references name an id in [folders]. */
    val homeItems: List<String>,
    /** [Organization.DOCK_SLOTS] entries of `AppKey.encode()`; null is an empty slot. */
    val dock: List<String?>,
    val folders: List<LayoutFolder>,
    val categories: List<LayoutCategory>,
    val hidden: List<String>,
)

data class LayoutFolder(val id: Long, val name: String, val apps: List<String>)

data class LayoutCategory(
    val id: Long,
    val name: String,
    val builtIn: String?,
    /** Position on the category strip; kept so a restore does not reshuffle the user's tabs. */
    val position: Int,
    val apps: List<String>,
)

/** What reading a chosen file produced. */
sealed interface LayoutBackupRead {
    /** A readable backup, already repaired so it can be applied as it stands. */
    data class Ok(val backup: LayoutBackup) : LayoutBackupRead
    /** Written by a newer Pluto: applying it here could drop layout, so it is refused. */
    data class TooNew(val version: Int) : LayoutBackupRead
    /** Not a Pluto layout backup, or too damaged to read. */
    data object Unreadable : LayoutBackupRead
}

/**
 * The backup file's whole format: one `key=value` record per line, flat keys such as
 * `folder.7.app.0`. A text file nobody needs a library to read is the point — it stays
 * readable for years, and a damaged line costs one entry rather than the file.
 *
 * Reading repairs as it goes: unknown keys are ignored (so a later 1.x file still loads),
 * everything undecodable is dropped, and the invariants the database relies on are restored
 * (one folder per app, one row per built-in category). Nothing in the file is trusted.
 */
object LayoutBackupCodec {
    const val VERSION = 1
    private const val HEADER = "pluto-layout"
    /** Last line: how many records precede it, so a cut-off copy is refused rather than restored. */
    private const val END = "end"
    /** Ids beyond this would exhaust SQLite's AUTOINCREMENT after a restore. */
    private const val MAX_ID = Int.MAX_VALUE.toLong()

    fun encode(backup: LayoutBackup): String {
        val body = records(backup)
        val count = body.lineSequence().count { it.isNotEmpty() }
        return "# Pluto layout backup. Keep this file: Settings, General, Back up reads it back.\n" +
            "$HEADER=$VERSION\n$body$END=$count\n"
    }

    private fun records(backup: LayoutBackup): String = buildString {
        backup.homeItems.filterNot { it.startsWith(HomeItem.WIDGET_PREFIX) }
            .forEachIndexed { index, id -> append("home.$index=").append(escape(id)).append('\n') }
        backup.dock.forEachIndexed { slot, key ->
            if (key != null) append("dock.$slot=").append(escape(key)).append('\n')
        }
        backup.folders.forEach { folder ->
            append("folder.").append(folder.id).append(".name=").append(escape(folder.name)).append('\n')
            folder.apps.forEachIndexed { index, key ->
                append("folder.").append(folder.id).append(".app.").append(index).append('=').append(escape(key)).append('\n')
            }
        }
        backup.categories.forEach { category ->
            append("category.").append(category.id).append(".name=").append(escape(category.name)).append('\n')
            append("category.").append(category.id).append(".position=").append(category.position).append('\n')
            category.builtIn?.let { append("category.").append(category.id).append(".builtIn=").append(it).append('\n') }
            category.apps.sorted().forEachIndexed { index, key ->
                append("category.").append(category.id).append(".app.").append(index).append('=').append(escape(key)).append('\n')
            }
        }
        backup.hidden.sorted().forEachIndexed { index, key -> append("hidden.$index=").append(escape(key)).append('\n') }
    }

    fun decode(text: String): LayoutBackupRead {
        val home = sortedMapOf<Int, String>()
        val dock = sortedMapOf<Int, String>()
        val hidden = sortedMapOf<Int, String>()
        val folderNames = HashMap<Long, String>()
        val folderApps = HashMap<Long, java.util.SortedMap<Int, String>>()
        val categoryNames = HashMap<Long, String>()
        val categoryBuiltIns = HashMap<Long, String>()
        val categoryPositions = HashMap<Long, Int>()
        val categoryApps = HashMap<Long, java.util.SortedMap<Int, String>>()
        var version: Int? = null
        var records = 0
        var declaredRecords: Int? = null

        for (raw in text.lineSequence()) {
            // Only the line ending is stripped: a name may legitimately end in a space.
            val line = raw.removeSuffix("\r")
            if (line.isBlank() || line.startsWith("#")) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator)
            val value = unescape(line.substring(separator + 1))
            if (key == HEADER) {
                version = value.trim().toIntOrNull() ?: return LayoutBackupRead.Unreadable
                if (version > VERSION) return LayoutBackupRead.TooNew(version)
                if (version < 1) return LayoutBackupRead.Unreadable
                continue
            }
            if (key == END) {
                declaredRecords = value.trim().toIntOrNull() ?: return LayoutBackupRead.Unreadable
                continue
            }
            // Anything after the end line means two files were joined or the file was edited.
            if (declaredRecords != null) return LayoutBackupRead.Unreadable
            records++
            val parts = key.split('.')
            when (parts.firstOrNull()) {
                "home" -> parts.getOrNull(1)?.toIntOrNull()?.let { home[it] = value }
                "dock" -> parts.getOrNull(1)?.toIntOrNull()?.let { dock[it] = value }
                "hidden" -> parts.getOrNull(1)?.toIntOrNull()?.let { hidden[it] = value }
                "folder" -> {
                    val id = parts.getOrNull(1)?.toLongOrNull() ?: continue
                    if (id !in 1..MAX_ID) continue
                    when {
                        parts.size == 3 && parts[2] == "name" -> folderNames[id] = value
                        parts.size == 4 && parts[2] == "app" -> parts[3].toIntOrNull()?.let { index ->
                            folderApps.getOrPut(id) { sortedMapOf() }[index] = value
                        }
                    }
                }
                "category" -> {
                    val id = parts.getOrNull(1)?.toLongOrNull() ?: continue
                    if (id !in 1..MAX_ID) continue
                    when {
                        parts.size == 3 && parts[2] == "name" -> categoryNames[id] = value
                        parts.size == 3 && parts[2] == "builtIn" -> categoryBuiltIns[id] = value
                        parts.size == 3 && parts[2] == "position" -> value.toIntOrNull()?.let { categoryPositions[id] = it }
                        parts.size == 4 && parts[2] == "app" -> parts[3].toIntOrNull()?.let { index ->
                            categoryApps.getOrPut(id) { sortedMapOf() }[index] = value
                        }
                    }
                }
            }
        }
        if (version == null) return LayoutBackupRead.Unreadable
        // A truncated or partly synced copy would otherwise replace the whole layout with a fragment.
        if (declaredRecords != records) return LayoutBackupRead.Unreadable

        // Folders: a nameless one loses its grid entry anyway, so drop it rather than show a blank.
        val folderIds = LinkedHashSet<Long>()
        val folders = folderNames.entries.sortedBy { it.key }
            .filter { it.value.isNotBlank() && folderIds.add(it.key) }
            .map { (id, name) -> LayoutFolder(id, name, folderAppKeys(folderApps[id])) }

        // An app lives in at most one folder; the lowest folder id wins the rest.
        val placed = HashSet<String>()
        val uniqueFolders = folders.map { folder ->
            folder.copy(apps = folder.apps.filter { placed.add(it) })
        }

        val homeItems = home.values
            .mapNotNull(HomeItem::decode)
            .filter { item ->
                when (item) {
                    is HomeItem.App -> item.key.encode() !in placed
                    is HomeItem.FolderRef -> item.folderId in folderIds
                    is HomeItem.Widget -> false
                }
            }
            .distinct()
            .map { it.id }

        val dockKeys = arrayOfNulls<String>(Organization.DOCK_SLOTS)
        val seenSlots = HashSet<String>()
        for ((slot, value) in dock) {
            if (slot !in 0 until Organization.DOCK_SLOTS) continue
            val key = appKey(value) ?: continue
            if (seenSlots.add(key)) dockKeys[slot] = key
        }

        // The table holds one row per built-in, so a file naming one twice keeps the first and
        // demotes the rest to ordinary categories rather than losing their apps.
        val seenBuiltIns = HashSet<String>()
        val categories = categoryNames.entries
            // Position is what the user sees; id breaks a tie so the order is always the same.
            .sortedWith(compareBy({ categoryPositions[it.key] ?: it.key.toInt() }, { it.key }))
            .filter { it.value.isNotBlank() }
            .map { (id, name) ->
                val builtIn = categoryBuiltIns[id]
                    ?.takeIf { candidate -> BuiltInCategory.entries.any { it.name == candidate } }
                    ?.takeIf { seenBuiltIns.add(it) }
                LayoutCategory(id, name, builtIn, categoryPositions[id] ?: id.toInt(), categoryAppKeys(categoryApps[id]))
            }

        return LayoutBackupRead.Ok(
            LayoutBackup(
                homeItems = homeItems,
                dock = dockKeys.toList(),
                folders = uniqueFolders,
                categories = categories,
                hidden = hidden.values.mapNotNull(::appKey).distinct().sorted(),
            ),
        )
    }

    private fun folderAppKeys(rows: java.util.SortedMap<Int, String>?): List<String> =
        rows.orEmpty().values.mapNotNull(::appKey).distinct()

    private fun categoryAppKeys(rows: java.util.SortedMap<Int, String>?): List<String> =
        rows.orEmpty().values.mapNotNull(::appKey).distinct().sorted()

    /** A decodable app key, in canonical form; null when the text is not one. */
    private fun appKey(value: String): String? = AppKey.decode(value)?.encode()

    private fun escape(value: String): String = buildString(value.length) {
        for (char in value) when (char) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }

    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    '\\' -> { out.append('\\'); index += 2 }
                    'n' -> { out.append('\n'); index += 2 }
                    'r' -> { out.append('\r'); index += 2 }
                    't' -> { out.append('\t'); index += 2 }
                    else -> { out.append(char); index += 1 }
                }
            } else {
                out.append(char)
                index += 1
            }
        }
        return out.toString()
    }
}
