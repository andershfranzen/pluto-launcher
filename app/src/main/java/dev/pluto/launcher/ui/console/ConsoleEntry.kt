package dev.pluto.launcher.ui.console

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.RomGame

/** One carousel card; ROM documents never enter the Android app catalogue. */
sealed interface ConsoleEntry {
    val key: String
    val label: String
    val packageName: String
    val isEnabled: Boolean

    data class App(val entry: AppEntry) : ConsoleEntry {
        override val key get() = "app:${entry.key.encode()}"
        override val label get() = entry.label
        override val packageName get() = entry.packageName
        override val isEnabled get() = entry.isEnabled
    }

    data class Rom(val libraryId: Long, val game: RomGame) : ConsoleEntry {
        override val key get() = "rom:$libraryId:${game.uri}"
        override val label get() = game.title
        override val packageName get() = ""
        override val isEnabled get() = true
    }
}
