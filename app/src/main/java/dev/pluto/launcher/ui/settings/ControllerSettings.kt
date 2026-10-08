package dev.pluto.launcher.ui.settings

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.data.prefs.ButtonMapping
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.ControllerInfo
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.LocalControllerRouter
import dev.pluto.launcher.ui.overlay.ActionRow
import dev.pluto.launcher.ui.overlay.BodyText
import dev.pluto.launcher.ui.overlay.ButtonBar
import dev.pluto.launcher.ui.overlay.ButtonStyle
import dev.pluto.launcher.ui.overlay.ChoiceGroup
import dev.pluto.launcher.ui.overlay.KitText
import dev.pluto.launcher.ui.overlay.LayerScaffold
import dev.pluto.launcher.ui.overlay.ModalPanel
import dev.pluto.launcher.ui.overlay.NoticeCard
import dev.pluto.launcher.ui.overlay.PlutoButton
import dev.pluto.launcher.ui.overlay.SectionHeader
import dev.pluto.launcher.ui.overlay.StepperRow
import dev.pluto.launcher.ui.overlay.rememberScreenFocus
import dev.pluto.launcher.ui.overlay.stepped
import dev.pluto.launcher.ui.motion.PlutoMotion
import dev.pluto.launcher.ui.overlay.AnimatedDialog
import dev.pluto.launcher.ui.overlay.ExpandingSection
import dev.pluto.launcher.ui.overlay.isDialogExiting
import dev.pluto.launcher.ui.overlay.rememberLastNonNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.hypot

private const val CAPTURE_TIMEOUT_SECONDS = 10
private const val DEAD_ZONE_STEP = 0.05f
private const val REPEAT_DELAY_STEP = 50
private val REPEAT_DELAY_RANGE = 150..1000
private const val REPEAT_INTERVAL_STEP = 20
private val REPEAT_INTERVAL_RANGE = 40..500

/** Keys that can never become launcher actions: movement and system-reserved buttons. */
private val REFUSED_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_DPAD_UP_LEFT,
    KeyEvent.KEYCODE_DPAD_UP_RIGHT,
    KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
    KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
)

/** Never consumed or captured, so Android keeps handling them (Home, Recents, controller Guide/Home). */
private val SYSTEM_KEYS = ButtonMapping.SYSTEM_RESERVED_KEYS

/** Non-gamepad keys in the defaults (system Back, Menu) that a remap keeps for its action. */
private val PRESERVED_KEYS = setOf(ButtonMapping.KEYCODE_BACK, ButtonMapping.KEYCODE_MENU)

/**
 * Binds [keyCode] as the action's controller button, unbinding it from any other action,
 * while keeping system Back / Menu bound so non-gamepad navigation still works.
 */
private fun remap(base: ButtonMapping, action: ControllerAction, keyCode: Int): ButtonMapping =
    base.keysFor(action)
        .filter { it in PRESERVED_KEYS && it != keyCode }
        .fold(base.replace(action, keyCode)) { mapping, kept -> mapping.rebind(action, kept) }

private fun ControllerInfo.mappingKey() = LauncherSettings.controllerMappingKey(descriptor, vendorId, productId)

/** Live diagnostics values. Read only inside [DiagnosticsPanel] so motion events recompose just that part. */
@Stable
private class Diagnostics {
    var lastKey by mutableStateOf<String?>(null)
    /** Bumped on every key press, so the last-key chip pulses even when the same key repeats. */
    var keyPresses by mutableIntStateOf(0)
    var axes by mutableStateOf<String?>(null)
    /** Coarse stick summary for screen readers; changes only on dead-zone or direction changes. */
    var stickSummary by mutableStateOf<String?>(null)
    /** Raw stick positions (-1..1); read only while drawing the stick pads. */
    var leftX by mutableFloatStateOf(0f)
    var leftY by mutableFloatStateOf(0f)
    var rightX by mutableFloatStateOf(0f)
    var rightY by mutableFloatStateOf(0f)
    var hasMotion by mutableStateOf(false)
}

/** "centred", "up", "down-left", ... for a stick position, given the dead zone. */
internal fun stickDirection(x: Float, y: Float, deadZone: Float): String {
    if (hypot(x, y) < deadZone) return "centred"
    val half = deadZone / 2f
    val vertical = when {
        y <= -half -> "up"
        y >= half -> "down"
        else -> null
    }
    val horizontal = when {
        x <= -half -> "left"
        x >= half -> "right"
        else -> null
    }
    return listOfNotNull(vertical, horizontal).joinToString("-")
}

