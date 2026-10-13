package dev.pluto.launcher.ui.home

import androidx.compose.ui.res.stringResource
import dev.pluto.launcher.R
import dev.pluto.launcher.domain.SearchSection
import dev.pluto.launcher.ui.UiText
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.domain.RicherSearch
import dev.pluto.launcher.domain.SearchResult
import dev.pluto.launcher.domain.SearchShortcut
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.RomLibrary
import dev.pluto.launcher.ui.components.AppIcon
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.console.ConsoleArt
import dev.pluto.launcher.ui.console.ConsoleArtCache
import dev.pluto.launcher.ui.console.ConsoleEntry
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.motion.LocalOriginRegistry
import dev.pluto.launcher.ui.motion.reportOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SearchBatch(val query: String, val category: Long?, val apps: List<AppEntry>,
    val libraries: List<RomLibrary>, val shortcuts: List<SearchShortcut>, val results: List<SearchResult>)

private data class SearchIndex(val apps: List<AppEntry>, val libraries: List<RomLibrary>,
    val shortcuts: List<SearchShortcut>, val index: RicherSearch.Index)

/** Source indexing and query work stay off the UI thread; cancelled work cannot publish stale results. */
@Composable
internal fun rememberRichSearch(query: String, category: Long?, apps: List<AppEntry>, libraries: List<RomLibrary>,
    shortcuts: List<SearchShortcut>): State<SearchBatch?> {
    // Keep source preparation separate from query changes, including rapid typing while indexing.
    val searching = query.isNotBlank()
    val source by produceState<SearchIndex?>(null, apps, libraries, shortcuts, searching) {
        // Opening/browsing the drawer should not prepare a potentially large ROM index.
        if (searching && (value?.apps != apps || value?.libraries != libraries || value?.shortcuts != shortcuts)) {
            value = withContext(Dispatchers.Default) { SearchIndex(apps, libraries, shortcuts, RicherSearch.Index(apps, libraries, shortcuts)) }
        }
    }
    return produceState<SearchBatch?>(null, query, category, source) {
        val snapshot = source
        value = if (query.isBlank() || snapshot == null) null else withContext(Dispatchers.Default) {
            SearchBatch(query, category, snapshot.apps, snapshot.libraries, snapshot.shortcuts, snapshot.index.results(query))
        }
    }
}

/** One logical grid row per result, including its section label, so off-screen focus uses exact item indices. */
@Composable
internal fun RichSearchResults(results: List<SearchResult>, grid: LazyGridState, compact: Boolean,
    emulatorName: (String?) -> String?, onActivate: (SearchResult) -> Unit, onActions: (SearchResult) -> Unit,
    onFocused: (SearchResult) -> Unit) {
    PrewarmGridIcons(grid, results, if (compact) 36.dp else 44.dp) { result ->
        when (result) {
            is SearchResult.App -> listOf(result.entry.key)
            is SearchResult.Shortcut -> listOf(result.shortcut.app.key)
            is SearchResult.Game -> emptyList()
        }
    }
    val origins = LocalOriginRegistry.current
    LazyVerticalGrid(columns = GridCells.Fixed(1), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(results, key = { _, result -> result.key }, contentType = { _, result -> result.section }) { index, result ->
            Column {
                if (index == 0 || results[index - 1].section != result.section) {
                    Text(sectionTitle(result.section), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = if (index == 0) 0.dp else 8.dp, bottom = 4.dp))
                }
                val shape = RoundedCornerShape(20.dp)
                Row(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f), shape)
                    .reportOrigin(origins, result.key)
                    .controllerFocusable(id = result.key, onActivate = { onActivate(result) },
                        onSecondary = { onActions(result) }, secondaryLabel = stringResource(R.string.home_search_result_options, result.label),
                        contentDescription = resultDescription(result), shape = shape, onFocused = { onFocused(result) })
                    .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (result) {
                        is SearchResult.App -> AppIcon(result.entry, if (compact) 36.dp else 44.dp)
                        is SearchResult.Shortcut -> AppIcon(result.shortcut.app, if (compact) 36.dp else 44.dp)
                        is SearchResult.Game -> GameSearchIcon(result, compact)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(result.label, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = when (result) {
                            is SearchResult.App -> stringResource(R.string.home_search_result_app_detail)
                            is SearchResult.Shortcut -> stringResource(R.string.home_search_result_shortcut_detail, result.shortcut.app.label)
                            is SearchResult.Game -> listOfNotNull(result.library.name, emulatorName(result.library.emulatorPackage)).joinToString(" · ")
                        }
                        Text(detail, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

internal fun resultDescription(result: SearchResult): String = when (result) {
    is SearchResult.App -> UiText.get(R.string.home_search_result_app, result.label)
    is SearchResult.Game -> UiText.get(R.string.home_search_result_game, result.label, result.library.name)
    is SearchResult.Shortcut -> UiText.get(R.string.home_search_result_shortcut, result.label, result.shortcut.app.label)
}

@Composable
private fun sectionTitle(section: SearchSection): String = stringResource(
    when (section) {
        SearchSection.APPS -> R.string.home_search_section_apps
        SearchSection.GAMES -> R.string.home_search_section_games
        SearchSection.SHORTCUTS -> R.string.home_search_section_shortcuts
    },
)

@Composable
private fun GameSearchIcon(result: SearchResult.Game, compact: Boolean) {
    val icons = LocalIconCache.current
    val context = LocalContext.current
    val side = if (compact) 36.dp else 44.dp
    val px = with(LocalDensity.current) { side.roundToPx() }
    val entry = remember(result.library.id, result.game) { ConsoleEntry.Rom(result.library.id, result.game) }
    val art by produceState<ConsoleArt?>(ConsoleArtCache.peek(entry, px, 0), entry, px) {
        value = ConsoleArtCache.load(context, icons, entry, px, 0)
    }
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        art?.let { Image(it.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}
