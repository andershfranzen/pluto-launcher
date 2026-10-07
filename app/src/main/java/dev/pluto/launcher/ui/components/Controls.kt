package dev.pluto.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.ui.UserMessage
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.theme.PlutoDimens
import dev.pluto.launcher.ui.theme.overWallpaper
import kotlinx.coroutines.delay

/** Standard translucent panel used by sheets, drawers and menus. */
@Composable
fun PlutoPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PlutoDimens.PanelCorner),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PlutoDimens.PanelAlpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}

/**
 * Icon button with a screen-reader [label]; when [showLabel] is true the label is also
 * shown next to the icon (pill style). Always at least 48dp.
 */
@Composable
fun PlutoIconButton(
    id: String,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    onWallpaper: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    val shape = if (showLabel) RoundedCornerShape(24.dp) else CircleShape
    val background = if (onWallpaper) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    Row(
        modifier
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(id = id, onActivate = onClick, contentDescription = label, shape = shape, onFocused = onFocused)
            .clip(shape)
            .background(background)
            .padding(horizontal = if (showLabel) 14.dp else 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** Text button in Pluto style (tonal pill), controller-focusable. */
@Composable
fun PlutoTextButton(
    id: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    val container = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier
            .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(id = id, onActivate = onClick, contentDescription = text, shape = shape)
            .clip(shape)
            .background(container)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Section title, marked as a heading for screen readers. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, onWallpaper: Boolean = true) {
    val base = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    Text(
        text = text,
        style = if (onWallpaper) base.overWallpaper() else base,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

/** Small rounded chip showing a controller key name, e.g. "A" or "L1". */
@Composable
fun KeyChip(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
        )
    }
}

/**
 * Category tabs. Touch-selectable; L1/R1 switching is handled by the caller (VM).
 * The selected tab uses a filled shape plus an underline bar, not colour alone.
 * [prevKey] / [nextKey] optionally show the bound shoulder buttons at either end.
 */
@Composable
fun CategoryTabs(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit,
    idPrefix: String,
    modifier: Modifier = Modifier,
    prevKey: String? = null,
    nextKey: String? = null,
    onFocused: ((Category) -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (prevKey != null) {
            KeyChip(prevKey, Modifier.padding(horizontal = 4.dp))
        }
        Row(
            Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEach { category ->
                CategoryTab(
                    category = category,
                    isSelected = category.id == selected?.id,
                    id = "$idPrefix:${category.id}",
                    onClick = { onSelect(category) },
                    onFocused = onFocused?.let { { it(category) } },
                )
            }
        }
        if (nextKey != null) {
            KeyChip(nextKey, Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun CategoryTab(category: Category, isSelected: Boolean, id: String, onClick: () -> Unit, onFocused: (() -> Unit)?) {
    val shape = RoundedCornerShape(20.dp)
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
    }
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .defaultMinSize(minHeight = PlutoDimens.MinTouchTarget)
            .controllerFocusable(
                id = id,
                onActivate = onClick,
                contentDescription = "${category.name} category",
                shape = shape,
                onFocused = onFocused,
            )
            .semantics { selected = isSelected }
            .clip(shape)
            .background(container)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            category.name,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isSelected) {
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(contentColor),
            )
        }
    }
}

/**
 * Snackbar-style message with a controller-reachable Dismiss button. Auto-dismisses after
 * [timeoutMillis]; announced politely to screen readers.
 */
@Composable
fun MessageBar(
    message: UserMessage?,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
    timeoutMillis: Long = 6_000,
) {
    if (message == null) return
    LaunchedEffect(message.id) {
        delay(timeoutMillis)
        onDismiss(message.id)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .defaultMinSize(minWidth = PlutoDimens.MinTouchTarget, minHeight = PlutoDimens.MinTouchTarget)
                    .controllerFocusable(
                        id = "message:dismiss",
                        onActivate = { onDismiss(message.id) },
                        contentDescription = "Dismiss message",
                        shape = shape,
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Dismiss",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

/** Centred empty-state block: a title, optional detail and optional actions. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onWallpaper: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val titleStyle = MaterialTheme.typography.titleMedium
        Text(title, style = if (onWallpaper) titleStyle.overWallpaper() else titleStyle)
        if (detail != null) {
            val detailStyle = MaterialTheme.typography.bodyMedium
            Text(
                detail,
                style = if (onWallpaper) detailStyle.overWallpaper() else detailStyle,
                color = if (onWallpaper) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}
