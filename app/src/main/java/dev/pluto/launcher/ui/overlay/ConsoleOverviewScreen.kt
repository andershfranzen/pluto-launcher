package dev.pluto.launcher.ui.overlay

import dev.pluto.launcher.ui.components.LocalNotificationApps
import dev.pluto.launcher.ui.components.hasDot
import dev.pluto.launcher.ui.components.notificationDot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.console.*
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.home.TrackFocusOrder
import dev.pluto.launcher.ui.home.TrackGridNavigation
import dev.pluto.launcher.ui.theme.ConsoleTheme

/** Select a shelf entry without launching it; Back returns to the existing carousel selection. */
@Composable
fun ConsoleOverviewScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val libraries by vm.romLibraries.collectAsState()
    val shelfId = vm.activeShelf(state)
    val library = (shelfId as? ShelfId.OfRomLibrary)?.let { id -> libraries.firstOrNull { it.id == id.libraryId } }
    val shelf = ConsoleShelves.shelf(shelfId, state.categories, state.categoryMembers, state.apps,
        ConsoleShelves.favouriteApps(state.homeTiles), state.recents)
    val entries = remember(shelf.apps, library) {
        library?.games?.map { ConsoleEntry.Rom(library.id, it) } ?: shelf.apps.map(ConsoleEntry::App)
    }
    val selectedKey = entries.firstOrNull { entry -> when (entry) {
        is ConsoleEntry.App -> entry.entry.key == vm.shelfSelection(shelfId)
        is ConsoleEntry.Rom -> entry.game.uri == vm.romSelection(entry.libraryId)
    } }?.key

    ConsoleTheme {
        LayerScaffold(title = "Browse ${library?.name ?: shelf.title}", idPrefix = "overview",
            onClose = { vm.back() }, trapFocus = state.session.topLayer == Layer.ConsoleOverview,
            maxWidth = 1040.dp, scrollable = false) {
            Text(if (entries.isEmpty()) "This shelf is empty." else "${entries.size} ${if (entries.size == 1) "item" else "items"} · Select to return to the carousel",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            ConsoleOverviewGrid(entries, selectedKey, Modifier.weight(1f)) { entry ->
                when (entry) {
                    is ConsoleEntry.App -> {
                        vm.rememberShelfSelection(shelfId, entry.entry.key)
                        vm.onAppSelected(entry.entry.key)
                    }
                    is ConsoleEntry.Rom -> vm.rememberRomSelection(entry.libraryId, entry.game.uri)
                }
                vm.back()
            }
        }
    }
}

/** One lazy grid shared by app and ROM shelves, with logical controller movement across off-screen rows. */
@Composable
internal fun ConsoleOverviewGrid(entries: List<ConsoleEntry>, selectedKey: String?, modifier: Modifier = Modifier,
    onSelect: (ConsoleEntry) -> Unit) {
    val selectedIndex = entries.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex)
    val ids = remember(entries) { entries.map { overviewId(it) } }
    TrackFocusOrder("console-overview", ids) { grid.scrollToItem(it) }
    TrackGridNavigation("console-overview", grid, ids)
    rememberScreenFocus(ids.getOrNull(selectedIndex) ?: "overview:close")

    LazyVerticalGrid(columns = GridCells.Adaptive(124.dp), state = grid,
        modifier = modifier, contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(entries, key = { it.key }) { entry ->
            OverviewTile(entry, entry.key == selectedKey) { onSelect(entry) }
        }
    }
}

private fun overviewId(entry: ConsoleEntry) = "overview:entry:${entry.key}"

@Composable
private fun OverviewTile(entry: ConsoleEntry, isSelected: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current
    val icons = LocalIconCache.current
    val versions by icons.versions.collectAsState()
    val version = versions[entry.packageName] ?: 0
    val px = with(LocalDensity.current) { 112.dp.roundToPx() }
    val art by produceState<ConsoleArt?>(ConsoleArtCache.peek(entry, px, version), entry, px, version) {
        value = ConsoleArtCache.load(context, icons, entry, px, version)
    }
    val hasNotifications = entry is ConsoleEntry.App && LocalNotificationApps.current.hasDot(entry.entry)
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth()
        .background(Color.White.copy(alpha = if (isSelected) 0.10f else 0.035f), shape)
        .border(1.dp, Color.White.copy(alpha = if (isSelected) 0.45f else 0.09f), shape)
        .controllerFocusable(id = overviewId(entry), onActivate = onSelect,
            contentDescription = "${entry.label}, select in carousel", shape = shape)
        .semantics { selected = isSelected }
        .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            art?.let { Image(it.icon, contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(if (entry is ConsoleEntry.App) 12.dp else 2.dp).notificationDot(hasNotifications), contentScale = ContentScale.Fit) }
        }
        Text(entry.label, style = MaterialTheme.typography.labelMedium, color = Color.White,
            textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
