package dev.pluto.launcher.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pluto.launcher.data.db.CategoryAppEntity
import dev.pluto.launcher.data.db.CategoryEntity
import dev.pluto.launcher.data.db.DockSlotEntity
import dev.pluto.launcher.data.db.FolderAppEntity
import dev.pluto.launcher.data.db.FolderEntity
import dev.pluto.launcher.data.db.HiddenAppEntity
import dev.pluto.launcher.data.db.HomeItemEntity
import dev.pluto.launcher.data.db.LauncherDatabase
import dev.pluto.launcher.data.db.RecentLaunchEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that every exported schema version (app/schemas, packaged as androidTest assets)
 * migrates to [LauncherDatabase.VERSION] using only the hand-written [LauncherDatabase.MIGRATIONS],
 * and that user organisation survives the trip.
 *
 * Adding schema version N+1:
 * 1. Bump [LauncherDatabase.VERSION] and build once so Room exports `N+1.json`.
 * 2. Add `Migration(N, N+1)` to [LauncherDatabase.MIGRATIONS].
 * 3. Add a `migrateNToNPlus1_...` test here that seeds version N with raw SQL and checks the
 *    migrated rows (including any new columns' defaults). [allExportedVersionsMigrateToLatest]
 *    picks up the new version automatically.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, LauncherDatabase::class.java)

    private val openedRoomDatabases = mutableListOf<LauncherDatabase>()

    @Before
    fun deleteLeftovers() {
        context.deleteDatabase(TEST_DB)
        for (v in 1..LauncherDatabase.VERSION) context.deleteDatabase(versionDbName(v))
    }

    @After
    fun closeRoom() {
        openedRoomDatabases.forEach { it.close() }
        openedRoomDatabases.clear()
    }

    @Test
    fun version1DataSurvivesMigrationToLatest() {
        helper.createDatabase(TEST_DB, 1).use { db -> seedVersion1(db) }

        // Runs any migrations from 1 to latest and validates the result against the exported
        // schema of the latest version. With VERSION == 1 this validates the v1 schema itself.
        helper.runMigrationsAndValidate(TEST_DB, LauncherDatabase.VERSION, true, *LauncherDatabase.MIGRATIONS)
            .close()

        val dao = openWithRoom(TEST_DB).dao()
        runBlocking {
            assertEquals(
                listOf(HomeItemEntity("app:$KEY_MAPS", 0), HomeItemEntity("folder:7", 1)),
                dao.homeItems(),
            )
            assertEquals(
                listOf(DockSlotEntity(0, KEY_PHONE), DockSlotEntity(3, KEY_BROWSER)),
                dao.dock(),
            )
            assertEquals(listOf(FolderEntity(7, "Work")), dao.observeFolders().first())
            assertEquals(
                listOf(FolderAppEntity(KEY_MAIL, 7, 0), FolderAppEntity(KEY_TERMINAL, 7, 1)),
                dao.folderApps(7),
            )
            assertEquals(FolderAppEntity(KEY_TERMINAL, 7, 1), dao.folderAppFor(KEY_TERMINAL))
            assertEquals(
                listOf(
                    CategoryEntity(1, "All apps", "ALL", 0),
                    CategoryEntity(2, "Games", "GAMES", 1),
                    CategoryEntity(3, "Tools", "TOOLS", 2),
                    CategoryEntity(4, "Émulateurs", null, 3),
                ),
                dao.categories(),
            )
            assertEquals(
                setOf(
                    CategoryAppEntity(2, KEY_GAME),
                    CategoryAppEntity(3, KEY_TERMINAL),
                    CategoryAppEntity(4, KEY_GAME),
                ),
                dao.observeCategoryApps().first().toSet(),
            )
            assertEquals(listOf(HiddenAppEntity(KEY_HIDDEN)), dao.observeHidden().first())
            assertEquals(
                listOf(RecentLaunchEntity(KEY_GAME, 2_000L), RecentLaunchEntity(KEY_MAPS, 1_000L)),
                dao.recents(),
            )

            // Foreign keys still cascade after migration: deleting a folder drops its membership only.
            dao.deleteFolder(7)
            assertTrue(dao.folderApps(7).isEmpty())
            assertEquals(2, dao.homeItems().size)
        }
    }

    @Test
    fun migrationChainCoversEveryVersion() {
        for (start in 1 until LauncherDatabase.VERSION) {
            assertNotNull(
                "No migration path from version $start to ${LauncherDatabase.VERSION}",
                migrationPath(start, LauncherDatabase.VERSION, LauncherDatabase.MIGRATIONS),
            )
        }
    }

    @Test
    fun allExportedVersionsMigrateToLatest() {
        for (version in 1..LauncherDatabase.VERSION) {
            val name = versionDbName(version)
            // Fails with FileNotFoundException if the schema for this version was not exported/committed.
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, LauncherDatabase.VERSION, true, *LauncherDatabase.MIGRATIONS)
                .close()
            // Room itself must also accept the database without a destructive fallback.
            val db = openWithRoom(name)
            runBlocking { db.dao().categoryCount() }
        }
    }

    // --- Helpers -----------------------------------------------------------

    private fun openWithRoom(name: String): LauncherDatabase =
        Room.databaseBuilder(context, LauncherDatabase::class.java, name)
            .addMigrations(*LauncherDatabase.MIGRATIONS)
            .build()
            .also { openedRoomDatabases += it }

    /** Inserts at least one row into every v1 table using raw SQL (no generated code involved). */
    private fun seedVersion1(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO folder (id, name) VALUES (7, 'Work')")
        db.execSQL("INSERT INTO home_item (itemId, position) VALUES (?, 0)", arrayOf("app:$KEY_MAPS"))
        db.execSQL("INSERT INTO home_item (itemId, position) VALUES ('folder:7', 1)")
        db.execSQL("INSERT INTO dock_slot (slot, appKey) VALUES (0, ?)", arrayOf(KEY_PHONE))
        db.execSQL("INSERT INTO dock_slot (slot, appKey) VALUES (3, ?)", arrayOf(KEY_BROWSER))
        db.execSQL("INSERT INTO folder_app (appKey, folderId, position) VALUES (?, 7, 0)", arrayOf(KEY_MAIL))
        db.execSQL("INSERT INTO folder_app (appKey, folderId, position) VALUES (?, 7, 1)", arrayOf(KEY_TERMINAL))
        db.execSQL("INSERT INTO category (id, name, builtIn, position) VALUES (1, 'All apps', 'ALL', 0)")
        db.execSQL("INSERT INTO category (id, name, builtIn, position) VALUES (2, 'Games', 'GAMES', 1)")
        db.execSQL("INSERT INTO category (id, name, builtIn, position) VALUES (3, 'Tools', 'TOOLS', 2)")
        db.execSQL("INSERT INTO category (id, name, builtIn, position) VALUES (4, ?, NULL, 3)", arrayOf("Émulateurs"))
        db.execSQL("INSERT INTO category_app (categoryId, appKey) VALUES (2, ?)", arrayOf(KEY_GAME))
        db.execSQL("INSERT INTO category_app (categoryId, appKey) VALUES (3, ?)", arrayOf(KEY_TERMINAL))
        db.execSQL("INSERT INTO category_app (categoryId, appKey) VALUES (4, ?)", arrayOf(KEY_GAME))
        db.execSQL("INSERT INTO hidden_app (appKey) VALUES (?)", arrayOf(KEY_HIDDEN))
        db.execSQL("INSERT INTO recent_launch (appKey, launchedAt) VALUES (?, 1000)", arrayOf(KEY_MAPS))
        db.execSQL("INSERT INTO recent_launch (appKey, launchedAt) VALUES (?, 2000)", arrayOf(KEY_GAME))
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        fun versionDbName(version: Int) = "migration-v$version.db"

        // Encoded AppKeys ("<userSerial>|<package>/<activity>") as stored by the app.
        const val KEY_MAPS = "0|com.example.maps/.MainActivity"
        const val KEY_PHONE = "0|com.example.dialer/.DialerActivity"
        const val KEY_BROWSER = "0|com.example.browser/.Browser"
        const val KEY_MAIL = "0|com.example.mail/.Inbox"
        const val KEY_TERMINAL = "0|com.example.terminal/.Term"
        const val KEY_GAME = "0|com.example.game/.GameActivity"
        const val KEY_HIDDEN = "0|com.example.bloat/.Launcher"

        /**
         * Finds a chain of migrations from [start] to [end], preferring the largest jump at each
         * step (as Room does). Returns null when the chain is broken.
         */
        fun migrationPath(start: Int, end: Int, migrations: Array<Migration>): List<Migration>? {
            val path = mutableListOf<Migration>()
            var current = start
            while (current < end) {
                val next = migrations
                    .filter { it.startVersion == current && it.endVersion in (current + 1)..end }
                    .maxByOrNull { it.endVersion } ?: return null
                path += next
                current = next.endVersion
            }
            return path
        }
    }
}
