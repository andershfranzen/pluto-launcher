package dev.pluto.launcher.ui.overlay

import dev.pluto.launcher.ui.components.LocalNotificationApps
import dev.pluto.launcher.ui.components.hasDot
import dev.pluto.launcher.ui.components.notificationDot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.R
import dev.pluto.launcher.domain.ConsoleBrowse
import dev.pluto.launcher.domain.RomScanSummary
import dev.pluto.launcher.domain.RomSort
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
    val reports by vm.romReports.collectAsState()
    val shelfId = vm.activeShelf(state)
    val library = (shelfId as? ShelfId.OfRomLibrary)?.let { id -> libraries.firstOrNull { it.id == id.libraryId } }
    val shelf = ConsoleShelves.shelf(shelfId, state.categories, state.categoryMembers, state.apps,
        ConsoleShelves.favouriteApps(state.homeTiles), state.recents)
    // A shelf that spans systems (one /ROMs tree) is browsed a system at a time.
    val systems = library?.let { reports[it.id]?.systems }.orEmpty()
    val saves = library?.let { reports[it.id]?.saves }.orEmpty()
    val favourites = state.settings.romFavourites
    val played = state.settings.romLastPlayed
    var system by rememberSaveable(library?.id) { mutableStateOf<String?>(null) }
    var favouritesOnly by rememberSaveable(library?.id) { mutableStateOf(false) }
    val sort = state.settings.romSort
    val systemChips = remember(library?.games, systems) { library?.let { ConsoleBrowse.systems(it.games, systems) }.orEmpty() }
    LaunchedEffect(systemChips) { if (system != null && system !in systemChips) system = null }
    val entries = remember(shelf.apps, library, systems, system, favouritesOnly, favourites, sort, played) {
        val romLibrary = library
        if (romLibrary == null) {
            shelf.apps.map(ConsoleEntry::App)
        } else {
            ConsoleBrowse.sort(
                ConsoleBrowse.select(romLibrary.games, system, systems, favouritesOnly, favourites), sort, played,
            ).map { ConsoleEntry.Rom(romLibrary.id, it) }
        }
    }
    val selectedKey = entries.firstOrNull { entry -> when (entry) {
        is ConsoleEntry.App -> entry.entry.key == vm.shelfSelection(shelfId)
        is ConsoleEntry.Rom -> entry.game.uri == vm.romSelection(entry.libraryId)
    } }?.key
    val now = remember { System.currentTimeMillis() }

    ConsoleTheme {
        LayerScaffold(title = stringResource(R.string.console_browse_title, library?.name ?: shelf.title), idPrefix = "overview",
            onClose = { vm.back() }, trapFocus = state.session.topLayer == Layer.ConsoleOverview,
            maxWidth = 1040.dp, scrollable = false,
            headerContent = {
                if (library != null) RomBrowseControls(systemChips, system, { system = it },
                    favouritesOnly, { favouritesOnly = it }, sort, vm::setRomSort)
            }) {
            Text(
                when {
                    entries.isEmpty() && library != null -> stringResource(R.string.console_browse_no_match)
                    entries.isEmpty() -> stringResource(R.string.console_browse_empty)
                    library != null && entries.size != library.games.size -> pluralStringResource(
                        R.plurals.console_browse_filtered_games, library.games.size, entries.size, library.games.size)
                    else -> pluralStringResource(R.plurals.console_browse_items, entries.size, entries.size)
                },
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            ConsoleOverviewGrid(entries, selectedKey, Modifier.weight(1f), favourites, saves, played, now, vm::toggleRomFavourite) { entry ->
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

private const val FILTER_SURFACE = "console-overview-filters"

/**
 * The console browse grid's controls: system chips when a shelf spans several systems, a
 * Favourites-only filter, and how to order. One lazy row so many systems stay reachable by
 * D-pad; every chip is a focusable control with a visible selected state.
 */
@Composable
private fun RomBrowseControls(
    systems: List<String>,
    system: String?,
    onSystem: (String?) -> Unit,
    favouritesOnly: Boolean,
    onFavouritesOnly: (Boolean) -> Unit,
    sort: RomSort,
    onSort: (RomSort) -> Unit,
) {
    val row = rememberLazyListState()
    val chips = buildList {
        if (systems.isNotEmpty()) {
            add(Chip("all", stringResource(R.string.console_chip_all_systems), null, system == null) { onSystem(null) })
            systems.forEach { add(Chip("sys:$it", romSystemLabel(it), null, system == it) { onSystem(it) }) }
        }
        add(Chip("fav", stringResource(R.string.console_chip_favourites), Icons.Rounded.Star, favouritesOnly) { onFavouritesOnly(!favouritesOnly) })
        add(Chip("sort:${RomSort.TITLE}", stringResource(R.string.console_sort_title), null, sort == RomSort.TITLE) { onSort(RomSort.TITLE) })
        add(Chip("sort:${RomSort.RECENT}", stringResource(R.string.console_sort_recent), null, sort == RomSort.RECENT) { onSort(RomSort.RECENT) })
        add(Chip("sort:${RomSort.FILENAME}", stringResource(R.string.console_sort_file_name), null, sort == RomSort.FILENAME) { onSort(RomSort.FILENAME) })
    }
    val ids = remember(chips) { chips.map { "$FILTER_SURFACE:${it.key}" } }
    TrackFocusOrder(FILTER_SURFACE, ids) { row.animateScrollToItem(it) }
    LazyRow(
        state = row, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.key }) { chip ->
            BrowseChip("$FILTER_SURFACE:${chip.key}", chip.label, chip.icon, chip.selected) { chip.onClick() }
        }
    }
}

private class Chip(
    val key: String,
    val label: String,
    val icon: ImageVector?,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun BrowseChip(id: String, label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .controllerFocusable(id = id, onActivate = onClick, shape = shape,
                contentDescription = if (selected) stringResource(R.string.console_chip_selected, label) else label)
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0.06f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.6f else 0.12f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

/** One lazy grid shared by app and ROM shelves, with logical controller movement across off-screen rows. */
@Composable
internal fun ConsoleOverviewGrid(
    entries: List<ConsoleEntry>,
    selectedKey: String?,
    modifier: Modifier = Modifier,
    favourites: Set<String> = emptySet(),
    saves: Map<String, Long> = emptyMap(),
    played: Map<String, Long> = emptyMap(),
    now: Long = 0L,
    onFavourite: (String) -> Unit = {},
    onSelect: (ConsoleEntry) -> Unit,
) {
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
            OverviewTile(entry, entry.key == selectedKey, favourites, saves, played, now, onFavourite) { onSelect(entry) }
        }
    }
}

private fun overviewId(entry: ConsoleEntry) = "overview:entry:${entry.key}"

@Composable
private fun OverviewTile(
    entry: ConsoleEntry,
    isSelected: Boolean,
    favourites: Set<String>,
    saves: Map<String, Long>,
    played: Map<String, Long>,
    now: Long,
    onFavourite: (String) -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val icons = LocalIconCache.current
    val versions by icons.versions.collectAsState()
    val version = versions[entry.packageName] ?: 0
    val px = with(LocalDensity.current) { 112.dp.roundToPx() }
    val art by produceState<ConsoleArt?>(ConsoleArtCache.peek(entry, px, version), entry, px, version) {
        value = ConsoleArtCache.load(context, icons, entry, px, version)
    }
    val hasNotifications = entry is ConsoleEntry.App && LocalNotificationApps.current.hasDot(entry.entry)
    val rom = entry as? ConsoleEntry.Rom
    val isFavourite = rom != null && rom.game.uri in favourites
    val savedAt = rom?.let { saves[it.game.uri] }
    val playedAt = rom?.let { played[it.game.uri] }
    val playedAge = playedAt?.let { romAgeText(RomScanSummary.age(now, it)) }
    val savedAge = savedAt?.let { romAgeText(RomScanSummary.age(now, it)) }
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth()
        .background(Color.White.copy(alpha = if (isSelected) 0.10f else 0.035f), shape)
        .border(1.dp, Color.White.copy(alpha = if (isSelected) 0.45f else 0.09f), shape)
        .controllerFocusable(id = overviewId(entry), onActivate = onSelect,
            onSecondary = rom?.let { { onFavourite(it.game.uri) } },
            secondaryLabel = stringResource(if (isFavourite) R.string.console_remove_favourite else R.string.console_add_favourite),
            contentDescription = listOfNotNull(
                stringResource(R.string.console_tile_select, entry.label),
                stringResource(R.string.console_tile_favourite).takeIf { isFavourite },
                playedAge?.let { stringResource(R.string.console_tile_played, it) },
                savedAge?.let { stringResource(R.string.console_tile_save_data, it) },
            ).joinToString(stringResource(R.string.console_list_separator)),
            shape = shape)
        .semantics { selected = isSelected }
        .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            art?.let { Image(it.icon, contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(if (entry is ConsoleEntry.App) 12.dp else 2.dp).notificationDot(hasNotifications), contentScale = ContentScale.Fit) }
            if (isFavourite) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = ChipAccent,
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp))
            }
            // One pill answers "when did this game last move?": what Pluto saw opened, or
            // failing that the save data's own date, which is the only date it has.
            when {
                playedAge != null -> StateBadge(Icons.Rounded.History, stringResource(R.string.console_badge_played, playedAge),
                    Modifier.align(Alignment.BottomStart))
                savedAge != null -> StateBadge(Icons.Rounded.Save, stringResource(R.string.console_badge_save, savedAge),
                    Modifier.align(Alignment.BottomStart))
            }
        }
        Text(entry.label, style = MaterialTheme.typography.labelMedium, color = Color.White,
            textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

private val ChipAccent = Color(0xFFFFD54F)

/** A small "Save · 2 days ago" or "Played 2 days ago" pill over a game. */
@Composable
private fun StateBadge(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}
