package dev.pluto.launcher.ui.overlay

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.domain.AppSuggestions
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.components.AppIcon
import dev.pluto.launcher.ui.focus.controllerFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PickerTileShape = RoundedCornerShape(18.dp)

/**
 * Chooses which apps belong to one category, as a grid that works the same with touch and a
 * controller: A (or a tap) ticks or unticks the focused app, B / Done closes. Likely apps
 * (see [AppSuggestions]) are listed first under "Suggested"; every change is saved at once.
 */
@Composable
fun ShelfPickerScreen(categoryId: Long, state: LauncherUiState, vm: LauncherViewModel) {
    val category = state.categories.firstOrNull { it.id == categoryId }
    val isTop = state.session.topLayer == Layer.ShelfPicker(categoryId)
    val members = state.categoryMembers[categoryId].orEmpty()
    val apps = state.apps

    // Android's own app categories, read once off the main thread.
    val context = LocalContext.current
    // Null until computed: the grid waits for it (a few ms), otherwise its scroll position
    // anchors to the first app and the Suggested section lands above the viewport.
    val loaded by produceState<List<AppEntry>?>(null, category?.builtIn, apps) {
        val pm = context.packageManager
        value = withContext(Dispatchers.IO) {
            val cache = HashMap<String, AppSuggestions.Traits?>()
            AppSuggestions.suggest(category?.builtIn, apps) { pkg ->
                cache.getOrPut(pkg) {
                    runCatching {
                        val info = pm.getApplicationInfo(pkg, 0)
                        @Suppress("DEPRECATION")
                        AppSuggestions.Traits(info.category, info.flags and ApplicationInfo.FLAG_IS_GAME != 0)
                    }.getOrNull()
                }
            }
        }
    }
    val suggested = loaded.orEmpty()
    val suggestedKeys = remember(suggested) { suggested.map { it.key }.toSet() }
    val others = remember(apps, suggestedKeys) { apps.filter { it.key !in suggestedKeys } }
    val firstId = (suggested.firstOrNull() ?: others.firstOrNull())?.let { pickerId(it.key) } ?: "picker:close"
    rememberScreenFocus(firstId, refocusKey = suggested.isNotEmpty())

    val name = category?.name ?: "category"
    LayerScaffold(
        title = if (members.isEmpty()) "Add apps to $name" else "Apps in $name",
        idPrefix = "picker",
        onClose = { vm.back() },
        trapFocus = isTop,
        maxWidth = 960.dp,
        scrollable = false,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${members.size} selected · tap or press A to add or remove",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            PlutoButton("picker:done", OverlayText.DONE, { vm.back() }, style = ButtonStyle.FILLED)
        }
        if (category == null) {
            BodyText(OverlayText.APP_MISSING, Modifier.padding(20.dp))
            return@LayerScaffold
        }
        if (loaded == null) return@LayerScaffold
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (suggested.isNotEmpty()) {
                item(key = "h:suggested", span = { GridItemSpan(maxLineSpan) }) { PickerHeader("Suggested") }
                items(suggested, key = { "s:" + it.key.encode() }) { app ->
                    PickerTile(app, app.key in members) { vm.setCategoryMembership(categoryId, app.key, it) }
                }
                item(key = "h:others", span = { GridItemSpan(maxLineSpan) }) { PickerHeader("All apps") }
            }
            items(others, key = { "o:" + it.key.encode() }) { app ->
                PickerTile(app, app.key in members) { vm.setCategoryMembership(categoryId, app.key, it) }
            }
        }
    }
}

internal fun pickerId(key: AppKey) = "picker:app:${key.encode()}"

@Composable
private fun PickerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** One app: icon, name and a check badge; the whole tile toggles membership. */
@Composable
private fun PickerTile(app: AppEntry, selected: Boolean, onToggle: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent, PickerTileShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, PickerTileShape)
            .controllerFocusable(
                id = pickerId(app.key),
                onActivate = { onToggle(!selected) },
                contentDescription = "${app.label}, ${if (selected) "in this category" else "not in this category"}",
                shape = PickerTileShape,
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(app, 52.dp)
            Text(
                app.label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Shape and fill carry the state, not colour alone.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(if (selected) scheme.primary else Color.Transparent, CircleShape)
                .border(1.5.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}
