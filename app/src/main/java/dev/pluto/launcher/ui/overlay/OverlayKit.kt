package dev.pluto.launcher.ui.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.InputMode
import dev.pluto.launcher.ui.focus.LocalControllerFocus
import dev.pluto.launcher.ui.focus.controllerFocusable
import kotlin.math.roundToInt

/*
 * Small building blocks shared by the overlay and settings screens. Every interactive
 * element goes through Modifier.controllerFocusable so touch, controller Confirm and
 * keyboard Enter behave identically. Nothing here uses platform Dialog windows: those
 * would take key events away from the Activity and therefore from the controller router.
 */

internal val PanelShape = RoundedCornerShape(24.dp)
internal val ControlShape = RoundedCornerShape(16.dp)
private const val PANEL_ALPHA = 0.92f
private const val MODAL_SCRIM_ALPHA = 0.4f

// --- Focus helpers ----------------------------------------------------------------

/**
 * Keeps controller focus inside a layer or dialog: spatial moves that would leave the
 * group are cancelled, so focus never wanders into the home screen drawn underneath.
 */
internal fun Modifier.focusTrap(enabled: Boolean = true): Modifier =
    if (!enabled) this else focusProperties { onExit = { cancelFocusChange() } }.focusGroup()

/**
 * Per-screen focus bookkeeping. Registers [ScreenFocus.defaultId] with the focus
 * controller and, in controller mode, moves focus there when the screen (or step)
 * appears. Nested dialogs call [returnFrom] when they close so focus goes back to the
 * control that opened them.
 */
@Stable
internal class ScreenFocus(private val controller: ControllerFocusController, defaultId: String) {
    private data class Request(val id: String, val seq: Int)

    var defaultId: String = defaultId
        internal set
    private var request by mutableStateOf<Request?>(null)
    private var seq = 0

    /** Focuses [id] (default: the screen's first control) once it is composed; controller mode only. */
    fun focus(id: String? = null) {
        request = Request(id ?: defaultId, ++seq)
    }

    /** A nested dialog closed: restore this screen's default and return focus to [openerId]. */
    fun returnFrom(openerId: String?) {
        controller.setDefaultFocus(defaultId)
        focus(openerId)
    }

    @Composable
    internal fun Effects() {
        val pending = request
        LaunchedEffect(pending) {
            if (pending == null) return@LaunchedEffect
            if (controller.inputMode == InputMode.CONTROLLER) {
                // The target may compose a frame or two later (e.g. after a state round-trip).
                repeat(FOCUS_RETRY_FRAMES) {
                    withFrameNanos { }
                    if (controller.requestFocus(pending.id)) return@LaunchedEffect
                }
            }
        }
    }

    private companion object {
        const val FOCUS_RETRY_FRAMES = 8
    }
}

/**
 * [defaultId] may change as content changes (it is re-registered silently); focus is only
 * moved when the screen first appears or when [refocusKey] changes (e.g. a wizard step).
 */
@Composable
internal fun rememberScreenFocus(defaultId: String, refocusKey: Any? = Unit): ScreenFocus {
    val controller = LocalControllerFocus.current
    val screenFocus = remember(controller) { ScreenFocus(controller, defaultId) }
    LaunchedEffect(screenFocus, defaultId) {
        screenFocus.defaultId = defaultId
        controller.setDefaultFocus(defaultId)
    }
    LaunchedEffect(screenFocus, refocusKey) {
        screenFocus.focus()
    }
    screenFocus.Effects()
    return screenFocus
}

// --- Surfaces -------------------------------------------------------------------

@Composable
private fun panelColor(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PANEL_ALPHA)

/** Dims what is below and swallows touches so nothing underneath can be activated. */
@Composable
private fun ModalScrim(onTap: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = MODAL_SCRIM_ALPHA))
            .pointerInput(onTap) { detectTapGestures { onTap?.invoke() } },
    )
}

@Composable
private fun PanelHeader(title: String, closeId: String, onClose: () -> Unit, leading: (@Composable () -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { heading() },
        )
        IconAction(id = closeId, icon = Icons.Rounded.Close, label = KitText.CLOSE, onClick = onClose)
    }
}

/**
 * A full-height layer (settings, edit, …): modal scrim plus a centred panel with a title
 * bar and Close button. [overlay] hosts nested dialogs above the panel.
 * The body scrolls vertically unless [scrollable] is false (for screens using lazy lists).
 */
@Composable
internal fun LayerScaffold(
    title: String,
    idPrefix: String,
    onClose: () -> Unit,
    trapFocus: Boolean = true,
    maxWidth: Dp = 720.dp,
    scrollable: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().focusTrap(trapFocus)) {
        ModalScrim(onTap = null)
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = PanelShape,
            color = panelColor(),
        ) {
            Column {
                PanelHeader(title, "$idPrefix:close", onClose, leading = null)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val body = Modifier.weight(1f).fillMaxWidth()
                if (scrollable) {
                    Column(
                        body
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        content = content,
                    )
                } else {
                    Column(body, content = content)
                }
            }
        }
        overlay()
    }
}

