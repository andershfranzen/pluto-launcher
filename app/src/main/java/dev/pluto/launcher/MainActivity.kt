package dev.pluto.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import dev.pluto.launcher.widgets.HOME_ROW_HEIGHT_DP
import dev.pluto.launcher.widgets.LocalWidgetBinder
import dev.pluto.launcher.widgets.LocalWidgetHost
import dev.pluto.launcher.widgets.WidgetBinder
import dev.pluto.launcher.widgets.WidgetHost
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.pluto.launcher.input.ControllerInputRouter
import dev.pluto.launcher.input.LauncherAction
import dev.pluto.launcher.model.RotationPreference
import dev.pluto.launcher.system.HomeRole
import dev.pluto.launcher.ui.LauncherRoot
import dev.pluto.launcher.ui.LauncherViewModel
import dev.pluto.launcher.ui.LocalControllerRouter
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.motion.LaunchSource
import dev.pluto.launcher.ui.motion.LaunchSourceFactory
import dev.pluto.launcher.ui.motion.LocalLaunchSourceFactory
import dev.pluto.launcher.ui.theme.LauncherMotionDurationScale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Home activity.
 *
 * - Edge-to-edge, transparent over the system wallpaper (theme windowShowWallpaper).
 * - Creates the LauncherViewModel (factory from LauncherApplication.container) and a
 *   ControllerInputRouter whose actions feed a flow passed to LauncherRoot.
 * - dispatchKeyEvent / dispatchGenericMotionEvent: give gamepad events to the router first.
 *   Touch events (dispatchTouchEvent) notify the focus layer that touch took over.
 * - onNewIntent with ACTION_MAIN + CATEGORY_HOME while already in front (resumed and not
 *   stopped since) -> vm.onHomePressed().
 * - Back (OnBackPressedDispatcher) -> vm.back(); at home Back does nothing (launcher stays).
 * - Applies settings.rotation to requestedOrientation: FOLLOW_SYSTEM -> SCREEN_ORIENTATION_USER,
 *   PORTRAIT -> USER_PORTRAIT, LANDSCAPE -> USER_LANDSCAPE (both directions),
 *   LANDSCAPE_WHEN_CONTROLLER -> USER_LANDSCAPE while a controller is connected, else USER.
 *   Only affects the launcher window, never the foreground app.
 * - onResume: vm.onResume(HomeRole.isDefaultHome()), which refreshes catalog and controllers.
 * - onPause / onWindowFocusChanged(false): router.reset().
 *
 * - App launches open out of the activated control (ActivityOptions scale-up from its
 *   bounds), provided to Compose as [LocalLaunchSourceFactory].
 * - Compose runs under [LauncherMotionDurationScale] (window recomposer context), so the
 *   Reduce motion setting and Android's animator duration scale apply to every animation.
 *
 * Touch takeover is detected inside Compose (pointer input on the root), so no touch
 * plumbing is needed here. Configuration changes are handled in-process (see manifest).
 */
