package dev.pluto.launcher.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.pluto.launcher.data.prefs.ButtonMapping
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.system.HomeRole

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens Android's Default apps screen; failures (missing settings activity) are ignored. */
internal fun openDefaultAppsSettings(context: Context) {
    val activity = context.findActivity() ?: return
    runCatching { HomeRole.openDefaultAppsSettings(activity) }
}

/**
 * Returns a callback that starts the system "set default Home app" flow.
 * The role request is launched for a result so the system knows the calling package;
 * success is picked up by the Activity's onResume (state.isDefaultHome). When the role flow
 * is unavailable, or the system refuses it without showing anything (an earlier "Don't ask
 * again", or a ROM that auto-denies), Default apps settings open instead so the tap is never
 * silent. If the user saw the dialog and declined, [onDeclined] explains the manual route.
 */
@Composable
internal fun rememberHomeRoleRequest(onDeclined: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val currentOnDeclined by rememberUpdatedState(onDeclined)
    val startedAt = remember { longArrayOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK || HomeRole.isDefaultHome(context)) return@rememberLauncherForActivityResult
        val elapsed = SystemClock.elapsedRealtime() - startedAt[0]
        if (elapsed < AUTO_DENIED_WITHIN_MS) openDefaultAppsSettings(context) else currentOnDeclined()
    }
    return remember(context, launcher) {
        {
            val intent = runCatching { HomeRole.requestIntent(context) }.getOrNull()
            startedAt[0] = SystemClock.elapsedRealtime()
            val started = intent != null && runCatching { launcher.launch(intent) }.isSuccess
            if (!started) openDefaultAppsSettings(context)
        }
    }
}

/** A role request answered faster than this was refused by the system without asking the user. */
private const val AUTO_DENIED_WITHIN_MS = 700L

/** App version for About; empty when unavailable. Cheap local lookup. */
@Composable
internal fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

/**
 * Readable name for an Android key code: "A" for KEYCODE_BUTTON_A, "Start" for
 * KEYCODE_BUTTON_START, "Back" for KEYCODE_BACK. Short codes stay upper case (L1, R2).
 */
internal fun keyLabel(keyCode: Int): String {
    val raw = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").removePrefix("BUTTON_")
    if (raw.all { it.isDigit() }) return "Key $raw"
    return raw.split('_').joinToString(" ") { part ->
        if (part.length <= 2) part else part.lowercase().replaceFirstChar { it.uppercase() }
    }
}

internal fun keysLabel(mapping: ButtonMapping, action: ControllerAction): String? =
    mapping.keysFor(action).sorted().takeIf { it.isNotEmpty() }?.joinToString(", ", transform = ::keyLabel)
