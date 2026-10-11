package dev.pluto.launcher.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pluto.launcher.data.db.LauncherDatabase
import dev.pluto.launcher.data.db.RomDao
import dev.pluto.launcher.data.db.RomGameEntity
import dev.pluto.launcher.data.db.RomLibraryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RomRepositoryTest {
    private lateinit var db: LauncherDatabase
    private lateinit var dao: RomDao
    private var id = 0L
    private val tree = "content://documents/tree/ps1"
    private fun game(uri: String = "content://documents/spyro.cue") =
        RomGameEntity(id, uri, "Spyro.cue", "Spyro", 10, "content://documents/spyro.png")

    @Before fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java).build()
        dao = db.romDao()
        id = dao.saveLibrary(RomLibraryEntity(name = "PS1", treeUri = tree, emulatorPackage = null, launchMode = "AUTOMATIC"))
        // Count actual writes, rather than relying on Room's coalesced invalidation timing.
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE rom_write_audit (value INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO rom_write_audit VALUES (0)")
        sql.execSQL("CREATE TRIGGER rom_insert_audit AFTER INSERT ON rom_game BEGIN UPDATE rom_write_audit SET value = value + 1; END")
        sql.execSQL("CREATE TRIGGER rom_delete_audit AFTER DELETE ON rom_game BEGIN UPDATE rom_write_audit SET value = value + 1; END")
    }

    @After fun teardown() = db.close()

    private fun writes(): Int = db.openHelper.readableDatabase.query("SELECT value FROM rom_write_audit").use {
        check(it.moveToFirst())
        it.getInt(0)
    }

    @Test fun unchangedReorderedScanDoesNotWriteGames() = runTest {
        val games = listOf(game(), game("content://documents/crash.cue"))
        dao.replaceGames(id, tree, games)
        val before = writes()
        dao.replaceGames(id, tree, games.reversed().map { it.copy() })
        assertEquals(before, writes())
        assertEquals(games.toSet(), dao.games(id).toSet())
    }

    @Test fun coverAndModificationChangesAreSavedAndRemovedGamesDisappear() = runTest {
        val original = game()
        dao.replaceGames(id, tree, listOf(original, game("content://documents/crash.cue")))
        val changed = original.copy(modified = 99, coverUri = "content://documents/new.png")
        dao.replaceGames(id, tree, listOf(changed))
        assertEquals(listOf(changed), dao.games(id))
        dao.replaceGames(id, tree, emptyList())
        assertTrue(dao.games(id).isEmpty())
        val before = writes()
        dao.replaceGames(id, tree, emptyList())
        assertEquals(before, writes())
    }

    @Test fun staleFolderScanCannotOverwriteCurrentShelf() = runTest {
        val row = dao.library(id)!!
        dao.saveLibrary(row.copy(treeUri = "content://documents/tree/new"))
        val current = game("content://documents/new.cue")
        dao.replaceGames(id, "content://documents/tree/new", listOf(current))
        val before = writes()
        dao.replaceGames(id, tree, listOf(game()))
        assertEquals(before, writes())
        assertEquals(listOf(current), dao.games(id))
        dao.deleteLibrary(id)
        dao.replaceGames(id, tree, listOf(game()))
        assertTrue(dao.games(id).isEmpty())
    }

    @Test fun invalidScanRollsBackWithoutDestroyingLastGoodGames() = runTest {
        val original = game()
        val other = game("content://documents/crash.cue")
        dao.replaceGames(id, tree, listOf(original, other))
        val before = writes()
        try {
            dao.replaceGames(id, tree, listOf(original, original))
            fail("Duplicate scan URIs must fail atomically")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Duplicate primary key after the delete must roll back the whole transaction.
        }
        assertEquals(before, writes())
        assertEquals(setOf(original, other), dao.games(id).toSet())
    }
}
