package dev.pluto.launcher.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

internal const val PACKAGE = "dev.pluto.launcher"
private const val WAIT_MS = 10_000L

/** Required controls fail the run when a layout change prevents collecting intended code. */
internal fun MacrobenchmarkScope.startPluto() {
    startActivityAndWait()
    check(device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), WAIT_MS)) { "Pluto did not start" }
    device.waitForIdle(1_000)
    requireObject(By.desc("Profile app 00"))
}

internal fun MacrobenchmarkScope.finishOnboarding() {
    repeat(12) {
        val button = find(By.text("Finish")) ?: find(By.text("Skip")) ?: find(By.text("Next")) ?: return
        button.click()
        device.waitForIdle(1_000)
    }
    error("Onboarding did not finish")
}

internal fun MacrobenchmarkScope.drawerJourney() {
    openPhoneDrawer()
    val field = requireObject(By.clazz("android.widget.EditText"))
    field.click()
    device.waitForIdle(1_000)
    for (query in listOf("s", "se", "set", "settings", "set", "s", "")) {
        requireObject(By.clazz("android.widget.EditText")).text = query
        device.waitForIdle(1_000)
        if (query == "settings") requireObject(By.desc("Settings, app"))
    }
    device.pressBack() // keyboard
    device.waitForIdle(1_000)
    requireObject(By.clazz("android.widget.EditText"))
    val grid = requireScrollable()
    fastGridFling(grid, Direction.DOWN)
    fastGridFling(requireScrollable(), Direction.UP)
    closeDrawer()
}

/** Profile fixtures make both non-app result types mandatory on every collection iteration. */
internal fun MacrobenchmarkScope.richerSearchJourney() {
    openPhoneDrawer()
    requireObject(By.clazz("android.widget.EditText")).click()
    requireObject(By.clazz("android.widget.EditText")).text = "profile game 00"
    requireObject(By.desc("Profile game 00, game in Profile shelf 1"))
    requireObject(By.clazz("android.widget.EditText")).text = "profile shortcut"
    requireObject(By.desc(java.util.regex.Pattern.compile("Profile shortcut action, .* shortcut")))
    requireObject(By.clazz("android.widget.EditText")).text = "profile game"
    requireObject(By.desc("Profile game 00, game in Profile shelf 1"))
    device.pressBack() // keyboard
    device.waitForIdle(1_000)
    fastGridFling(requireScrollable(), Direction.DOWN)
    device.waitForIdle(1_000)
    requireObject(By.clazz("android.widget.EditText")).text = ""
    closeDrawer()
}

internal fun MacrobenchmarkScope.pagesJourney() {
    for (label in listOf("Launcher settings", "Edit home")) {
        android.os.SystemClock.sleep(350) // The returning Home can precede the closing layer’s touch overlay.
        requireObject(By.descStartsWith("Home screen. Actions:")).longClick()
        requireObject(By.text(label))
        android.os.SystemClock.sleep(300) // Popup springs into its final touch bounds.
        requireObject(By.text(label)).click()
        device.waitForIdle(1_000)
        requireObject(By.text(if (label == "Launcher settings") "Settings" else "Edit home"))
        device.findObjects(By.pkg(PACKAGE).scrollable(true)).maxByOrNull { it.visibleBounds.height() }?.let { list ->
            fastGridFling(list, Direction.DOWN)
            device.waitForIdle(1_000)
            fastGridFling(requireScrollable(), Direction.UP)
            device.waitForIdle(1_000)
        }
        device.pressBack()
        device.waitForIdle(1_000)
        requireObject(By.descStartsWith("Home screen. Actions:"))
    }
}

/** Exercise app/ROM carousels, real cover decodes, overview grid, and rapid shelf cancellation. */
internal fun MacrobenchmarkScope.consoleJourney() {
    configureFixtures("console")
    device.waitForIdle(1_000)
    requireObject(By.text("All apps")).click()
    requireObject(By.desc("Browse All apps").clickable(true)).click()
    requireObject(By.text("Browse All apps"))
    requireObject(By.desc(java.util.regex.Pattern.compile(".*, select in carousel")))
    fastGridFling(requireScrollable(), Direction.DOWN)
    device.waitForIdle(1_000)
    device.pressBack()
    requireObject(By.desc("Browse All apps").clickable(true))
    for (index in 1..2) {
        android.os.SystemClock.sleep(350) // The overview's exit layer must release touch/input.
        if (index == 1) requireObject(By.text("Profile shelf 1")).click()
        else sendGamepadKeys(KeyEvent.KEYCODE_BUTTON_R1)
        requireObject(By.desc("Browse Profile shelf $index").clickable(true))
        // Fast card selection and far flings exercise windowing and prewarm cancellation.
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(4) { device.swipe(w * 4 / 5, h / 2, w / 5, h / 2, 8) }
        device.waitForIdle(1_000)
        awaitDecodedCover(index, 128)
        requireObject(By.desc("Browse Profile shelf $index").clickable(true)).click()
        requireObject(By.text("Browse Profile shelf $index"))
        requireObject(By.text("48 items · Select to return to the carousel"))
        val grid = requireScrollable()
        fastGridFling(grid, Direction.DOWN)
        device.waitForIdle(1_000)
        fastGridFling(requireScrollable(), Direction.UP)
        device.waitForIdle(1_000)
        awaitDecodedCover(index, 112)
        selectVisibleOverviewGame()
        requireObject(By.desc("Browse Profile shelf $index").clickable(true))
    }
    android.os.SystemClock.sleep(350)
    sendGamepadKeys(KeyEvent.KEYCODE_BUTTON_L1)
    requireObject(By.desc("Browse Profile shelf 1").clickable(true))
    sendGamepadKeys(KeyEvent.KEYCODE_BUTTON_R1)
    requireObject(By.desc("Browse Profile shelf 2").clickable(true))
    sendGamepadKeys(*IntArray(6) { if (it % 2 == 0) KeyEvent.KEYCODE_BUTTON_L1 else KeyEvent.KEYCODE_BUTTON_R1 })
    device.waitForIdle(1_000)
    requireObject(By.desc("Browse Profile shelf 2").clickable(true))
    configureFixtures("phone")
    device.waitForIdle(1_000)
    requireObject(By.descStartsWith("Home screen. Actions:"))
}

