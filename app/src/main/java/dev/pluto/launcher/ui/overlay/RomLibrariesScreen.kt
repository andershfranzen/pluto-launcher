package dev.pluto.launcher.ui.overlay

import android.content.Intent
import android.net.Uri
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
    val screenFocus = rememberScreenFocus("rom:add")
    val installedApps = state.allApps.filter { it.isEnabled }.distinctBy { it.key.packageName }.sortedBy { it.label.lowercase() }
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
        if (choosingEmulator) { choosingEmulator = false; keyboard?.hide(); screenFocus.focus("rom:emulator") }
        else if (editing != null) { if (changed) dialog = "discard" else closeEditor() }
        else vm.back()
    }
    fun chooseEmulator(pkg: String?) {
        emulator = pkg
        method = RomLaunchMode.AUTOMATIC
        choosingEmulator = false
        keyboard?.hide()
        screenFocus.focus("rom:emulator")
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
        title = if (choosingEmulator) "Choose default emulator" else if (editing == 0L) "Add ROM tab" else if (editing != null) "Edit ROM tab" else "ROM tabs",
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
                        BodyText("Artwork is not downloaded automatically yet. Put a PNG, JPG or WebP image beside the game with the same filename before the extension. For example: Spyro.cue and Spyro.png. Then refresh the tab.")
                        SectionHeader("Emulator setup")
                        BodyText("Install the emulator you want, open it once and finish its setup. Some systems need a BIOS file. Pluto opens your games in that app; emulator settings and save files are managed there.")
                    }
                }
            }
        },
    ) {
        if (choosingEmulator) {
            BodyText("Choose the app games in this tab should open with. You can change it later.")
            ActionRow("rom:chooser", "Ask each time", { chooseEmulator(null) }, supporting = "Choose an app whenever you open a game", selected = emulator == null)
            val previous = libraries.mapNotNull { it.emulatorPackage }.toSet()
            val suggested = installedApps.filter { it.key.packageName in previous || it.key.packageName == emulator || emulatorName.containsMatchIn(it.label + " " + it.key.packageName) }
            val filtered = installedApps.filter { it.label.contains(query.trim(), true) || it.key.packageName.contains(query.trim(), true) }
            val visible = if (query.isNotBlank() || showAllApps) filtered else suggested
            SectionHeader(if (query.isNotBlank()) "Search results" else if (showAllApps) "Installed apps" else "Suggested emulators")
            if (visible.isEmpty()) BodyText(if (query.isNotBlank()) "No installed apps match “$query”." else "Search for your emulator or show all installed apps. If it is not installed yet, install it and return here.")
            visible.forEach { app ->
                ActionRow("rom:emulator:${app.key.packageName}", app.label, { chooseEmulator(app.key.packageName) }, selected = emulator == app.key.packageName)
            }
            if (query.isBlank()) ActionRow("rom:all-apps", if (showAllApps) "Show suggested emulators" else "Show all installed apps", { showAllApps = !showAllApps }, supporting = "Use any emulator that can open your games")
        } else if (editing != null) {
            BodyText(if (editing == 0L) "Turn a folder of games into a console tab. Your files stay where they are." else "Choose where this tab finds its games and which app opens them.")
            SectionHeader("1 · ROM folder")
            ActionRow("rom:folder", if (folder.isBlank()) "Choose ROM folder" else "Change ROM folder", { picker.launch(folder.takeIf(String::isNotEmpty)?.let(Uri::parse)) },
                icon = Icons.Rounded.Folder, supporting = if (folder.isEmpty()) "Select the folder containing your games" else Uri.decode(folder.substringAfterLast('/')).substringAfter(':'))
            folderError?.let { NoticeCard(it, isError = true) }
            if (folder.isNotBlank()) BodyText("Games in subfolders are included. New games can be added to this folder later.")
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
                if (emulator == null || emulator == RomLauncher.DUCKSTATION_PACKAGE) add(RomLaunchMode.DUCKSTATION to "DuckStation direct launch")
            }, method, { method = it }, supporting = "Emulators support different formats and ways of opening games. Try Ask which app to use if the default does not work.")
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
            }
            ActionRow("rom:help", "How to add games and box art", { dialog = "help" }, icon = Icons.Rounded.Info)
        }
    }
}

private val emulatorName = Regex("duckstation|retroarch|ppsspp|dolphin|aethersx2|nethersx2|epsxe|fpse|melon(ds)?|desmume|dra(stic)?|citra|lime3ds|azahar|mupen|n64|snes9x|nes\\.emu|gba\\.emu|gbc\\.emu|pizzaboy|pizza boy|mgba|lemuroid|redream|flycast|reicast|yabasanshiro|yaba sanshiro|saturn\\.emu|scummvm|dosbox|emu\\.project", RegexOption.IGNORE_CASE)
