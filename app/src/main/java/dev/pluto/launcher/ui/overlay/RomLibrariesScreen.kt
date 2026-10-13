package dev.pluto.launcher.ui.overlay

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.model.RomLibrary
import dev.pluto.launcher.model.RomLaunchMode
import dev.pluto.launcher.system.RomLauncher
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.console.ShelfId
import java.text.DateFormat
import java.util.Date

/** Folder, emulator, name: launch troubleshooting stays out of the normal setup path. */
@Composable
fun RomLibrariesScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val libraries by vm.romLibraries.collectAsState()
    val scanning by vm.romScanning.collectAsState()
    val errors by vm.romErrors.collectAsState()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf("") }
    var emulator by rememberSaveable { mutableStateOf<String?>(null) }
    var method by rememberSaveable { mutableStateOf(RomLaunchMode.AUTOMATIC) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingEmulator by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var folderError by rememberSaveable { mutableStateOf<String?>(null) }
    var addedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf<Long?>(null) }
    var gamesPanel by rememberSaveable { mutableStateOf<Long?>(null) }
    var gameQuery by rememberSaveable { mutableStateOf("") }
    /** The game whose own emulator is being chosen; null means the tab's default emulator. */
    var emulatorGame by rememberSaveable { mutableStateOf<String?>(null) }
    val screenFocus = rememberScreenFocus("rom:add")
    val reports by vm.romReports.collectAsState()
    val installedApps = state.allApps.filter { it.isEnabled }.distinctBy { it.key.packageName }.sortedBy { it.label.lowercase() }
    val installedPackages = remember(installedApps) { installedApps.map { it.key.packageName }.toSet() }
    val suggestions = remember(installedPackages) { dev.pluto.launcher.domain.RomDiscovery.suggestions(installedPackages) }
    fun appLabel(pkg: String?): String = installedApps.firstOrNull { it.key.packageName == pkg }?.label ?: pkg.orEmpty()
    val selectedApp = installedApps.firstOrNull { it.key.packageName == emulator }
    val window = LocalWindowInfo.current.containerSize
    val compactSearch = choosingEmulator && window.width > window.height && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val original = libraries.firstOrNull { it.id == editing }
    val changed = editing != null && if (original != null) {
        name != original.name || folder != original.treeUri || emulator != original.emulatorPackage || method != original.launchMode
    } else name.isNotBlank() || folder.isNotBlank() || emulator != null
    fun edit(library: RomLibrary?) {
        editing = library?.id ?: 0L
        name = library?.name ?: ""
        folder = library?.treeUri ?: ""
        emulator = library?.emulatorPackage
        method = library?.launchMode ?: RomLaunchMode.AUTOMATIC
        advanced = method != RomLaunchMode.AUTOMATIC
        folderError = null
        choosingEmulator = false
        screenFocus.focus("rom:folder")
    }
    fun closeEditor() { editing = null; choosingEmulator = false; keyboard?.hide(); screenFocus.focus("rom:add") }
    fun close() {
        if (saving) return
        if (choosingEmulator) { choosingEmulator = false; emulatorGame = null; keyboard?.hide(); screenFocus.focus("rom:emulator") }
        else if (editing != null) { if (changed) dialog = "discard" else closeEditor() }
        else vm.back()
    }
    fun chooseEmulator(pkg: String?) {
        val game = emulatorGame
        if (game != null) vm.setRomEmulator(game, pkg)
        else { emulator = pkg; method = RomLaunchMode.AUTOMATIC }
        emulatorGame = null
        choosingEmulator = false
        keyboard?.hide()
        screenFocus.focus("rom:emulator")
    }
    /** Choosing an emulator for one game: the tab's own default is left untouched. */
    fun chooseEmulatorFor(uri: String) {
        emulatorGame = uri
        gamesPanel = null
        query = ""
        showAllApps = false
        choosingEmulator = true
        screenFocus.focus("rom:chooser")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folder = uri.toString()
                if (name.isBlank()) name = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfter(':').substringAfterLast('/').trim().take(MAX_NAME_LENGTH).ifBlank { "My games" }
                folderError = null
            } catch (_: SecurityException) {
                folderError = "Pluto could not keep access to this folder. Choose a folder on this device."
            }
        }
        screenFocus.focus("rom:folder")
    }
    BackHandler(editing != null && dialog == null) { close() }
    LayerScaffold(
        title = if (choosingEmulator) {
            if (emulatorGame != null) "Choose this game's emulator" else "Choose default emulator"
        } else if (editing == 0L) "Add ROM tab" else if (editing != null) "Edit ROM tab" else "ROM tabs",
        idPrefix = "rom", onClose = { close() }, dialogOpen = dialog != null,
        compactHeader = compactSearch,
        contentKey = Pair(editing, choosingEmulator),
        headerContent = {
            if (choosingEmulator) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compactSearch) 2.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search installed apps") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    modifier = Modifier.weight(1f),
                )
                if (compactSearch) IconAction("rom:search:close", Icons.Rounded.Close, "Close search", { close() })
            }
        },
        footerContent = {
            if (editing != null && !choosingEmulator) Column {
                HorizontalDivider()
                ButtonBar(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    PlutoButton("rom:save", if (saving) "Saving…" else if (editing == 0L) "Create tab" else "Save changes", {
                        saving = true
                        val isNew = editing == 0L
                        vm.saveRomLibrary(RomLibrary(editing ?: 0L, name, folder, emulator, method), onFailure = { saving = false }) { id ->
                            saving = false
                            if (isNew) addedId = id
                            closeEditor()
                            vm.selectShelf(ShelfId.OfRomLibrary(id))
                        }
                    }, style = ButtonStyle.FILLED, enabled = name.isNotBlank() && folder.isNotBlank() && !saving)
                    PlutoButton("rom:cancel", "Cancel", { close() }, enabled = !saving)
                }
            }
        },
        overlay = {
            AnimatedDialog(dialog) { shown ->
                when (shown) {
                    "name" -> TextInputDialog("rom:name-dialog", "Tab name", "Name", name, "Done",
                        onConfirm = { name = it; dialog = null; keyboard?.hide(); screenFocus.returnFrom("rom:name") },
                        onDismiss = { dialog = null; keyboard?.hide(); screenFocus.returnFrom("rom:name") })
                    "discard" -> ConfirmDialog("rom:discard-dialog", "Discard changes?", "Your ROM tab has not been saved.", "Discard",
                        onConfirm = { dialog = null; closeEditor() }, onDismiss = { dialog = null })
                    "delete" -> ConfirmDialog("rom:delete-dialog", "Remove $name?", "Only this tab is removed. Your ROMs and save files stay where they are.", "Remove",
                        onConfirm = { editing?.let(vm::deleteRomLibrary); dialog = null; closeEditor() },
                        onDismiss = { dialog = null; screenFocus.returnFrom("rom:delete") }, destructive = true)
                    "help" -> ModalPanel("Add games and box art", "rom:help", { dialog = null }, interceptBack = true) {
                        SectionHeader("Adding more games")
                        BodyText("Copy your games into the folder you chose, including their tracks or discs. Subfolders are included. Pluto checks for changes when you return; Refresh games checks immediately.")
                        SectionHeader("Box art")
                        BodyText("Artwork is not downloaded automatically. Put a PNG, JPG or WebP image beside the game with the same filename before the extension. For example: Spyro.cue and Spyro.png.")
                        BodyText("Pluto also reads the layouts the big front ends write: an image in boxart/, covers/ or images/, RetroArch's Named_Boxarts/ set, or a name-thumb.png variant. Then refresh the tab.")
                        SectionHeader("gamelist.xml")
                        BodyText("If your folder came from RetroPie, Batocera or another EmulationStation setup that keeps gamelist.xml beside the games, keep it. Pluto reads each entry's name and artwork, so games appear with the titles and covers you already curated.")
                        SectionHeader("Emulator setup")
                        BodyText("Install the emulator you want, open it once and finish its setup. Some systems need a BIOS file. Pluto opens your games in that app; emulator settings and save files are managed there.")
                    }
                }
            }
            AnimatedDialog(if (diagnostics != null) "diagnostics" else null) { shown ->
                if (shown == "diagnostics") {
                    val library = libraries.firstOrNull { it.id == diagnostics }
                    if (library != null) RomDiagnosticsPanel(library, reports[library.id], context) { diagnostics = null }
                }
            }
            AnimatedDialog(if (gamesPanel != null) "games" else null) { shown ->
                if (shown == "games") {
                    val library = libraries.firstOrNull { it.id == gamesPanel }
                    if (library == null) gamesPanel = null
                    else ModalPanel("Games · ${library.name}", "rom:games", { gamesPanel = null; gameQuery = "" }, interceptBack = true) {
                        BodyText("A game opens in this tab's emulator unless you pick another app for it here. The rest of the tab is not affected.")
                        if (library.games.isEmpty()) BodyText("No games in this folder yet. Add files, then refresh the tab.")
                        // A full set can hold thousands of games: rows are not lazy here, so only a
                        // bounded slice is built, with games that already have their own app first.
                        if (library.games.size > GAMES_PANEL_ROWS) OutlinedTextField(
                            value = gameQuery, onValueChange = { gameQuery = it }, label = { Text("Find a game") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val matches = library.games.filter { gameQuery.isBlank() || it.title.contains(gameQuery.trim(), ignoreCase = true) }
                            .sortedBy { it.uri !in state.settings.romEmulators }
                        if (matches.size > GAMES_PANEL_ROWS) BodyText("Showing $GAMES_PANEL_ROWS of ${matches.size} games. Type part of a name to find one.")
                        else if (matches.isEmpty() && library.games.isNotEmpty()) BodyText("No games match “${gameQuery.trim()}”.")
                        val now = System.currentTimeMillis()
                        matches.take(GAMES_PANEL_ROWS).forEach { game ->
                            val chosen = state.settings.romEmulators[game.uri]
                            val played = state.settings.romLastPlayed[game.uri]
                            ActionRow("rom:game:${game.uri}", game.title, { chooseEmulatorFor(game.uri) },
                                supporting = buildString {
                                    append(chosen?.let { "Opens in ${appLabel(it)}" } ?: "Uses this tab's default")
                                    played?.let { append(" · played ${dev.pluto.launcher.domain.RomScanSummary.age(now, it)}") }
                                })
                        }
                    }
                }
            }
        },
    ) {
        if (choosingEmulator) {
            val forGame = emulatorGame?.let { uri -> libraries.firstNotNullOfOrNull { lib -> lib.games.firstOrNull { it.uri == uri } } }
            val chosen = if (forGame != null) state.settings.romEmulators[forGame.uri] else emulator
            BodyText(if (forGame != null) "Choose the app that opens “${forGame.title}”. Only that game changes; the tab keeps its own emulator."
            else "Choose the app games in this tab should open with. You can change it later.")
            ActionRow("rom:chooser", if (forGame != null) "Use the tab's default" else "Ask each time", { chooseEmulator(null) },
                supporting = if (forGame != null) "Follow this tab's emulator, or ask, as it is set up" else "Choose an app whenever you open a game",
                selected = chosen == null)
            val previous = libraries.mapNotNull { it.emulatorPackage }.toSet()
            val suggested = installedApps.filter { it.key.packageName in previous || it.key.packageName == chosen || emulatorName.containsMatchIn(it.label + " " + it.key.packageName) }
            val filtered = installedApps.filter { it.label.contains(query.trim(), true) || it.key.packageName.contains(query.trim(), true) }
            val visible = if (query.isNotBlank() || showAllApps) filtered else suggested
            SectionHeader(if (query.isNotBlank()) "Search results" else if (showAllApps) "Installed apps" else "Suggested emulators")
            if (visible.isEmpty()) BodyText(if (query.isNotBlank()) "No installed apps match “$query”." else "Search for your emulator or show all installed apps. If it is not installed yet, install it and return here.")
            visible.forEach { app ->
                ActionRow("rom:emulator:${app.key.packageName}", app.label, { chooseEmulator(app.key.packageName) }, selected = chosen == app.key.packageName)
            }
            if (query.isBlank()) ActionRow("rom:all-apps", if (showAllApps) "Show suggested emulators" else "Show all installed apps", { showAllApps = !showAllApps }, supporting = "Use any emulator that can open your games")
        } else if (editing != null) {
            BodyText(if (editing == 0L) "Turn a folder of games into a console tab. Your files stay where they are." else "Choose where this tab finds its games and which app opens them.")
            SectionHeader("1 · ROM folder")
            ActionRow("rom:folder", if (folder.isBlank()) "Choose ROM folder" else "Change ROM folder", { picker.launch(folder.takeIf(String::isNotEmpty)?.let(Uri::parse)) },
                icon = Icons.Rounded.Folder, supporting = if (folder.isEmpty()) "Select the folder containing your games" else Uri.decode(folder.substringAfterLast('/')).substringAfter(':'))
            folderError?.let { NoticeCard(it, isError = true) }
            if (folder.isNotBlank()) BodyText("Games in subfolders are included. New games can be added to this folder later.")
            if (folder.isBlank()) {
                SectionHeader("Where games usually are")
                BodyText("Pluto cannot look inside a folder until you allow it. These are the folders the installed emulators use; tap one to open the picker there.")
                suggestions.forEach { suggestion ->
                    ActionRow(
                        "rom:suggest:${suggestion.folder}",
                        suggestion.packageName?.let { "${appLabel(it)} games" } ?: "“${suggestion.folder}” folder",
                        { picker.launch(sharedStorageFolder(suggestion.folder)) },
                        supporting = "Look in ${suggestion.folder} on this device",
                    )
                }
            }
            SectionHeader("2 · Default emulator")
            ActionRow("rom:emulator", if (emulator == null) "Ask each time" else appLabel(emulator), {
                query = ""; showAllApps = false; choosingEmulator = true; screenFocus.focus("rom:chooser")
            }, icon = Icons.Rounded.SportsEsports, supporting = if (emulator == null) "Choose a default to open games with one tap" else "Used for every game in this tab")
            if (selectedApp != null) ActionRow("rom:setup-emulator", "Open ${selectedApp.label} setup", { vm.launch(selectedApp.key) },
                supporting = if (emulator == RomLauncher.DUCKSTATION_PACKAGE) "Import your PlayStation BIOS in DuckStation, then return here" else "Finish the emulator’s first-time setup, then return here")
            else if (emulator != null) NoticeCard("This emulator is no longer installed. Choose another default.", isError = true)
            SectionHeader("3 · Tab name")
            ActionRow("rom:name", name.ifBlank { "Name this tab" }, { dialog = "name" }, supporting = "For example, PlayStation 1 or Game Boy. Shown in console mode.")
            ActionRow("rom:advanced", if (advanced) "Hide advanced options" else "Advanced options", { advanced = !advanced }, supporting = "Only needed if a game does not open correctly")
            if (advanced) ChoiceGroup("rom:method", "Open games using", buildList {
                add(RomLaunchMode.AUTOMATIC to "Automatic (recommended)")
                add(RomLaunchMode.OPEN_WITH to "Ask which app to use")
                add(RomLaunchMode.VIEW to "Android Open file")
                val adapter = RomLauncher.directMode(emulator)
                if (adapter != null) add(adapter to "${appLabel(emulator)} direct launch")
                else if (emulator == null) add(RomLaunchMode.DUCKSTATION to "DuckStation direct launch")
            }, method, { method = it }, supporting = "Emulators support different formats and ways of opening games. Direct launch needs the folder on shared storage; try Ask which app to use if the default does not work.")
            ActionRow("rom:help", "Adding games and box art", { dialog = "help" }, icon = Icons.Rounded.Info)
            if (editing != 0L) ActionRow("rom:delete", "Remove tab", { dialog = "delete" }, icon = Icons.Rounded.Delete, destructive = true)
        } else {
            addedId?.let { id -> libraries.firstOrNull { it.id == id }?.let { NoticeCard("${it.name} added. Your tab appears in console mode.") } }
            BodyText("Browse your ROM collections as tabs in console mode. Each tab has its own folder and default emulator.")
            PlutoButton("rom:add", "Add ROM tab", { edit(null) }, icon = Icons.Rounded.Add, style = ButtonStyle.FILLED)
            libraries.forEach { library ->
                SectionHeader(library.name)
                val busy = library.id in scanning
                val problem = errors[library.id]
                BodyText(if (busy) "Finding games…" else "${library.games.size} ${if (library.games.size == 1) "game" else "games"} · " + (library.emulatorPackage?.let(::appLabel) ?: "Ask each time"))
                problem?.let { NoticeCard(it, isError = true) }
                if (!busy && problem == null && library.games.isEmpty()) BodyText("No games found. Add ROMs to this folder, then refresh. You can choose another folder in Edit tab.")
                if (state.mode == LauncherMode.HANDHELD) ActionRow("rom:view:${library.id}", "View tab", { vm.selectShelf(ShelfId.OfRomLibrary(library.id)); vm.closeAllLayers() })
                ActionRow("rom:edit:${library.id}", "Edit tab", { edit(library) }, supporting = "${library.name} · Folder, default emulator and name")
                ActionRow("rom:refresh:${library.id}", if (busy) "Finding games…" else "Refresh games", { vm.refreshRomLibrary(library.id) }, icon = Icons.Rounded.Refresh, enabled = !busy)
                ActionRow("rom:games:${library.id}", "Games and emulators", { gamesPanel = library.id }, icon = Icons.Rounded.SportsEsports,
                    supporting = "Pick an emulator for one game, and see what has been played")
                ActionRow("rom:diagnostics:${library.id}", "Diagnostics", { diagnostics = library.id }, icon = Icons.Rounded.Info,
                    supporting = reports[library.id]?.let { "Last check: ${it.documents} files, ${it.games} games" }
                        ?: "See what Pluto found in this folder")
            }
            ActionRow("rom:help", "How to add games and box art", { dialog = "help" }, icon = Icons.Rounded.Info)
        }
    }
}

