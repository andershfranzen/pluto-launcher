package dev.pluto.launcher.domain

import dev.pluto.launcher.model.Organization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutBackupTest {
    private val apps = listOf("0|com.f/.F", "0|com.g/.G")

    private fun folder(id: Long, name: String, apps: List<String>) = LayoutFolder(id, name, apps)

    private fun backup(
        homeItems: List<String> = listOf("app:0|com.a/.Main", "folder:7"),
        dock: List<String?> = listOf("0|com.d/.D", null, null, null, null),
        folders: List<LayoutFolder> = listOf(folder(7, "Games = fun \\ stuff\nsecond line", apps)),
        categories: List<LayoutCategory> = listOf(
            LayoutCategory(1, "All apps", "ALL", 0, emptyList()),
            LayoutCategory(2, "Games", "GAMES", 1, listOf(apps.first())),
            LayoutCategory(5, "Work, really", null, 2, listOf("0|com.w/.W")),
        ),
        hidden: List<String> = listOf("0|com.h/.H"),
    ) = LayoutBackup(homeItems, dock, folders, categories, hidden)

    /** Hand-written fixtures get the record-count line a real backup ends with. */
    private fun withEnd(text: String): String {
        val records = text.lines().count { line -> line.indexOf('=') > 0 && !line.startsWith("#") && !line.startsWith("pluto-layout=") }
        return "$text\nend=$records\n"
    }

    private fun read(text: String): LayoutBackup =
        (LayoutBackupCodec.decode(text) as LayoutBackupRead.Ok).backup

    @Test fun carriesAWholeLayoutThroughTheTextForm() {
        val original = backup(homeItems = listOf("app:0|com.a/.Main", "folder:7", "app:0|com.b/.B"))
        assertEquals(original, read(LayoutBackupCodec.encode(original)))
    }

    @Test fun writingIsStableSoTwoFilesCanBeCompared() {
        val once = LayoutBackupCodec.encode(backup())
        assertEquals(once, LayoutBackupCodec.encode(backup()))
        // Re-writing what was read produces the same bytes: nothing drifts on a restore.
        assertEquals(once, LayoutBackupCodec.encode(read(once)))
    }

    @Test fun widgetsNeverReachTheFile() {
        val withWidgets = backup(homeItems = listOf("app:0|com.a/.Main", "widget:4", "folder:7"))
        val text = LayoutBackupCodec.encode(withWidgets)
        assertTrue("widget:4" !in text)
        assertEquals(listOf("app:0|com.a/.Main", "folder:7"), read(text).homeItems)
    }

    @Test fun refusesABackupFromANewerPlutoRatherThanDroppingLayout() {
        assertEquals(LayoutBackupRead.TooNew(2), LayoutBackupCodec.decode("pluto-layout=2\nhome.0=app:0|com.a/.A\n"))
    }

    @Test fun refusesAFileThatIsNotABackup() {
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode(""))
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode("just some notes\n"))
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode("# a comment only\n"))
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode("pluto-layout=soon\n"))
    }

    @Test fun refusesACutOffCopyInsteadOfReplacingTheLayoutWithAFragment() {
        val whole = LayoutBackupCodec.encode(backup())
        val cut = whole.lines().filterNot { it.startsWith("end=") }.dropLast(3).joinToString("\n")
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode(cut))
        // The end line is there but records went missing before it (a partly synced copy).
        val missing = whole.lines().filterNot { it.startsWith("folder.") }.joinToString("\n")
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode(missing))
        // Only the header: nothing to restore, and certainly not an empty layout.
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode("pluto-layout=1\n"))
    }

    @Test fun refusesRecordsAfterTheEndLine() {
        val joined = LayoutBackupCodec.encode(backup()) + "home.9=app:0|com.z/.Z\n"
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode(joined))
    }

    @Test fun idsTheDatabaseCouldNotContinueFromAreDropped() {
        val text = """
            pluto-layout=1
            folder.9223372036854775807.name=Too far
            folder.9223372036854775807.app.0=0|com.f/.F
            category.4294967296.name=Too far
        """.trimIndent().let(::withEnd)
        val read = read(text)
        assertEquals(emptyList<LayoutFolder>(), read.folders)
        assertEquals(emptyList<LayoutCategory>(), read.categories)
    }

    @Test fun dropsWhatItCannotReadAndKeepsEverythingElse() {
        val text = """
            pluto-layout=1
            future.key=ignored
            home.0=app:0|com.a/.A
            home.1=app:not-an-app-key
            home.2=folder:404
            home.3=widget:9
            home.4
            dock.0=0|com.d/.D
            dock.9=0|com.far/.Far
            dock.1=rubbish
            folder.7.name=Kept
            folder.7.app.0=0|com.g/.G
            folder.7.app.1=broken
            folder.8.name=
            hidden.0=0|com.h/.H
        """.trimIndent().let(::withEnd)
        val read = read(text)
        assertEquals(listOf("app:0|com.a/.A"), read.homeItems)
        assertEquals(listOf("0|com.d/.D", null, null, null, null), read.dock)
        assertEquals(listOf(folder(7, "Kept", listOf("0|com.g/.G"))), read.folders)
        assertEquals(listOf("0|com.h/.H"), read.hidden)
    }

    @Test fun anAppBelongsToOneFolderOnly() {
        val text = """
            pluto-layout=1
            folder.9.name=Later
            folder.9.app.0=0|com.f/.F
            folder.3.name=First
            folder.3.app.0=0|com.f/.F
            folder.3.app.1=0|com.g/.G
        """.trimIndent().let(::withEnd)
        assertEquals(
            listOf(folder(3, "First", apps), folder(9, "Later", emptyList())),
            read(text).folders,
        )
    }

    @Test fun anAppThatMovedIntoAFolderLeavesTheGrid() {
        val text = """
            pluto-layout=1
            home.0=app:0|com.f/.F
            home.1=app:0|com.g/.G
            folder.3.name=Games
            folder.3.app.0=0|com.f/.F
        """.trimIndent().let(::withEnd)
        assertEquals(listOf("app:0|com.g/.G"), read(text).homeItems)
    }

    @Test fun aBuiltInCategoryKeepsOneRowAndItsNameIsKeptOtherwise() {
        val text = """
            pluto-layout=1
            category.1.name=All apps
            category.1.builtIn=ALL
            category.4.name=Games
            category.4.builtIn=GAMES
            category.4.app.0=0|com.f/.F
            category.6.name=Games again
            category.6.builtIn=GAMES
            category.7.name=Invented
            category.7.builtIn=NOT_A_CATEGORY
        """.trimIndent().let(::withEnd)
        val categories = read(text).categories
        assertEquals(listOf("ALL", "GAMES", null, null), categories.map { it.builtIn })
        assertEquals(listOf("0|com.f/.F"), categories[1].apps)
    }

    @Test fun theCategoryStripKeepsTheOrderTheUserArrangedItIn() {
        val text = """
            pluto-layout=1
            category.1.name=All apps
            category.1.builtIn=ALL
            category.1.position=0
            category.2.name=Games
            category.2.builtIn=GAMES
            category.2.position=2
            category.9.name=Work
            category.9.position=1
        """.trimIndent().let(::withEnd)
        assertEquals(listOf("All apps", "Work", "Games"), read(text).categories.map { it.name })
        // A file with no positions at all still loads, in id order.
        val withoutPositions = """
            pluto-layout=1
            category.1.name=All apps
            category.1.builtIn=ALL
            category.2.name=Games
            category.2.builtIn=GAMES
            category.9.name=Work
        """.trimIndent().let(::withEnd)
        assertEquals(listOf("All apps", "Games", "Work"), read(withoutPositions).categories.map { it.name })
    }

    @Test fun aDamagedFileIsNotMistakenForAnEmptyLayout() {
        // A bare header could be any cut-off file, so it is refused; an empty layout that was
        // really written says so with its end line and still restores.
        assertEquals(LayoutBackupRead.Unreadable, LayoutBackupCodec.decode("pluto-layout=1\n"))
        val empty = read(LayoutBackupCodec.encode(LayoutBackup(emptyList(), List(Organization.DOCK_SLOTS) { null }, emptyList(), emptyList(), emptyList())))
        assertEquals(emptyList<String>(), empty.homeItems)
        assertEquals(emptyList<LayoutFolder>(), empty.folders)
        assertTrue(empty.dock.all { it == null })
        assertNotEquals(empty, backup())
    }

    @Test fun theDockKeepsTheFirstSlotOfAnAppAndItsShape() {
        val text = """
            pluto-layout=1
            dock.3=0|com.d/.D
            dock.1=0|com.d/.D
            dock.4=0|com.e/.E
        """.trimIndent().let(::withEnd)
        assertEquals(listOf(null, "0|com.d/.D", null, null, "0|com.e/.E"), read(text).dock)
    }
}
