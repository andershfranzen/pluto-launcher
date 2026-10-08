package dev.pluto.launcher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
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
import dev.pluto.launcher.ui.theme.LauncherMotionDurationScale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Home activity.
 *
 * - Edge-to-edge, transparent over the system wallpaper (theme windowShowWallpaper).
 * - Creates the LauncherViewModel (factory from LauncherApplication.container) and a
 *   ControllerInputRouter whose actions feed a flow passed to LauncherRoot.
 * - dispatchKeyEvent / dispatchGenericMotionEvent: give gamepad events to the router first.
 *   Touch events (dispatchTouchEvent) notify the focus layer that touch took over.
 * - onNewIntent with ACTION_MAIN + CATEGORY_HOME while already resumed -> vm.onHomePressed().
 * - Back (OnBackPressedDispatcher) -> vm.back(); at home Back does nothing (launcher stays).
 * - Applies settings.rotation to requestedOrientation: FOLLOW_SYSTEM -> SCREEN_ORIENTATION_USER,
 *   PORTRAIT -> USER_PORTRAIT, LANDSCAPE -> USER_LANDSCAPE (both directions),
 *   LANDSCAPE_WHEN_CONTROLLER -> USER_LANDSCAPE while a controller is connected, else USER.
 *   Only affects the launcher window, never the foreground app.
 * - onResume: vm.onResume(HomeRole.isDefaultHome()), which refreshes catalog and controllers.
 * - onPause / onWindowFocusChanged(false): router.reset().
 *
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
            ) {
                LauncherRoot(vm, actions)
            }
        }
        setContentView(view)
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
        // Checked before super: the lifecycle tells us whether Home was pressed while we were
        // already in front, or whether we're being brought back from another app.
        val wasResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        super.onNewIntent(intent)
        setIntent(intent)
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        if (isHome && wasResumed) vm.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        // vm.onResume refreshes the catalog and controllers.
        vm.onResume(HomeRole.isDefaultHome(this))
    }

    override fun onPause() {
        router.reset()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) router.reset()
    }

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