/** Connected controllers, live input diagnostics, dead zone/repeat tuning, button remapping. */
@Composable
fun ControllerSettingsScreen(state: LauncherUiState, vm: LauncherViewModel) {
    val router = LocalControllerRouter.current
    val settings = state.settings
    val controllers = state.controllers
    val isTop = state.session.topLayer == Layer.ControllerSettings

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val controller = controllers.getOrNull(selectedIndex) ?: controllers.firstOrNull()
    var applyToThis by rememberSaveable { mutableStateOf(true) }
    val scopeThis = controller != null && applyToThis
    val mapping = if (scopeThis) {
        settings.mappingFor(controller.descriptor, controller.vendorId, controller.productId)
    } else {
        settings.defaultMapping
    }
    val hasOverride = controller != null && (
        settings.controllerMappings.containsKey(controller.mappingKey()) ||
            settings.controllerMappings.containsKey(LauncherSettings.vendorProductKey(controller.vendorId, controller.productId))
        )

    val diagnostics = remember { Diagnostics() }
    var capturing by remember { mutableStateOf<ControllerAction?>(null) }
    var secondsLeft by remember { mutableIntStateOf(CAPTURE_TIMEOUT_SECONDS) }
    var captureRefused by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val screenFocus = rememberScreenFocus(
        if (controllers.isNotEmpty()) "controller:device:0" else "controller:remap:${ControllerAction.CONFIRM}",
    )

    fun endCapture(message: String?, action: ControllerAction?) {
        capturing = null
        captureRefused = false
        notice = message
        screenFocus.returnFrom(action?.let { "controller:remap:$it" })
    }

    fun save(action: ControllerAction, keyCode: Int) {
        val previous = mapping.actionFor(keyCode)?.takeIf { it != action }
        val target = controller?.takeIf { scopeThis }
        vm.updateSettings { s ->
            if (target != null) {
                val base = s.mappingFor(target.descriptor, target.vendorId, target.productId)
                s.copy(controllerMappings = s.controllerMappings + (target.mappingKey() to remap(base, action, keyCode)))
            } else {
                s.copy(defaultMapping = remap(s.defaultMapping, action, keyCode))
            }
        }
        endCapture(
            SettingsText.captured(keyLabel(keyCode), SettingsText.actionLabel(action), previous?.let(SettingsText::actionLabel)),
            action,
        )
    }

    // Raw key hook: always records the last key; consumes only while capturing a remap.
    val onRawKey by rememberUpdatedState { event: KeyEvent ->
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN) {
            val device = event.device?.name?.let { " · $it" }.orEmpty()
            diagnostics.lastKey = "${keyLabel(code)} (${KeyEvent.keyCodeToString(code)})$device"
            if (event.repeatCount == 0) diagnostics.keyPresses++
        }
        val action = capturing
        when {
            action == null || code in SYSTEM_KEYS -> false
            event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0 -> true
            code in REFUSED_KEYS -> {
                captureRefused = true
                true
            }
            else -> {
                save(action, code)
                true
            }
        }
    }
    val onRawMotion by rememberUpdatedState { event: MotionEvent ->
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val inDeadZone = hypot(x, y) < settings.stickDeadZone
        val summary = "Left stick ${stickDirection(x, y, settings.stickDeadZone)}, right stick ${stickDirection(rx, ry, settings.stickDeadZone)}"
        if (summary != diagnostics.stickSummary) diagnostics.stickSummary = summary
        diagnostics.leftX = x
        diagnostics.leftY = y
        diagnostics.rightX = rx
        diagnostics.rightY = ry
        if (!diagnostics.hasMotion) diagnostics.hasMotion = true
        diagnostics.axes = "Left stick %+.2f, %+.2f%s\nRight stick %+.2f, %+.2f\nD-pad (hat) %+.0f, %+.0f".format(
            x, y, if (inDeadZone) " (dead zone)" else "", rx, ry, hatX, hatY,
        )
    }
    DisposableEffect(router) {
        val previousKey = router?.onRawKey
        val previousMotion = router?.onRawMotion
        router?.onRawKey = { event -> onRawKey(event) || (previousKey?.invoke(event) ?: false) }
        router?.onRawMotion = { event ->
            onRawMotion(event)
            previousMotion?.invoke(event)
        }
        onDispose {
            router?.onRawKey = previousKey
            router?.onRawMotion = previousMotion
        }
    }

    // Capture times out after 10 s so a controller-only user is never stuck.
    LaunchedEffect(capturing) {
        val action = capturing ?: return@LaunchedEffect
        secondsLeft = CAPTURE_TIMEOUT_SECONDS
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        endCapture(SettingsText.CAPTURE_TIMEOUT, action)
    }

    LayerScaffold(
        title = SettingsText.CONTROLLER_TITLE,
        idPrefix = "controller",
        onClose = { vm.back() },
        trapFocus = isTop,
        dialogOpen = capturing != null,
        overlay = {
            AnimatedDialog(capturing) { action ->
                CaptureDialog(
                    action = action,
                    secondsLeft = secondsLeft,
                    refused = captureRefused,
                    reducedMotion = settings.reducedMotion,
                    onCancel = { endCapture(SettingsText.CAPTURE_CANCELLED, action) },
                )
            }
        },
    ) {
        // --- Connected controllers ---
        SectionHeader(SettingsText.CONNECTED)
        if (controllers.isEmpty()) {
            NoticeCard(SettingsText.NO_CONTROLLER, icon = Icons.Rounded.SportsEsports)
        }
        controllers.forEachIndexed { index, info ->
            ActionRow(
                id = "controller:device:$index",
                label = info.name.ifBlank { SettingsText.CONTROLLER_TITLE },
                icon = Icons.Rounded.SportsEsports,
                supporting = SettingsText.ids(info.vendorId, info.productId) + " · " +
                    SettingsText.descriptor(info.descriptor.take(8).ifBlank { "–" }),
                selected = controllers.size > 1 && info == controller,
                onClick = { selectedIndex = index },
            )
        }

        // --- Diagnostics ---
        SectionHeader(SettingsText.DIAGNOSTICS)
        if (router == null) {
            BodyText(SettingsText.DIAGNOSTICS_UNAVAILABLE)
        } else {
            BodyText(SettingsText.DIAGNOSTICS_HELP)
            DiagnosticsPanel(diagnostics, settings.stickDeadZone)
        }

        // --- Button mapping ---
        SectionHeader(SettingsText.MAPPING)
        BodyText(SettingsText.MAPPING_HELP)
        if (controller != null) {
            ChoiceGroup(
                idPrefix = "controller:scope",
                label = SettingsText.APPLY_TO,
                supporting = if (hasOverride) SettingsText.USING_OVERRIDE else SettingsText.USING_DEFAULT,
                options = listOf(true to SettingsText.THIS_CONTROLLER, false to SettingsText.ALL_CONTROLLERS),
                selected = applyToThis,
                onSelect = { applyToThis = it },
            )
        }
        val shownNotice = rememberLastNonNull(notice)
        ExpandingSection(visible = notice != null) {
            shownNotice?.let { NoticeCard(it, icon = Icons.Rounded.SwapHoriz) }
        }
        BodyText("${SettingsText.MOVE}: ${SettingsText.MOVE_KEYS}")
        ControllerAction.entries.forEach { action ->
            val keys = keysLabel(mapping, action)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(SettingsText.actionLabel(action), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        keys ?: SettingsText.NOT_ASSIGNED,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (keys == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlutoButton(
                    id = "controller:remap:$action",
                    label = SettingsText.REMAP,
                    enabled = router != null,
                    onClick = {
                        notice = null
                        captureRefused = false
                        capturing = action
                    },
                )
            }
        }
        ButtonBar(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), arrangement = Arrangement.spacedBy(8.dp)) {
            PlutoButton(
                "controller:reset",
                SettingsText.RESET,
                {
                    val target = controller?.takeIf { scopeThis }
                    vm.updateSettings { s ->
                        if (target != null) {
                            s.copy(
                                controllerMappings = s.controllerMappings -
                                    target.mappingKey() -
                                    LauncherSettings.vendorProductKey(target.vendorId, target.productId),
                            )
                        } else {
                            s.copy(defaultMapping = ButtonMapping.DEFAULT)
                        }
                    }
                    notice = SettingsText.RESET_DONE
                },
                icon = Icons.Rounded.RestartAlt,
                style = ButtonStyle.OUTLINED,
            )
        }

        // --- Stick tuning ---
        SectionHeader(SettingsText.STICK)
        StepperRow(
            idPrefix = "controller:deadzone",
            label = SettingsText.DEAD_ZONE,
            supporting = SettingsText.DEAD_ZONE_HELP,
            valueText = "%.2f".format(settings.stickDeadZone),
            onDecrease = stepped(settings.stickDeadZone, DEAD_ZONE_STEP, LauncherSettings.DEAD_ZONE_RANGE, -1)
                ?.let { v -> { vm.updateSettings { it.copy(stickDeadZone = v) } } },
            onIncrease = stepped(settings.stickDeadZone, DEAD_ZONE_STEP, LauncherSettings.DEAD_ZONE_RANGE, 1)
                ?.let { v -> { vm.updateSettings { it.copy(stickDeadZone = v) } } },
        )
        StepperRow(
            idPrefix = "controller:delay",
            label = SettingsText.REPEAT_DELAY,
            supporting = SettingsText.REPEAT_DELAY_HELP,
            valueText = SettingsText.ms(settings.repeatDelayMs),
            onDecrease = stepped(settings.repeatDelayMs, REPEAT_DELAY_STEP, REPEAT_DELAY_RANGE, -1)
                ?.let { v -> { vm.updateSettings { it.copy(repeatDelayMs = v) } } },
            onIncrease = stepped(settings.repeatDelayMs, REPEAT_DELAY_STEP, REPEAT_DELAY_RANGE, 1)
                ?.let { v -> { vm.updateSettings { it.copy(repeatDelayMs = v) } } },
        )
        StepperRow(
            idPrefix = "controller:interval",
            label = SettingsText.REPEAT_INTERVAL,
            supporting = SettingsText.REPEAT_INTERVAL_HELP,
            valueText = SettingsText.ms(settings.repeatIntervalMs),
            onDecrease = stepped(settings.repeatIntervalMs, REPEAT_INTERVAL_STEP, REPEAT_INTERVAL_RANGE, -1)
                ?.let { v -> { vm.updateSettings { it.copy(repeatIntervalMs = v) } } },
            onIncrease = stepped(settings.repeatIntervalMs, REPEAT_INTERVAL_STEP, REPEAT_INTERVAL_RANGE, 1)
                ?.let { v -> { vm.updateSettings { it.copy(repeatIntervalMs = v) } } },
        )
    }
}