class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as LauncherApplication).container

    private val vm: LauncherViewModel by viewModels {
        viewModelFactory {
            initializer { LauncherViewModel(container, createSavedStateHandle()) }
        }
    }

    /** The shared state model, exposed for instrumented tests. */
    @VisibleForTesting
    internal val viewModel: LauncherViewModel get() = vm

    /** Controller actions for LauncherRoot; buffered so a burst of input never blocks dispatch. */
    private val actions = MutableSharedFlow<LauncherAction>(extraBufferCapacity = 64)
    private val router = ControllerInputRouter { actions.tryEmit(it) }
    private val motionScale = LauncherMotionDurationScale()
    private val animatorScaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = readSystemAnimatorScale()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The catalog and controller monitor are started/stopped by LauncherViewModel (init/onCleared).

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                // At home vm.back() returns false and we deliberately do nothing:
                // a launcher must stay visible rather than finish.
                override fun handleOnBackPressed() {
                    vm.back()
                }
            },
        )

        observeState()

        readSystemAnimatorScale()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animatorScaleObserver,
        )

        val view = ComposeView(this)
        // Our own window recomposer so its coroutine context carries the launcher's motion scale.
        view.setParentCompositionContext(view.createLifecycleAwareWindowRecomposer(motionScale, lifecycle))
        view.setContent {
            CompositionLocalProvider(
                LocalControllerRouter provides router,
                LocalIconCache provides container.icons,
                LocalLaunchSourceFactory provides launchSources,
                LocalWidgetHost provides container.widgets,
                LocalWidgetBinder provides widgetBinder,
            ) {
                LauncherRoot(vm, actions)
            }
        }
        setContentView(view)
    }

    /**
     * App launches open out of the activated control: [LaunchSource] from its window
     * bounds. The decor view is the window's root, so window coordinates map onto it after
     * subtracting its own window offset (zero in practice), and onto the screen by adding
     * its screen position.
     */
    private val launchSources = LaunchSourceFactory { bounds, animate -> launchSourceFor(bounds, animate) }

    private fun launchSourceFor(bounds: androidx.compose.ui.geometry.Rect, animate: Boolean): LaunchSource? {
        if (bounds.isEmpty || !bounds.isFinite) return null
        val decor = window?.decorView ?: return null
        val inWindow = IntArray(2)
        val onScreen = IntArray(2)
        decor.getLocationInWindow(inWindow)
        decor.getLocationOnScreen(onScreen)
        val left = bounds.left.roundToInt() - inWindow[0]
        val top = bounds.top.roundToInt() - inWindow[1]
        val width = bounds.width.roundToInt().coerceAtLeast(1)
        val height = bounds.height.roundToInt().coerceAtLeast(1)
        val screen = Rect(left + onScreen[0], top + onScreen[1], left + onScreen[0] + width, top + onScreen[1] + height)
        val options = if (animate) {
            runCatching { ActivityOptions.makeScaleUpAnimation(decor, left, top, width, height).toBundle() }.getOrNull()
        } else {
            null
        }
        return LaunchSource(screen, options)
    }

    // --- Widgets: bind (asking the user if needed), configure, then add to Home ---

    private class PendingWidget(val id: Int, val info: AppWidgetProviderInfo)

    private var pendingWidget: PendingWidget? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrAddWidget(pending) else cancelWidget(pending)
    }

    private val widgetBinder = WidgetBinder { info ->
        pendingWidget?.let(::cancelWidget)
        val id = container.widgets.allocate()
        val pending = PendingWidget(id, info)
        pendingWidget = pending
        if (container.widgets.bindIfAllowed(id, info)) {
            configureOrAddWidget(pending)
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    private fun configureOrAddWidget(pending: PendingWidget) {
        val optional = Build.VERSION.SDK_INT >= 28 &&
            pending.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (pending.info.configure == null || optional) {
            addWidget(pending)
            return
        }
        try {
            container.widgets.startConfigure(this, pending.id, REQUEST_CONFIGURE_WIDGET)
        } catch (e: RuntimeException) {
            // No reachable configuration screen: add it as it is.
            addWidget(pending)
        }
    }

    @Deprecated("AppWidgetHost reports configuration through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_WIDGET) return
        val pending = pendingWidget ?: return
        if (resultCode == RESULT_OK) addWidget(pending) else cancelWidget(pending)
    }

    private fun addWidget(pending: PendingWidget) {
        pendingWidget = null
        val rows = WidgetHost.defaultRows(pending.info, resources.displayMetrics.density, HOME_ROW_HEIGHT_DP)
        vm.addWidget(pending.id, pending.info.provider.flattenToString(), rows)
    }

    private fun cancelWidget(pending: PendingWidget) {
        if (pendingWidget === pending) pendingWidget = null
        container.widgets.delete(pending.id)
    }

    private fun readSystemAnimatorScale() {
        motionScale.systemScale = runCatching {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.state.collect {
                        router.settings = it.settings
                        motionScale.reducedMotion = it.settings.reducedMotion
                    }
                }
                launch {
                    vm.state
                        .map { orientationFor(it.settings.rotation, it.controllerConnected) }
                        .distinctUntilChanged()
                        .collect { orientation ->
                            if (requestedOrientation != orientation) requestedOrientation = orientation
                        }
                }
                launch {
                    // A removed controller must not leave a held direction repeating.
                    var previous = 0
                    container.controllers.controllers.collect { list ->
                        if (list.size < previous) router.reset()
                        previous = list.size
                    }
                }
            }
        }
    }

    private fun orientationFor(rotation: RotationPreference, controllerConnected: Boolean): Int =
        when (rotation) {
            RotationPreference.FOLLOW_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_USER
            RotationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            RotationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            RotationPreference.LANDSCAPE_WHEN_CONTROLLER ->
                if (controllerConnected) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER
                }
        }

    override fun onNewIntent(intent: Intent) {
        // The framework pauses a singleTask activity before delivering a new intent, so the
        // lifecycle state cannot tell "Home pressed while in front" from "returning from an
        // app". [visibleSinceResume] is only cleared in onStop, which a press while we are in
        // front never reaches.
        val alreadyInFront = visibleSinceResume
        super.onNewIntent(intent)
        setIntent(intent)
        if (isHomeIntent(intent) && alreadyInFront) vm.onHomePressed()
    }

    /** True from onResume until onStop: the launcher has been in front and still is visible. */
    private var visibleSinceResume = false

    override fun onResume() {
        super.onResume()
        visibleSinceResume = true
        // vm.onResume refreshes the catalog (if it may be stale) and controllers.
        vm.onResume(HomeRole.isDefaultHome(this))
    }

    override fun onStart() {
        super.onStart()
        container.widgets.startListening()
    }

    override fun onStop() {
        container.widgets.stopListening()
        visibleSinceResume = false
        super.onStop()
    }

    override fun onPause() {
        router.reset()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) router.reset()
    }

    private fun isHomeIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        router.onKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        router.onGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(animatorScaleObserver)
        router.reset()
        super.onDestroy()
    }
}

private const val REQUEST_CONFIGURE_WIDGET = 0x5701
