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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.R
import dev.pluto.launcher.domain.RomMetadata
import dev.pluto.launcher.domain.RomScanSummary
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.model.RomLibrary
import dev.pluto.launcher.model.RomLaunchMode
import dev.pluto.launcher.system.RomLauncher
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.console.ShelfId
import dev.pluto.launcher.ui.console.romAgeText
import dev.pluto.launcher.ui.console.romExtensionsText
import dev.pluto.launcher.ui.console.romSystemLabel
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
    val defaultTabName = stringResource(R.string.rom_default_tab_name)
    val folderAccessError = stringResource(R.string.rom_folder_access_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folder = uri.toString()
                if (name.isBlank()) name = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfter(':').substringAfterLast('/').trim().take(MAX_NAME_LENGTH).ifBlank { defaultTabName }
                folderError = null
            } catch (_: SecurityException) {
                folderError = folderAccessError
            }
        }
        screenFocus.focus("rom:folder")
    }
    BackHandler(editing != null && dialog == null) { close() }
    LayerScaffold(
        title = if (choosingEmulator) {
            stringResource(if (emulatorGame != null) R.string.rom_title_choose_game_emulator else R.string.rom_title_choose_default_emulator)
        } else stringResource(if (editing == 0L) R.string.rom_add_tab else if (editing != null) R.string.rom_title_edit else R.string.rom_title),
        idPrefix = "rom", onClose = { close() }, dialogOpen = dialog != null,
        compactHeader = compactSearch,
        contentKey = Pair(editing, choosingEmulator),
        headerContent = {
            if (choosingEmulator) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compactSearch) 2.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.rom_search_installed_apps)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    modifier = Modifier.weight(1f),
                )
                if (compactSearch) IconAction("rom:search:close", Icons.Rounded.Close, stringResource(R.string.rom_close_search), { close() })
            }
        },
        footerContent = {
            if (editing != null && !choosingEmulator) Column {
                HorizontalDivider()
                ButtonBar(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    PlutoButton("rom:save", stringResource(if (saving) R.string.rom_saving else if (editing == 0L) R.string.rom_create_tab else R.string.rom_save_changes), {
                        saving = true
                        val isNew = editing == 0L
                        vm.saveRomLibrary(RomLibrary(editing ?: 0L, name, folder, emulator, method), onFailure = { saving = false }) { id ->
                            saving = false
                            if (isNew) addedId = id
                            closeEditor()
                            vm.selectShelf(ShelfId.OfRomLibrary(id))
                        }
                    }, style = ButtonStyle.FILLED, enabled = name.isNotBlank() && folder.isNotBlank() && !saving)
                    PlutoButton("rom:cancel", stringResource(R.string.rom_cancel), { close() }, enabled = !saving)
                }
            }
        },
        overlay = {
            AnimatedDialog(dialog) { shown ->
                when (shown) {
                    "name" -> TextInputDialog("rom:name-dialog", stringResource(R.string.rom_name_dialog_title), stringResource(R.string.rom_name_dialog_label), name, stringResource(R.string.rom_done),
                        onConfirm = { name = it; dialog = null; keyboard?.hide(); screenFocus.returnFrom("rom:name") },
                        onDismiss = { dialog = null; keyboard?.hide(); screenFocus.returnFrom("rom:name") })
                    "discard" -> ConfirmDialog("rom:discard-dialog", stringResource(R.string.rom_discard_title), stringResource(R.string.rom_discard_message), stringResource(R.string.rom_discard),
                        onConfirm = { dialog = null; closeEditor() }, onDismiss = { dialog = null })
                    "delete" -> ConfirmDialog("rom:delete-dialog", stringResource(R.string.rom_remove_title, name), stringResource(R.string.rom_remove_message), stringResource(R.string.rom_remove),
                        onConfirm = { editing?.let(vm::deleteRomLibrary); dialog = null; closeEditor() },
                        onDismiss = { dialog = null; screenFocus.returnFrom("rom:delete") }, destructive = true)
                    "help" -> ModalPanel(stringResource(R.string.rom_help_title), "rom:help", { dialog = null }, interceptBack = true) {
                        SectionHeader(stringResource(R.string.rom_help_adding_header))
                        BodyText(stringResource(R.string.rom_help_adding_body))
                        SectionHeader(stringResource(R.string.rom_help_art_header))
                        BodyText(stringResource(R.string.rom_help_art_body))
                        BodyText(stringResource(R.string.rom_help_art_layouts))
                        SectionHeader(RomMetadata.GAMELIST) // A file name: not translated
                        BodyText(stringResource(R.string.rom_help_gamelist_body))
                        SectionHeader(stringResource(R.string.rom_help_emulator_header))
                        BodyText(stringResource(R.string.rom_help_emulator_body))
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
                    else ModalPanel(stringResource(R.string.rom_games_panel_title, library.name), "rom:games", { gamesPanel = null; gameQuery = "" }, interceptBack = true) {
                        BodyText(stringResource(R.string.rom_games_panel_intro))
                        if (library.games.isEmpty()) BodyText(stringResource(R.string.rom_games_panel_empty))
                        // A full set can hold thousands of games: rows are not lazy here, so only a
                        // bounded slice is built, with games that already have their own app first.
                        if (library.games.size > GAMES_PANEL_ROWS) OutlinedTextField(
                            value = gameQuery, onValueChange = { gameQuery = it }, label = { Text(stringResource(R.string.rom_find_game)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val matches = library.games.filter { gameQuery.isBlank() || it.title.contains(gameQuery.trim(), ignoreCase = true) }
                            .sortedBy { it.uri !in state.settings.romEmulators }
                        if (matches.size > GAMES_PANEL_ROWS) BodyText(pluralStringResource(R.plurals.rom_games_panel_truncated, matches.size, GAMES_PANEL_ROWS, matches.size))
                        else if (matches.isEmpty() && library.games.isNotEmpty()) BodyText(stringResource(R.string.rom_games_panel_no_match, gameQuery.trim()))
                        val now = System.currentTimeMillis()
                        matches.take(GAMES_PANEL_ROWS).forEach { game ->
                            val chosen = state.settings.romEmulators[game.uri]
                            val played = state.settings.romLastPlayed[game.uri]
                            ActionRow("rom:game:${game.uri}", game.title, { chooseEmulatorFor(game.uri) },
                                supporting = run {
                                    val opens = chosen?.let { stringResource(R.string.rom_game_opens_in, appLabel(it)) }
                                        ?: stringResource(R.string.rom_game_uses_default)
                                    played?.let { stringResource(R.string.rom_game_played, opens, romAgeText(RomScanSummary.age(now, it))) } ?: opens
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
            BodyText(if (forGame != null) stringResource(R.string.rom_chooser_game_intro, forGame.title)
            else stringResource(R.string.rom_chooser_tab_intro))
            ActionRow("rom:chooser", stringResource(if (forGame != null) R.string.rom_use_tab_default else R.string.rom_ask_each_time), { chooseEmulator(null) },
                supporting = stringResource(if (forGame != null) R.string.rom_use_tab_default_supporting else R.string.rom_ask_each_time_supporting),
                selected = chosen == null)
            val previous = libraries.mapNotNull { it.emulatorPackage }.toSet()
            val suggested = installedApps.filter { it.key.packageName in previous || it.key.packageName == chosen || emulatorName.containsMatchIn(it.label + " " + it.key.packageName) }
            val filtered = installedApps.filter { it.label.contains(query.trim(), true) || it.key.packageName.contains(query.trim(), true) }
            val visible = if (query.isNotBlank() || showAllApps) filtered else suggested
            SectionHeader(stringResource(if (query.isNotBlank()) R.string.rom_search_results else if (showAllApps) R.string.rom_installed_apps else R.string.rom_suggested_emulators))
            if (visible.isEmpty()) BodyText(if (query.isNotBlank()) stringResource(R.string.rom_no_apps_match, query) else stringResource(R.string.rom_no_suggestions))
            visible.forEach { app ->
                ActionRow("rom:emulator:${app.key.packageName}", app.label, { chooseEmulator(app.key.packageName) }, selected = chosen == app.key.packageName)
            }
            if (query.isBlank()) ActionRow("rom:all-apps", stringResource(if (showAllApps) R.string.rom_show_suggested else R.string.rom_show_all_apps), { showAllApps = !showAllApps }, supporting = stringResource(R.string.rom_show_all_apps_supporting))
        } else if (editing != null) {
            BodyText(stringResource(if (editing == 0L) R.string.rom_editor_intro_new else R.string.rom_editor_intro_edit))
            SectionHeader(stringResource(R.string.rom_section_folder))
            ActionRow("rom:folder", stringResource(if (folder.isBlank()) R.string.rom_choose_folder else R.string.rom_change_folder), { picker.launch(folder.takeIf(String::isNotEmpty)?.let(Uri::parse)) },
                icon = Icons.Rounded.Folder, supporting = if (folder.isEmpty()) stringResource(R.string.rom_choose_folder_supporting) else Uri.decode(folder.substringAfterLast('/')).substringAfter(':'))
            folderError?.let { NoticeCard(it, isError = true) }
            if (folder.isNotBlank()) BodyText(stringResource(R.string.rom_folder_subfolders))
            if (folder.isBlank()) {
                SectionHeader(stringResource(R.string.rom_suggestions_header))
                BodyText(stringResource(R.string.rom_suggestions_intro))
                suggestions.forEach { suggestion ->
                    ActionRow(
                        "rom:suggest:${suggestion.folder}",
                        suggestion.packageName?.let { stringResource(R.string.rom_suggestion_app, appLabel(it)) } ?: stringResource(R.string.rom_suggestion_folder, suggestion.folder),
                        { picker.launch(sharedStorageFolder(suggestion.folder)) },
                        supporting = stringResource(R.string.rom_suggestion_supporting, suggestion.folder),
                    )
                }
            }
            SectionHeader(stringResource(R.string.rom_section_emulator))
            ActionRow("rom:emulator", if (emulator == null) stringResource(R.string.rom_ask_each_time) else appLabel(emulator), {
                query = ""; showAllApps = false; choosingEmulator = true; screenFocus.focus("rom:chooser")
            }, icon = Icons.Rounded.SportsEsports, supporting = stringResource(if (emulator == null) R.string.rom_emulator_ask_supporting else R.string.rom_emulator_default_supporting))
            if (selectedApp != null) ActionRow("rom:setup-emulator", stringResource(R.string.rom_open_setup, selectedApp.label), { vm.launch(selectedApp.key) },
                supporting = stringResource(if (emulator == RomLauncher.DUCKSTATION_PACKAGE) R.string.rom_setup_duckstation else R.string.rom_setup_generic))
            else if (emulator != null) NoticeCard(stringResource(R.string.rom_emulator_missing), isError = true)
            SectionHeader(stringResource(R.string.rom_section_name))
            ActionRow("rom:name", name.ifBlank { stringResource(R.string.rom_name_placeholder) }, { dialog = "name" }, supporting = stringResource(R.string.rom_name_supporting))
            ActionRow("rom:advanced", stringResource(if (advanced) R.string.rom_hide_advanced else R.string.rom_advanced), { advanced = !advanced }, supporting = stringResource(R.string.rom_advanced_supporting))
            if (advanced) ChoiceGroup("rom:method", stringResource(R.string.rom_method_title), buildList {
                add(RomLaunchMode.AUTOMATIC to stringResource(R.string.rom_method_automatic))
                add(RomLaunchMode.OPEN_WITH to stringResource(R.string.rom_method_open_with))
                add(RomLaunchMode.VIEW to stringResource(R.string.rom_method_view))
                val adapter = RomLauncher.directMode(emulator)
                if (adapter != null) add(adapter to stringResource(R.string.rom_method_direct, appLabel(emulator)))
                else if (emulator == null) add(RomLaunchMode.DUCKSTATION to stringResource(R.string.rom_method_direct, "DuckStation"))
            }, method, { method = it }, supporting = stringResource(R.string.rom_method_supporting))
            ActionRow("rom:help", stringResource(R.string.rom_help_row_editor), { dialog = "help" }, icon = Icons.Rounded.Info)
            if (editing != 0L) ActionRow("rom:delete", stringResource(R.string.rom_remove_tab), { dialog = "delete" }, icon = Icons.Rounded.Delete, destructive = true)
        } else {
            addedId?.let { id -> libraries.firstOrNull { it.id == id }?.let { NoticeCard(stringResource(R.string.rom_added, it.name)) } }
            BodyText(stringResource(R.string.rom_list_intro))
            PlutoButton("rom:add", stringResource(R.string.rom_add_tab), { edit(null) }, icon = Icons.Rounded.Add, style = ButtonStyle.FILLED)
            libraries.forEach { library ->
                SectionHeader(library.name)
                val busy = library.id in scanning
                val problem = errors[library.id]
                BodyText(if (busy) stringResource(R.string.rom_finding_games) else pluralStringResource(R.plurals.rom_library_summary, library.games.size,
                    library.games.size, library.emulatorPackage?.let(::appLabel) ?: stringResource(R.string.rom_ask_each_time)))
                problem?.let { NoticeCard(it, isError = true) }
                if (!busy && problem == null && library.games.isEmpty()) BodyText(stringResource(R.string.rom_no_games_found))
                if (state.mode == LauncherMode.HANDHELD) ActionRow("rom:view:${library.id}", stringResource(R.string.rom_view_tab), { vm.selectShelf(ShelfId.OfRomLibrary(library.id)); vm.closeAllLayers() })
                ActionRow("rom:edit:${library.id}", stringResource(R.string.rom_edit_tab), { edit(library) }, supporting = stringResource(R.string.rom_edit_tab_supporting, library.name))
                ActionRow("rom:refresh:${library.id}", stringResource(if (busy) R.string.rom_finding_games else R.string.rom_refresh), { vm.refreshRomLibrary(library.id) }, icon = Icons.Rounded.Refresh, enabled = !busy)
                ActionRow("rom:games:${library.id}", stringResource(R.string.rom_games_and_emulators), { gamesPanel = library.id }, icon = Icons.Rounded.SportsEsports,
                    supporting = stringResource(R.string.rom_games_and_emulators_supporting))
                ActionRow("rom:diagnostics:${library.id}", stringResource(R.string.rom_diagnostics), { diagnostics = library.id }, icon = Icons.Rounded.Info,
                    supporting = reports[library.id]?.let {
                        stringResource(R.string.rom_diagnostics_last_check,
                            pluralStringResource(R.plurals.rom_count_files, it.documents, it.documents),
                            pluralStringResource(R.plurals.rom_count_games, it.games, it.games))
                    } ?: stringResource(R.string.rom_diagnostics_supporting))
            }
            ActionRow("rom:help", stringResource(R.string.rom_help_row), { dialog = "help" }, icon = Icons.Rounded.Info)
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
    ModalPanel(stringResource(R.string.rom_diag_title, library.name), "rom:diag", onClose, interceptBack = true) {
        SectionHeader(stringResource(R.string.rom_diag_folder))
        BodyText(Uri.decode(library.treeUri))
        BodyText(
            stringResource(if (readAccess) R.string.rom_diag_access_granted else R.string.rom_diag_access_missing)
        )
        if (!readAccess) NoticeCard(stringResource(R.string.rom_diag_access_lost), isError = true)
        SectionHeader(stringResource(R.string.rom_diag_last_check))
        if (report == null) {
            BodyText(stringResource(R.string.rom_diag_no_check))
        } else {
            BodyText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(report.at)))
            BodyText(stringResource(R.string.rom_diag_counts,
                pluralStringResource(R.plurals.rom_diag_files_checked, report.documents, report.documents),
                pluralStringResource(R.plurals.rom_count_games, report.games, report.games),
                pluralStringResource(R.plurals.rom_diag_with_art, report.gamesWithArt, report.gamesWithArt)))
            report.systems.values.distinct().sorted().takeIf { it.isNotEmpty() }?.let { systems ->
                val names = systems.map { romSystemLabel(it) }.joinToString(stringResource(R.string.console_list_separator))
                BodyText(stringResource(R.string.rom_diag_systems, names))
            }
            report.saves.size.takeIf { it > 0 }?.let {
                BodyText(pluralStringResource(R.plurals.rom_diag_saves, it, it))
            }
            BodyText(
                if (report.gamelistEntries > 0) pluralStringResource(R.plurals.rom_diag_gamelist, report.gamelistEntries, report.gamelistEntries)
                else stringResource(R.string.rom_diag_no_gamelist)
            )
        }
        SectionHeader(stringResource(R.string.rom_diag_skipped_header))
        BodyText(
            when {
                report == null -> stringResource(R.string.rom_diag_skipped_unchecked)
                report.skipped == 0 -> stringResource(R.string.rom_diag_skipped_none)
                else -> pluralStringResource(R.plurals.rom_diag_skipped, report.skipped, report.skipped, romExtensionsText(report.skippedExtensions))
            }
        )
        report?.takeIf { it.sheets > 0 }?.let { BodyText(pluralStringResource(R.plurals.rom_diag_sheets, it.sheets, it.sheets)) }
        report?.takeIf { it.missingTracks > 0 }?.let {
            NoticeCard(pluralStringResource(R.plurals.rom_diag_missing_tracks, it.missingTracks, it.missingTracks), isError = true)
        }
        if (report != null && report.games == 0) NoticeCard(stringResource(R.string.rom_diag_no_games), isError = true)
    }
}

/** Rows the Games panel builds at once; the rest are reached by searching. */
private const val GAMES_PANEL_ROWS = 60