/**
 * Last key and live axes. Only the last key is a (polite) live region: axis values change
 * dozens of times per second and would flood a screen reader, so they expose a coarse
 * direction summary instead, read when the user reaches it.
 */
@Composable
private fun DiagnosticsPanel(diagnostics: Diagnostics, deadZone: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(SettingsText.LAST_KEY, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LastKeyChip(diagnostics)
        Spacer(Modifier.heightIn(min = 8.dp))
        Text(SettingsText.AXES, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.padding(vertical = 8.dp).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StickPad(diagnostics, deadZone, right = false)
            StickPad(diagnostics, deadZone, right = true)
        }
        val summary = diagnostics.stickSummary ?: SettingsText.NO_MOTION_YET
        Text(
            diagnostics.axes ?: SettingsText.NO_MOTION_YET,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
        )
    }
}

private const val KEY_PULSE_SCALE = 0.06f

/** The last key pressed, in a chip that pulses once per new press (a one-shot, never looping). */
@Composable
private fun LastKeyChip(diagnostics: Diagnostics) {
    val colors = MaterialTheme.colorScheme
    val pulse = remember { Animatable(0f) }
    val presses = diagnostics.keyPresses
    LaunchedEffect(presses) {
        if (presses == 0) return@LaunchedEffect
        pulse.snapTo(1f)
        pulse.animateTo(0f, tween(PlutoMotion.LONG_MS, easing = PlutoMotion.EmphasizedDecelerate))
    }
    val highlight = colors.primary
    Text(
        diagnostics.lastKey ?: SettingsText.NO_KEY_YET,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurface,
        modifier = Modifier
            .padding(top = 4.dp)
            .graphicsLayer {
                val scale = 1f + KEY_PULSE_SCALE * pulse.value
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHighest)
            .drawBehind { drawRect(highlight, alpha = 0.28f * pulse.value) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private val StickPadSize = 64.dp
private val StickDotSize = 14.dp

/**
 * Live stick position: a ring (with the dead zone shaded) and a dot. The dot follows the
 * true value on a very stiff, critically damped spring (≈20 ms behind), which smooths
 * coarse event steps without visible lag. Drawn in the draw phase only.
 */
@Composable
private fun StickPad(diagnostics: Diagnostics, deadZone: Float, right: Boolean) {
    val colors = MaterialTheme.colorScheme
    val position = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(diagnostics, right) {
        snapshotFlow {
            if (right) Offset(diagnostics.rightX, diagnostics.rightY) else Offset(diagnostics.leftX, diagnostics.leftY)
        }.collectLatest { target ->
            position.animateTo(target, StickSpring)
        }
    }
    val ring = colors.outline
    val zone = colors.surfaceContainerHighest
    val dot = colors.primary
    val idle = colors.onSurfaceVariant
    val dotRadius = with(LocalDensity.current) { StickDotSize.toPx() / 2f }
    Canvas(Modifier.size(StickPadSize)) {
        val radius = size.minDimension / 2f
        drawCircle(zone, radius = radius * deadZone.coerceIn(0f, 1f))
        drawCircle(ring, radius = radius - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
        val p = position.value
        val travel = radius - dotRadius
        drawCircle(
            if (diagnostics.hasMotion) dot else idle,
            radius = dotRadius,
            center = center + Offset(p.x.coerceIn(-1f, 1f) * travel, p.y.coerceIn(-1f, 1f) * travel),
        )
    }
}

/** Critically damped and very stiff: settles in ~40 ms, trails a moving stick by ~20 ms. */
private val StickSpring = spring<Offset>(dampingRatio = 1f, stiffness = 10_000f, visibilityThreshold = Offset(0.001f, 0.001f))

/** Modal "Press a button…" prompt. Cancel works by touch or system Back; it also times out. */
@Composable
private fun CaptureDialog(
    action: ControllerAction,
    secondsLeft: Int,
    refused: Boolean,
    reducedMotion: Boolean,
    onCancel: () -> Unit,
) {
    rememberScreenFocus("controller:capture:cancel")
    // The "listening" pulse loops only while capture is live and Reduce motion is off.
    val listening = !reducedMotion && !isDialogExiting()
    ModalPanel(
        title = SettingsText.PRESS_A_BUTTON,
        idPrefix = "controller:capture",
        onDismiss = onCancel,
        interceptBack = true,
        actions = { PlutoButton("controller:capture:cancel", KitText.CANCEL, onCancel, style = ButtonStyle.TEXT) },
    ) {
        ListeningIndicator(listening, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        BodyText(SettingsText.captureFor(SettingsText.actionLabel(action), secondsLeft))
        ExpandingSection(visible = refused) {
            NoticeCard(SettingsText.CAPTURE_REFUSED, isError = true, icon = Icons.Rounded.Warning)
        }
    }
}

private val ListeningSize = 72.dp
private const val PULSE_PERIOD_MS = 1_400

/**
 * Gamepad glyph with a gentle ring that keeps expanding and fading while [active]. The loop
 * stops (ring hidden) as soon as [active] turns false, and also when the window's animation
 * scale is 0 (Reduce motion / system "Remove animations"), so it never spins frames for nothing.
 * Decorative: hidden from accessibility (the dialog text says what to do).
 */
@Composable
private fun ListeningIndicator(active: Boolean, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(active) {
        ring.snapTo(0f)
        if (!active) return@LaunchedEffect
        while (isActive) {
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale == 0f) {
                ring.snapTo(0f)
                break
            }
            ring.snapTo(0f)
            ring.animateTo(1f, tween(PULSE_PERIOD_MS, easing = LinearOutSlowInEasing))
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(ListeningSize)
            .clearAndSetSemantics { }
            .drawBehind {
                val p = ring.value
                if (p > 0f) {
                    val base = size.minDimension * 0.32f
                    drawCircle(
                        color,
                        radius = base + (size.minDimension / 2f - base) * p,
                        alpha = 0.45f * (1f - p),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ListeningSize * 0.64f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