/**
 * A content-sized modal panel (sheet / dialog). When [interceptBack] is true (nested
 * dialogs inside a layer) system Back closes just this dialog via [onDismiss].
 * Tapping the scrim dismisses too. [scrollable] = false lets the caller host a lazy list
 * with Modifier.weight(1f, fill = false).
 */
@Composable
internal fun ModalPanel(
    title: String,
    idPrefix: String,
    onDismiss: () -> Unit,
    trapFocus: Boolean = true,
    interceptBack: Boolean = false,
    maxWidth: Dp = 560.dp,
    scrollable: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (interceptBack) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().focusTrap(trapFocus)) {
        ModalScrim(onTap = onDismiss)
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            shape = PanelShape,
            color = panelColor(),
            tonalElevation = 2.dp,
        ) {
            Column {
                PanelHeader(title, "$idPrefix:close", onDismiss, leading)
                val body = Modifier.weight(1f, fill = false).fillMaxWidth()
                if (scrollable) {
                    Column(
                        body.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        content = content,
                    )
                } else {
                    Column(body, content = content)
                }
                if (actions != null) {
                    ButtonBar(Modifier.padding(16.dp), content = actions)
                } else {
                    Spacer(Modifier.size(12.dp))
                }
            }
        }
    }
}

/** Right-aligned buttons that wrap onto further lines at large text sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ButtonBar(
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.End),
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// --- Controls -------------------------------------------------------------------

internal enum class ButtonStyle { FILLED, TONAL, OUTLINED, TEXT }

@Composable
internal fun PlutoButton(
    id: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.TONAL,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (style) {
        ButtonStyle.FILLED -> if (destructive) colors.error to colors.onError else colors.primary to colors.onPrimary
        ButtonStyle.TONAL -> if (destructive) colors.errorContainer to colors.onErrorContainer
        else colors.secondaryContainer to colors.onSecondaryContainer
        ButtonStyle.OUTLINED, ButtonStyle.TEXT -> Color.Transparent to (if (destructive) colors.error else colors.primary)
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .controllerFocusable(
                id = id,
                onActivate = { if (enabled) onClick() },
                contentDescription = if (enabled) label else "$label (${KitText.UNAVAILABLE})",
                shape = ControlShape,
            )
            .clip(ControlShape)
            .background(container)
            .then(
                if (style == ButtonStyle.OUTLINED) Modifier.border(BorderStroke(1.dp, colors.outline), ControlShape)
                else Modifier,
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = content, style = MaterialTheme.typography.labelLarge)
    }
}

/** 48dp square icon-only control. [label] is the screen-reader name. */
@Composable
internal fun IconAction(id: String, icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(48.dp)
            .controllerFocusable(
                id = id,
                onActivate = { if (enabled) onClick() },
                contentDescription = if (enabled) label else "$label (${KitText.UNAVAILABLE})",
                shape = ControlShape,
            )
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A full-width list row: optional leading icon or composable, a label with optional
 * supporting text, and an optional trailing composable (switch, checkbox…).
 */
@Composable
internal fun ActionRow(
    id: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean = false,
    contentDescription: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val description = contentDescription
        ?: listOfNotNull(label, supporting, if (!enabled) KitText.UNAVAILABLE else null).joinToString(", ")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .controllerFocusable(
                id = id,
                onActivate = { if (enabled) onClick() },
                contentDescription = description,
                shape = ControlShape,
                onFocused = onFocused,
            )
            .clip(ControlShape)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when {
            destructive -> colors.error
            selected -> colors.onSecondaryContainer
            else -> colors.onSurface
        }
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
internal fun SwitchRow(
    id: String,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    icon: ImageVector? = null,
) {
    ActionRow(
        id = id,
        label = label,
        supporting = supporting,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        contentDescription = listOfNotNull(label, if (checked) KitText.ON else KitText.OFF, supporting).joinToString(", "),
        trailing = {
            // Display only: the row is the single focusable/clickable control.
            Switch(checked = checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        },
    )
}

@Composable
internal fun CheckRow(
    id: String,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    ActionRow(
        id = id,
        label = label,
        supporting = supporting,
        leading = leading,
        onClick = { onCheckedChange(!checked) },
        contentDescription = listOfNotNull(label, if (checked) KitText.CHECKED else KitText.NOT_CHECKED, supporting)
            .joinToString(", "),
        trailing = { Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { }) },
    )
}

/**
 * Single-choice group rendered as wrapping chips. The selected chip is filled and
 * carries a check mark, so selection never relies on colour alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceGroup(
    idPrefix: String,
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    supporting: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.size(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .controllerFocusable(
                            id = "$idPrefix:$value",
                            onActivate = { onSelect(value) },
                            contentDescription = "$label: $text" + if (isSelected) ", ${KitText.SELECTED}" else "",
                            shape = ControlShape,
                        )
                        .clip(ControlShape)
                        .background(if (isSelected) colors.secondaryContainer else Color.Transparent)
                        .border(
                            BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.secondary else colors.outline),
                            ControlShape,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Slider replacement that works with D-pad LEFT/RIGHT: a "−" button, the value and a
 * "+" button. A null callback disables that end (value at its limit).
 */
@Composable
internal fun StepperRow(
    idPrefix: String,
    label: String,
    valueText: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    supporting: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        IconAction(
            id = "$idPrefix:dec",
            icon = Icons.Rounded.Remove,
            label = "${KitText.DECREASE} $label, $valueText",
            onClick = { onDecrease?.invoke() },
            enabled = onDecrease != null,
        )
        Text(
            valueText,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 64.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconAction(
            id = "$idPrefix:inc",
            icon = Icons.Rounded.Add,
            label = "${KitText.INCREASE} $label, $valueText",
            onClick = { onIncrease?.invoke() },
            enabled = onIncrease != null,
        )
    }
}

/** Steps [value] by [step] (direction ±1) inside [range], rounding away float drift. Null at the limit. */
internal fun stepped(value: Float, step: Float, range: ClosedFloatingPointRange<Float>, direction: Int): Float? {
    val next = (((value / step).roundToInt() + direction) * step).coerceIn(range.start, range.endInclusive)
    val rounded = (next * 1000f).roundToInt() / 1000f
    return if (kotlin.math.abs(rounded - value) < step / 4f) null else rounded
}

internal fun stepped(value: Int, step: Int, range: IntRange, direction: Int): Int? {
    val next = (value + direction * step).coerceIn(range.first, range.last)
    return if (next == value) null else next
}

internal fun percent(value: Float): String = "${(value * 100f).roundToInt()}%"

// --- Text -----------------------------------------------------------------------

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Highlighted informational box; announced politely when it appears. */
@Composable
internal fun NoticeCard(text: String, modifier: Modifier = Modifier, isError: Boolean = false, icon: ImageVector = Icons.Rounded.Info) {
    val colors = MaterialTheme.colorScheme
    val container = if (isError) colors.errorContainer else colors.tertiaryContainer
    val content = if (isError) colors.onErrorContainer else colors.onTertiaryContainer
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(ControlShape)
            .background(container)
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

// --- Standard dialogs -----------------------------------------------------------

/** Nested confirmation dialog. Default focus is Cancel, so a stray Confirm press is harmless. */
@Composable
internal fun ConfirmDialog(
    idPrefix: String,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    rememberScreenFocus("$idPrefix:cancel")
    ModalPanel(
        title = title,
        idPrefix = idPrefix,
        onDismiss = onDismiss,
        interceptBack = true,
        actions = {
            PlutoButton("$idPrefix:cancel", KitText.CANCEL, onDismiss, style = ButtonStyle.TEXT)
            PlutoButton(
                "$idPrefix:confirm",
                confirmLabel,
                onConfirm,
                style = ButtonStyle.FILLED,
                destructive = destructive,
            )
        },
    ) {
        BodyText(text)
    }
}

/**
 * Nested single-line text entry (folder / category names). In touch mode the field is
 * focused so the keyboard opens; in controller mode focus starts on Save and the
 * field is one move up (the system keyboard opens when it is activated).
 */
@Composable
internal fun TextInputDialog(
    idPrefix: String,
    title: String,
    fieldLabel: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val valid = text.isNotBlank()
    val submit = { if (valid) onConfirm(text.trim()) }
    val controller = LocalControllerFocus.current
    val fieldFocus = remember { FocusRequester() }
    rememberScreenFocus("$idPrefix:confirm")
    LaunchedEffect(Unit) {
        if (controller.inputMode == InputMode.TOUCH) {
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
        }
    }
    ModalPanel(
        title = title,
        idPrefix = idPrefix,
        onDismiss = onDismiss,
        interceptBack = true,
        actions = {
            PlutoButton("$idPrefix:cancel", KitText.CANCEL, onDismiss, style = ButtonStyle.TEXT)
            PlutoButton("$idPrefix:confirm", confirmLabel, submit, style = ButtonStyle.FILLED, enabled = valid)
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_NAME_LENGTH) },
            label = { Text(fieldLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .focusRequester(fieldFocus),
        )
    }
}

internal const val MAX_NAME_LENGTH = 40

/** Texts used by the shared building blocks. */
internal object KitText {
    const val CLOSE = "Close"
    const val CANCEL = "Cancel"
    const val DONE = "Done"
    const val ON = "on"
    const val OFF = "off"
    const val CHECKED = "checked"
    const val NOT_CHECKED = "not checked"
    const val SELECTED = "selected"
    const val UNAVAILABLE = "unavailable"
    const val DECREASE = "Decrease"
    const val INCREASE = "Increase"
}
