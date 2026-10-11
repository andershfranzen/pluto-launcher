package dev.pluto.launcher.data

import dev.pluto.launcher.data.db.RomGameEntity
import dev.pluto.launcher.data.db.RomLibraryEntity
import dev.pluto.launcher.data.db.RomLibraryRows
import dev.pluto.launcher.model.RomLaunchMode
import org.junit.Assert.*
import org.junit.Test

class RomLibraryProjectorTest {
    private val metadata = RomLibraryEntity(1, "PS1", "content://tree/one", null, "AUTOMATIC")
    private fun game(uri: String, title: String) = RomGameEntity(1, uri, "$title.cue", title, 1, null)
    private val alpha = game("content://a", "Alpha")
    private val zulu = game("content://z", "zulu")

    @Test fun unchangedReorderedRowsRetainTheWholeProjection() {
        val projector = RomLibraryProjector()
        val first = projector.project(listOf(RomLibraryRows(metadata, listOf(zulu, alpha))))
        val next = projector.project(listOf(RomLibraryRows(metadata.copy(), listOf(alpha.copy(), zulu.copy()))))
        assertSame(first, next)
        assertEquals(listOf("Alpha", "zulu"), next.single().games.map { it.title })
    }

    @Test fun changingOneShelfRetainsOtherShelvesAndUnchangedGames() {
        val projector = RomLibraryProjector()
        val other = RomLibraryRows(metadata.copy(id = 2), emptyList())
        val first = projector.project(listOf(RomLibraryRows(metadata, listOf(alpha, zulu)), other))
        val changed = zulu.copy(title = "Aardvark", modified = 2, coverUri = "content://cover")
        val next = projector.project(listOf(RomLibraryRows(metadata, listOf(alpha, changed)), other))
        assertSame(first[1], next[1])
        assertSame(first[0].games[0], next[0].games[1])
        assertEquals("Aardvark", next[0].games[0].title)
        assertEquals(2L, next[0].games[0].modified)
        assertEquals("content://cover", next[0].games[0].coverUri)
    }

    @Test fun metadataChangesReuseSortedGamesAndInvalidModeFallsBack() {
        val projector = RomLibraryProjector()
        val first = projector.project(listOf(RomLibraryRows(metadata, listOf(alpha))))
        val next = projector.project(listOf(RomLibraryRows(metadata.copy(name = "Renamed", launchMode = "future"), listOf(alpha))))
        assertSame(first[0].games, next[0].games)
        assertEquals("Renamed", next[0].name)
        assertEquals(RomLaunchMode.AUTOMATIC, next[0].launchMode)
    }

    @Test fun removedGamesAndShelvesAreNotRetainedInTheNextProjection() {
        val projector = RomLibraryProjector()
        projector.project(listOf(RomLibraryRows(metadata, listOf(alpha, zulu))))
        assertEquals(listOf("Alpha"), projector.project(listOf(RomLibraryRows(metadata, listOf(alpha)))).single().games.map { it.title })
        assertTrue(projector.project(emptyList()).isEmpty())
        assertEquals(listOf("zulu"), projector.project(listOf(RomLibraryRows(metadata, listOf(zulu)))).single().games.map { it.title })
    }
}
