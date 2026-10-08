package dev.pluto.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppTile
import dev.pluto.launcher.ui.components.ButtonLegend
import dev.pluto.launcher.ui.components.CategoryTabs
import dev.pluto.launcher.ui.components.EmptyState
import dev.pluto.launcher.ui.components.Legends
import dev.pluto.launcher.ui.components.PlutoIconButton
import dev.pluto.launcher.ui.components.PlutoPanel
import dev.pluto.launcher.ui.components.PlutoTextButton
import dev.pluto.launcher.ui.components.activeMapping
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.controllerFocusTarget

private const val SURFACE = "drawer"

/** Focus id of the drawer's search field; LauncherRoot focuses it for Y / Search. */
const val DRAWER_SEARCH_ID = "drawer:search"
private const val DR_CLOSE = "drawer:close"
private const val DR_CLEAR = "drawer:clear"
private const val CHIP_PREFIX = "drawer:cat"
private const val DR_ACTIONS = "drawer:actions"

/** Below this panel height, with the keyboard up, the drawer drops everything but search and results. */
private val CompactImeHeight = 300.dp

private fun drawerAppId(key: AppKey) = "drawer:${key.encode()}"

/**
 * The app drawer (layer Drawer): search field with Clear, category filter, and an
 * alphabetical grid of state.drawerApps (filtering and accent-insensitive matching are
 * done by the view model). Shows a useful empty state.
 *
 * Touch reaches App actions through the visible "Actions" toggle (tap it, then tap an
 * app) as well as by pressing and holding an app; controllers use X.
 * When the soft keyboard leaves little room (a phone in landscape), the category tabs and
 * legend step aside and tiles shrink so at least one row of results stays visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val focus = LocalControllerFocus.current
    val keyboard = LocalSoftwareKeyboardController.current
    val iconSize = state.iconSize()
    val session = state.session
    val gridKeys = state.drawerApps.map { drawerAppId(it.key) }
    val gridState = rememberAnchoredGridState(SURFACE, state, gridKeys)
    ReportScrollAnchor(SURFACE, gridState, state, vm, gridKeys)
    TrackFocusOrder(SURFACE, gridKeys) { gridState.scrollToItem(it) }
    var actionsMode by rememberSaveable { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    // Local field value keeps the cursor/selection stable; the VM holds the text itself.
    var field by remember { mutableStateOf(TextFieldValue(session.searchText, TextRange(session.searchText.length))) }
    // Last text we sent to the VM; only *other* changes (Clear, Back) overwrite the field, so
    // a state update arriving a frame late never rewinds fast typing.
    val lastSent = remember { arrayOf(session.searchText) }
    LaunchedEffect(session.searchText) {
        if (session.searchText != lastSent[0]) {
            lastSent[0] = session.searchText
            field = TextFieldValue(session.searchText, TextRange(session.searchText.length))
        }
    }

    val firstAppId = gridKeys.firstOrNull()
    val defaultId = if (session.searchActive) DRAWER_SEARCH_ID else firstAppId ?: DRAWER_SEARCH_ID
    SideEffect { focus.setDefaultFocus(defaultId) }

    // On open: Search (button or Y) focuses the field and raises the keyboard; a controller
    // otherwise lands on the first app.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        when {
            session.searchActive -> if (focus.requestFocusWhenReady(DRAWER_SEARCH_ID)) keyboard?.show()
            focus.inputMode == InputMode.CONTROLLER -> focus.requestFocusWhenReady(defaultId)
        }
    }

    // Back closed the search: drop the keyboard and move focus off the field.
    val currentFirstApp by rememberUpdatedState(firstAppId)
    LaunchedEffect(session.searchActive) {
        if (!session.searchActive) {
            keyboard?.hide()
            if (focus.focusedId == DRAWER_SEARCH_ID) {
                currentFirstApp?.let { focus.requestFocus(it) } ?: focus.requestFocus(DR_CLOSE)
            }
        }
    }

    // New search text: show the best matches from the top.
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(session.searchText) {
        if (firstRun[0]) {
            firstRun[0] = false
        } else if (state.drawerApps.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Keyboard up in a short window: keep only the search row and results.
        val compact = imeVisible && maxHeight < CompactImeHeight
        val gap = if (compact) 4.dp else 8.dp
        PlutoPanel(
            Modifier
                .fillMaxSize()
                .padding(gap),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = if (compact) 4.dp else 12.dp, top = if (compact) 2.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlutoIconButton(DR_CLOSE, Icons.AutoMirrored.Outlined.ArrowBack, "Close all apps", { vm.back() })
                    OutlinedTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            if (it.text != lastSent[0]) {
                                lastSent[0] = it.text
                                vm.setSearchText(it.text)
                            }
                        },
                        singleLine = true,
                        placeholder = { Text("Search apps") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = if (field.text.isNotEmpty()) {
                            {
                                PlutoIconButton(DR_CLEAR, Icons.Outlined.Clear, "Clear search", { vm.clearSearch() })
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            if (focus.inputMode == InputMode.CONTROLLER) firstAppId?.let(focus::requestFocus)
                        }),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .controllerFocusTarget(
                                id = DRAWER_SEARCH_ID,
                                onActivate = { keyboard?.show() },
                                onFocused = { if (!state.session.searchActive) vm.setSearchActive(true) },
                            ),
                    )
                    PlutoIconButton(
                        id = DR_ACTIONS,
                        icon = if (actionsMode) Icons.Outlined.Check else Icons.Outlined.MoreVert,
                        label = if (actionsMode) DrawerText.ACTIONS_DONE else DrawerText.ACTIONS,
                        onClick = { actionsMode = !actionsMode },
                        showLabel = !compact,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                if (actionsMode && !compact) {
                    Text(
                        DrawerText.ACTIONS_HINT,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (state.categories.size > 1 && !compact) {
                    CategoryTabs(
                        categories = state.categories,
                        selected = state.activeCategory,
                        onSelect = { vm.selectCategory(if (it.isAll) null else it.id) },
                        idPrefix = CHIP_PREFIX,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.drawerApps.isEmpty()) {
                        DrawerEmptyState(state, vm, Modifier.align(Alignment.Center))
                    } else {
                        val tileIcon = if (compact) iconSize * 0.75f else iconSize
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = tileIcon + 36.dp),
                            state = gridState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 4.dp else 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.drawerApps, key = { drawerAppId(it.key) }) { entry ->
                                val id = drawerAppId(entry.key)
                                val openActions = { vm.openLayer(Layer.AppActions(entry.key)) }
                                AppTile(
                                    entry = entry,
                                    iconSize = tileIcon,
                                    onLaunch = if (actionsMode) openActions else { { vm.launch(entry.key) } },
                                    onActions = openActions,
                                    actionsOnTap = actionsMode,
                                    // Keyboard-compact rows show icons only so no tile is cut off; names stay in semantics.
                                    showLabel = !compact,
                                    focusId = id,
                                    onFocused = {
                                        vm.onAppSelected(entry.key)
                                        vm.onControlFocused(id)
                                    },
                                )
                            }
                        }
                    }
                }

                if (showControllerHints(state, focus) && !compact) {
                    ButtonLegend(
                        state.activeMapping(),
                        Legends.Drawer,
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

/** Drawer copy (inline for 0.1). */
internal object DrawerText {
    const val ACTIONS = "Actions"
    const val ACTIONS_DONE = "Done"
    const val ACTIONS_HINT = "Tap an app to see its actions: pin, dock, folder, categories, hide, app info."
}

@Composable
private fun DrawerEmptyState(state: LauncherUiState, vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val query = state.session.searchText
    val category = state.activeCategory
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            query.isNotBlank() -> EmptyState(
                title = "No apps match “$query”",
                detail = if (category != null && !category.isAll) {
                    "Searching in ${category.name}. Try All apps or a shorter name."
                } else {
                    "Search looks at app names and package names."
                },
            ) {
                PlutoTextButton("drawer:empty:clear", "Clear search", { vm.clearSearch() }, emphasized = true)
                if (category != null && !category.isAll) {
                    PlutoTextButton("drawer:empty:all", "Search all apps", { vm.selectCategory(null) })
                }
            }
            category != null && !category.isAll -> EmptyState(
                title = "No apps in ${category.name} yet",
                detail = "Tap Actions, choose an app, then Categories… to add it here.",
            ) {
                PlutoTextButton("drawer:empty:all", "Show all apps", { vm.selectCategory(null) }, emphasized = true)
            }
            else -> EmptyState(
                title = "No apps to show",
                detail = "Hidden apps can be restored from Settings → Hidden apps.",
            ) {
                PlutoTextButton("drawer:empty:hidden", "Hidden apps", { vm.openLayer(Layer.HiddenApps) })
            }
        }
    }
}