internal fun MacrobenchmarkScope.configureFixtures(method: String) {
    val result = device.executeShellCommand("content call --uri content://$PACKAGE.profile-fixtures --method $method")
    check(result.contains("status=ready")) { "Profile fixture $method failed: $result" }
}

private fun MacrobenchmarkScope.sendGamepadKeys(vararg keys: Int) {
    // UiDevice.pressKeyCode emits keyboard-source events, which the gamepad router ignores.
    device.executeShellCommand("input gamepad keyevent ${keys.joinToString(" ")}")
}

private fun MacrobenchmarkScope.selectVisibleOverviewGame() {
    val deadline = android.os.SystemClock.uptimeMillis() + WAIT_MS
    var previous: Pair<String, android.graphics.Rect>? = null
    do {
        val viewport = requireScrollable().visibleBounds
        val candidates = device.findObjects(By.desc(java.util.regex.Pattern.compile("Profile game [0-9]{2}, select in carousel")).pkg(PACKAGE))
        val target = candidates.firstNotNullOfOrNull { candidate ->
            // Fling momentum can recycle every cell between hierarchy queries; retry fresh nodes.
            try { candidate.visibleBounds.takeIf { !it.isEmpty && viewport.contains(it.centerX(), it.centerY()) }
                ?.let { candidate.contentDescription to it } }
            catch (_: StaleObjectException) { null }
        }
        if (target != null && target == previous) {
            val bounds = target.second
            device.click(bounds.centerX(), bounds.centerY())
            return
        }
        previous = target
        android.os.SystemClock.sleep(200)
    } while (android.os.SystemClock.uptimeMillis() < deadline)
    error("Expected a visible overview game after scrolling settled")
}

private fun MacrobenchmarkScope.fastGridFling(grid: UiObject2, direction: Direction) {
    // UiObject2.fling waits up to 10s for a scroll-finished event even at a boundary.
    // A fast swipe drives the same pointer/velocity path; screen assertions handle readiness.
    grid.swipe(if (direction == Direction.DOWN) Direction.UP else Direction.DOWN, 0.8f, 7_500)
    device.waitForIdle(1_000)
}

private fun MacrobenchmarkScope.awaitDecodedCover(library: Int, dp: Int) {
    val deadline = android.os.SystemClock.uptimeMillis() + WAIT_MS
    var result: String
    do {
        result = device.executeShellCommand("content call --uri content://$PACKAGE.profile-fixtures --method assertCover --arg $library:$dp")
        if (result.contains("status=ready")) return
        android.os.SystemClock.sleep(200)
    } while (android.os.SystemClock.uptimeMillis() < deadline)
    error("Expected correctly sized decoded ${dp}dp cover for shelf $library: $result")
}

private fun MacrobenchmarkScope.openPhoneDrawer() {
    android.os.SystemClock.sleep(350)
    requireObject(By.descStartsWith("Home screen. Actions:"))
    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
        device.displayWidth / 2, device.displayHeight / 4, 12)
    requireObject(By.clazz("android.widget.EditText"))
}

private fun MacrobenchmarkScope.closeDrawer() {
    repeat(3) {
        if (find(By.clazz("android.widget.EditText")) == null) {
            requireObject(By.descStartsWith("Home screen. Actions:"))
            return
        }
        device.pressBack()
        device.waitForIdle(1_000)
    }
    check(find(By.clazz("android.widget.EditText")) == null) { "Drawer did not close" }
    requireObject(By.descStartsWith("Home screen. Actions:"))
}

private fun MacrobenchmarkScope.find(selector: BySelector): UiObject2? =
    device.wait(Until.findObject(selector.pkg(PACKAGE)), 500L)

private fun MacrobenchmarkScope.requireObject(selector: BySelector): UiObject2 =
    checkNotNull(device.wait(Until.findObject(selector.pkg(PACKAGE)), WAIT_MS)) {
        val hierarchy = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(hierarchy)
        "Required journey control missing: $selector\n${hierarchy.toString(Charsets.UTF_8.name()).take(12_000)}"
    }

private fun MacrobenchmarkScope.requireScrollable(): UiObject2 {
    check(device.wait(Until.hasObject(By.pkg(PACKAGE).scrollable(true)), WAIT_MS)) {
        "Expected scrollable content was absent"
    }
    return checkNotNull(device.findObjects(By.pkg(PACKAGE).scrollable(true)).mapNotNull { candidate ->
        try { candidate.visibleBounds.takeIf { !it.isEmpty }?.let { candidate to it.height() } }
        catch (_: StaleObjectException) { null }
    }.maxByOrNull { it.second }?.first) { "Expected visible scrollable content was absent" }
}