private val emulatorName = Regex("duckstation|retroarch|ppsspp|dolphin|aethersx2|nethersx2|epsxe|fpse|melon(ds)?|desmume|dra(stic)?|citra|lime3ds|azahar|mupen|n64|snes9x|nes\\.emu|gba\\.emu|gbc\\.emu|pizzaboy|pizza boy|mgba|lemuroid|redream|flycast|reicast|yabasanshiro|yaba sanshiro|saturn\\.emu|scummvm|dosbox|emu\\.project", RegexOption.IGNORE_CASE)

/** Opens the folder picker already inside [folder] on shared storage, so it is one tap away. */
private fun sharedStorageFolder(folder: String): Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$folder")

/**
 * What one folder check saw, so a shelf that looks empty — or a game that will not open —
 * explains itself. Mirrors the controller diagnostics screen: plain facts, no setup path.
 */
@Composable
private fun RomDiagnosticsPanel(
    library: RomLibrary,
    report: dev.pluto.launcher.domain.RomScanReport?,
    context: android.content.Context,
    onClose: () -> Unit,
) {
    val readAccess = remember(library.treeUri) {
        context.contentResolver.persistedUriPermissions.any { it.uri.toString() == library.treeUri && it.isReadPermission }
    }
    ModalPanel("Diagnostics · ${library.name}", "rom:diag", onClose, interceptBack = true) {
        SectionHeader("Folder")
        BodyText(Uri.decode(library.treeUri))
        BodyText(
            if (readAccess) "Folder access is granted. Pluto can read this folder."
            else "Access is missing. Open Edit tab and choose this folder again."
        )
        if (!readAccess) NoticeCard("Android is no longer letting Pluto read this folder.", isError = true)
        SectionHeader("Last check")
        if (report == null) {
            BodyText("No check has run in this session yet. Refresh games reads the folder now.")
        } else {
            BodyText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(report.at)))
            BodyText("${report.documents} files checked · ${report.games} games · ${report.gamesWithArt} with box art")
            report.systems.values.distinct().sorted().takeIf { it.isNotEmpty() }?.let {
                BodyText("Systems in this folder: ${it.joinToString(", ")}. Each is a filter in the console browse grid.")
            }
            report.saves.size.takeIf { it > 0 }?.let {
                BodyText("$it ${if (it == 1) "game has" else "games have"} save data beside them (battery saves or savestates).")
            }
            BodyText(
                if (report.gamelistEntries > 0) "gamelist.xml: ${report.gamelistEntries} entries read. Its names and artwork are used."
                else "No gamelist.xml here. Names come from filenames; artwork from the boxart layout."
            )
        }
        SectionHeader("Not shown as games")
        BodyText(
            when {
                report == null -> "Run a check to list what is skipped."
                report.skipped == 0 -> "Nothing skipped: every file is a game, artwork or a track."
                else -> "${report.skipped} files are not games: ${report.skippedExtensions}."
            }
        )
        report?.takeIf { it.sheets > 0 }?.let { BodyText("${it.sheets} playlists read for their tracks and discs.") }
        report?.takeIf { it.missingTracks > 0 }?.let {
            NoticeCard("${it.missingTracks} tracks or discs named by a playlist are missing from this folder. Copy them beside the playlist; those games cannot open.", isError = true)
        }
        if (report != null && report.games == 0) NoticeCard("No games were found in this folder.", isError = true)
    }
}

/** Rows the Games panel builds at once; the rest are reached by searching. */
private const val GAMES_PANEL_ROWS = 60
