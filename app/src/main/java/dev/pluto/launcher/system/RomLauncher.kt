package dev.pluto.launcher.system

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.model.RomLaunchMode
import dev.pluto.launcher.model.RomLibrary

/**
 * Opens a game in the tab's emulator. DuckStation has an adapter that hands the document
 * straight to its own activity; everything else goes through Android's Open file request,
 * pinned to the chosen emulator, where the emulator's document handling takes over.
 */
object RomLauncher {
    const val DUCKSTATION_PACKAGE = "com.github.stenzek.duckstation"

    /**
     * The direct-launch method Pluto has for an emulator, or null when it has none. Only
     * adapters verified on a device belong here: a wrong activity name or a missing extra (such
     * as RetroArch's core path) turns a working Open file launch into a failure.
     */
    fun directMode(packageName: String?): RomLaunchMode? = when (packageName) {
        DUCKSTATION_PACKAGE -> RomLaunchMode.DUCKSTATION
        else -> null
    }

    /** What AUTOMATIC resolves to: a known adapter for the chosen emulator, else Open file. */
    fun automaticMode(packageName: String?): RomLaunchMode = directMode(packageName) ?: RomLaunchMode.VIEW

    /**
     * Document URI grants accompany both standard ACTION_VIEW and DuckStation's extras.
     * [emulatorPackage] is a per-game emulator choice: it replaces the tab's default and decides
     * the launch method from that emulator, even in a tab that otherwise asks which app to use.
     */
    fun intent(library: RomLibrary, game: RomGame, emulatorPackage: String? = null): Intent {
        val uri = Uri.parse(game.uri)
        val pkg = emulatorPackage ?: library.emulatorPackage
        val mode = when {
            emulatorPackage != null || library.launchMode == RomLaunchMode.AUTOMATIC -> automaticMode(pkg)
            library.launchMode == RomLaunchMode.OPEN_WITH -> RomLaunchMode.OPEN_WITH
            library.launchMode == RomLaunchMode.DUCKSTATION -> RomLaunchMode.DUCKSTATION
            // Unverified adapters stored by a pre-release build open like Android Open file.
            else -> RomLaunchMode.VIEW
        }
        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        open.clipData = ClipData.newRawUri(game.title, uri).apply {
            // Disc descriptors and playlists may open sibling tracks/discs. Grant the
            // user-selected tree as well as the entry document, without write access.
            addItem(ClipData.Item(Uri.parse(library.treeUri)))
        }
        if (mode == RomLaunchMode.DUCKSTATION) {
            // DuckStation takes the document itself, which keeps cloud and downloads folders working.
            val duck = pkg ?: DUCKSTATION_PACKAGE
            open.component = ComponentName(duck, "$duck.EmulationActivity")
            open.putExtra("bootPath", game.uri)
            open.putExtra("resumeState", false)
        } else if (pkg != null && mode != RomLaunchMode.OPEN_WITH) {
            // "Ask each time" is left unpinned, or the chooser would show one entry.
            open.setPackage(pkg)
        }
        return if (mode == RomLaunchMode.OPEN_WITH || (pkg == null && mode != RomLaunchMode.DUCKSTATION)) {
            Intent.createChooser(open, "Open ${game.title}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else open
    }

    fun launch(context: Context, library: RomLibrary, game: RomGame, emulatorPackage: String? = null): String? = try {
        context.startActivity(intent(library, game, emulatorPackage))
        null
    } catch (_: android.content.ActivityNotFoundException) {
        "This app could not open the game. In ROM tabs, choose another default emulator or try Advanced options."
    } catch (_: SecurityException) {
        "Folder access was lost. In ROM tabs, edit the tab and choose the ROM folder again."
    } catch (_: IllegalArgumentException) {
        "This ROM is unavailable. Refresh its shelf or choose the folder again."
    }
}
