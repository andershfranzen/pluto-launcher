package dev.pluto.launcher.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import dev.pluto.launcher.domain.ControllerClassifier
import dev.pluto.launcher.model.ControllerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks connected game controllers via InputManager. Enumerates on [refresh]
 * (call at start and resume) and listens for added/removed/changed devices.
 * Ordinary keyboards never count (see ControllerClassifier).
 */
class ControllerMonitor(context: Context) {
    private val inputManager = context.applicationContext.getSystemService(InputManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listening = false

    private val _controllers = MutableStateFlow<List<ControllerInfo>>(emptyList())
    val controllers: StateFlow<List<ControllerInfo>> = _controllers.asStateFlow()

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
    }

    /**
     * Owners that called [start] without a matching [stop]. Each MainActivity's ViewModel
     * starts and stops this shared monitor, and two can briefly coexist (granting the Home
     * role starts a fresh Home task while the old one finishes); the listener is only
     * unregistered when the last owner stops, or attach/detach would go unnoticed.
     */
    private var owners = 0

    /** Registers the InputManager listener (once, however many owners start it). */
    fun start() {
        owners++
        if (!listening) {
            listening = true
            try {
                inputManager.registerInputDeviceListener(listener, mainHandler)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't register input device listener", e)
            }
        }
        refresh()
    }

    fun stop() {
        if (owners == 0) return
        owners--
        if (owners > 0 || !listening) return
        listening = false
        try {
            inputManager.unregisterInputDeviceListener(listener)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't unregister input device listener", e)
        }
    }

    /** Re-enumerates devices; safe to call often. */
    fun refresh() {
        val found = try {
            inputManager.inputDeviceIds.toList().mapNotNull { id ->
                inputManager.getInputDevice(id)?.takeIf(::isController)?.toInfo()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't enumerate input devices", e)
            return
        }
        // StateFlow dedupes equal lists, so repeated refreshes don't churn the UI.
        _controllers.value = found.sortedBy { it.deviceId }
    }

    private fun isController(device: InputDevice): Boolean {
        val hasButtons = runCatching {
            device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B).any { it }
        }.getOrDefault(false)
        return ControllerClassifier.isGameController(device.sources, device.isVirtual, hasButtons)
    }

    private fun InputDevice.toInfo() = ControllerInfo(
        deviceId = id,
        name = name.orEmpty(),
        descriptor = descriptor.orEmpty(),
        vendorId = vendorId,
        productId = productId,
        sources = sources,
    )

    private companion object {
        const val TAG = "ControllerMonitor"
    }
}
