package dev.pluto.launcher.ui.settings

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
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
import kotlinx.coroutines.delay
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

/** Never consumed at all, so Android keeps handling them. */
private val SYSTEM_KEYS = setOf(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH)

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
    var axes by mutableStateOf<String?>(null)
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
        trapFocus = isTop && capturing == null,
        overlay = {
            capturing?.let { action ->
                CaptureDialog(
                    action = action,
                    secondsLeft = secondsLeft,
                    refused = captureRefused,
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
            DiagnosticsPanel(diagnostics)
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
        notice?.let { NoticeCard(it, icon = Icons.Rounded.SwapHoriz) }
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

@Composable
private fun DiagnosticsPanel(diagnostics: Diagnostics) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(SettingsText.LAST_KEY, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(diagnostics.lastKey ?: SettingsText.NO_KEY_YET, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.heightIn(min = 8.dp))
        Text(SettingsText.AXES, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(diagnostics.axes ?: SettingsText.NO_MOTION_YET, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Modal "Press a button…" prompt. Cancel works by touch or system Back; it also times out. */
@Composable
private fun CaptureDialog(action: ControllerAction, secondsLeft: Int, refused: Boolean, onCancel: () -> Unit) {
    rememberScreenFocus("controller:capture:cancel")
    ModalPanel(
        title = SettingsText.PRESS_A_BUTTON,
        idPrefix = "controller:capture",
        onDismiss = onCancel,
        interceptBack = true,
        actions = { PlutoButton("controller:capture:cancel", KitText.CANCEL, onCancel, style = ButtonStyle.TEXT) },
    ) {
        BodyText(SettingsText.captureFor(SettingsText.actionLabel(action), secondsLeft))
        if (refused) NoticeCard(SettingsText.CAPTURE_REFUSED, isError = true, icon = Icons.Rounded.Warning)
    }
}
