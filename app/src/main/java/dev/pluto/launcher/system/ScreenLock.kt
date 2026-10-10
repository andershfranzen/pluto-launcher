package dev.pluto.launcher.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Locks the screen for the Home double tap. Android only lets an app do that without
 * disabling biometric unlock through an accessibility service's GLOBAL_ACTION_LOCK_SCREEN, so
 * this service does exactly that and nothing else: it subscribes to no events and cannot
 * read window content. The user turns it on in Accessibility settings.
 */
class ScreenLockService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "ScreenLock"

        @Volatile private var instance: ScreenLockService? = null

        /** True while the user has the service turned on and Android has connected it. */
        val isReady: Boolean get() = instance != null

        /** Locks the screen; false when the service is off (the caller explains how to turn it on). */
        fun lock(): Boolean {
            val service = instance ?: return false
            return try {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Lock screen action failed", e)
                false
            }
        }

        /** Opens Accessibility settings, on Pluto's own entry where the system supports that. */
        fun openSettings(context: Context) {
            val component = ComponentName(context, ScreenLockService::class.java).flattenToString()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't open Accessibility settings", e)
            }
        }
    }
}
