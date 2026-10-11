package dev.pluto.launcher.data

import dev.pluto.launcher.data.db.RomGameEntity
import dev.pluto.launcher.data.db.RomLibraryEntity
import dev.pluto.launcher.data.db.RomLibraryRows
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.model.RomLaunchMode
import dev.pluto.launcher.model.RomLibrary

/** Collector-confined projection retaining unchanged shelves, sorted lists and game objects. */
internal class RomLibraryProjector {
    private data class Shelf(
        val metadata: RomLibraryEntity,
        val rows: Map<String, RomGameEntity>,
        val games: Map<String, RomGame>,
        val library: RomLibrary,
    )

    private var shelves = emptyMap<Long, Shelf>()
    private var previous = emptyList<RomLibrary>()
    private val gameOrder = compareBy<RomGame, String>(String.CASE_INSENSITIVE_ORDER) { it.title }

    fun project(rows: List<RomLibraryRows>): List<RomLibrary> {
        val next = HashMap<Long, Shelf>(rows.size)
        val result = rows.map { row ->
            val old = shelves[row.library.id]
            // Room relation rows have no order guarantee; compare metadata by URI.
            val unchangedGames = old != null && row.games.size == old.rows.size &&
                row.games.all { old.rows[it.uri] == it }
            val shelf = if (unchangedGames && old!!.metadata == row.library) {
                old
            } else {
                val entities = if (unchangedGames) old!!.rows else row.games.associateBy { it.uri }
                val games = if (unchangedGames) old!!.games else entities.mapValues { (uri, game) ->
                    if (old?.rows?.get(uri) == game) old.games.getValue(uri)
                    else RomGame(game.uri, game.fileName, game.title, game.modified, game.coverUri)
                }
                val sorted = if (unchangedGames) old!!.library.games
                    else games.values.sortedWith(gameOrder)
                val lib = row.library
                Shelf(lib, entities, games, RomLibrary(lib.id, lib.name, lib.treeUri, lib.emulatorPackage,
                    runCatching { RomLaunchMode.valueOf(lib.launchMode) }.getOrDefault(RomLaunchMode.AUTOMATIC), sorted))
            }
            next[row.library.id] = shelf
            shelf.library
        }
        shelves = next
        if (result.size == previous.size && result.indices.all { result[it] === previous[it] }) return previous
        previous = result
        return result
    }
}
