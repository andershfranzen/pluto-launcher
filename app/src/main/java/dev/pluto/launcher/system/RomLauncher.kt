package dev.pluto.launcher.system

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.model.RomLaunchMode
import dev.pluto.launcher.model.RomLibrary

object RomLauncher {
    const val DUCKSTATION_PACKAGE = "com.github.stenzek.duckstation"

    /** Document URI grants accompany both standard ACTION_VIEW and emulator-specific extras. */
    fun intent(library: RomLibrary, game: RomGame): Intent {
        val uri = Uri.parse(game.uri)
        val mode = when (library.launchMode) {
            RomLaunchMode.AUTOMATIC -> if (library.emulatorPackage == DUCKSTATION_PACKAGE) RomLaunchMode.DUCKSTATION else RomLaunchMode.VIEW
            else -> library.launchMode
        }
        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        open.clipData = ClipData.newRawUri(game.title, uri).apply {
            // Disc descriptors and playlists may open sibling tracks/discs. Grant the
            // user-selected tree as well as the entry document, without write access.
            addItem(ClipData.Item(Uri.parse(library.treeUri)))
        }
        if (mode == RomLaunchMode.DUCKSTATION) {
            val pkg = library.emulatorPackage ?: DUCKSTATION_PACKAGE
            open.component = ComponentName(pkg, "$pkg.EmulationActivity")
            open.putExtra("bootPath", game.uri)
            open.putExtra("resumeState", false)
        } else if (mode != RomLaunchMode.OPEN_WITH && library.emulatorPackage != null) {
            open.setPackage(library.emulatorPackage)
        }
        return if (mode == RomLaunchMode.OPEN_WITH || (library.emulatorPackage == null && mode != RomLaunchMode.DUCKSTATION)) {
            Intent.createChooser(open, "Open ${game.title}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else open
    }

    fun launch(context: Context, library: RomLibrary, game: RomGame): String? = try {
        context.startActivity(intent(library, game))
        null
    } catch (_: android.content.ActivityNotFoundException) {
        "This app could not open the game. In ROM tabs, choose another default emulator or try Advanced options."
    } catch (_: SecurityException) {
        "Folder access was lost. In ROM tabs, edit the tab and choose the ROM folder again."
    } catch (_: IllegalArgumentException) {
        "This ROM is unavailable. Refresh its shelf or choose the folder again."
    }
}
