package dev.pluto.launcher.input

import android.content.Context
import dev.pluto.launcher.model.ControllerInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks connected game controllers via InputManager. Enumerates on [refresh]
 * (call at start and resume) and listens for added/removed/changed devices.
 * Ordinary keyboards never count (see ControllerClassifier).
 */
class ControllerMonitor(context: Context) {
    val controllers: StateFlow<List<ControllerInfo>> = TODO()

    /** Registers the InputManager listener (idempotent). */
    fun start(): Unit = TODO()
    fun stop(): Unit = TODO()

    /** Re-enumerates devices; safe to call often. */
    fun refresh(): Unit = TODO()
}
