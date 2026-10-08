package dev.pluto.launcher.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/**
 * UiAutomator journeys through Pluto's hot paths, shared by the profile generator and the
 * benchmarks. Elements are found by their accessibility labels (the same ones TalkBack
 * reads), so nothing test-only is needed in the app. Every step tolerates a missing
 * element (a layout variant, a step already done) rather than failing the whole run.
 */
internal const val PACKAGE = "dev.pluto.launcher"

private const val WAIT_MS = 3_000L

/** Starts Pluto from the home screen and waits for its first content. */
internal fun MacrobenchmarkScope.startPluto() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), WAIT_MS)
    device.waitForIdle()
}

/** A fresh install starts with onboarding on top: walk through it. */
internal fun MacrobenchmarkScope.finishOnboarding() {
    repeat(12) {
        val button = find(By.text("Finish")) ?: find(By.text("Skip")) ?: find(By.text("Next")) ?: return
        button.click()
        device.waitForIdle()
    }
}

/** All apps: open (button), fling down and up, search typing and Clear, close with Back. */
internal fun MacrobenchmarkScope.drawerJourney() {
    val open = find(By.desc("All apps")) ?: find(By.text("All apps")) ?: return
    open.click()
    device.waitForIdle()
    val grid = scrollable()
    if (grid != null) {
        repeat(3) {
            grid.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(3) {
            grid.fling(Direction.UP)
            device.waitForIdle()
        }
    }
    val field = find(By.clazz("android.widget.EditText")) ?: find(By.text("Search apps"))
    if (field != null) {
        field.click()
        device.waitForIdle()
        for (query in listOf("c", "ca", "cal", "ca", "c", "", "g", "ga", "")) {
            find(By.clazz("android.widget.EditText"))?.text = query
            device.waitForIdle()
        }
    }
    device.pressBack() // keyboard / search
    device.waitForIdle()
    device.pressBack() // search
    device.waitForIdle()
    if (find(By.clazz("android.widget.EditText")) != null) {
        device.pressBack()
        device.waitForIdle()
    }
    // Open again by swipe from home, then close with Home.
    val w = device.displayWidth
    val h = device.displayHeight
    device.swipe(w / 2, h * 3 / 4, w / 2, h / 6, 12)
    device.waitForIdle()
    device.pressHome()
    device.waitForIdle()
}

/** Settings page: open, fling the list, close. Edit page likewise. */
internal fun MacrobenchmarkScope.pagesJourney() {
    for (label in listOf("Launcher settings", "Edit home")) {
        val button = find(By.desc(label)) ?: continue
        button.click()
        device.waitForIdle()
        scrollable()?.let { list ->
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }
        device.pressBack()
        device.waitForIdle()
    }
}

/**
 * Landscape and Handheld: switches Handheld appearance to "Always in landscape" (via
 * Settings), rotates, traverses with the D-pad, switches shelves, and rotates back.
 */
internal fun MacrobenchmarkScope.handheldJourney() {
    setHandheldAlways()
    device.setOrientationLandscape()
    device.waitForIdle()
    repeat(8) {
        device.pressDPadRight()
    }
    device.waitForIdle()
    repeat(3) {
        device.pressDPadDown()
    }
    device.waitForIdle()
    repeat(2) {
        device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_R1)
        device.waitForIdle()
    }
    device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_L1)
    device.waitForIdle()
    // The drawer from Handheld (controller Y opens search).
    device.pressKeyCode(KeyEvent.KEYCODE_BUTTON_Y)
    device.waitForIdle()
    repeat(6) {
        device.pressDPadDown()
    }
    device.waitForIdle()
    device.pressBack()
    device.pressBack()
    device.waitForIdle()
    device.setOrientationNatural()
    device.waitForIdle()
    device.unfreezeRotation()
}

private fun MacrobenchmarkScope.setHandheldAlways() {
    val settings = find(By.desc("Launcher settings")) ?: return
    settings.click()
    device.waitForIdle()
    var option = find(By.text("Handheld appearance"))
    val list = scrollable()
    var tries = 0
    while (option == null && list != null && tries++ < 4) {
        list.scroll(Direction.DOWN, 0.8f)
        option = find(By.text("Handheld appearance"))
    }
    option?.click()
    device.waitForIdle()
    find(By.text("Always in landscape"))?.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
    if (find(By.text("Handheld appearance")) != null) {
        device.pressBack()
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.find(selector: BySelector): UiObject2? =
    device.wait(Until.findObject(selector.pkg(PACKAGE)), 1_000L)

private fun MacrobenchmarkScope.scrollable(): UiObject2? =
    device.findObjects(By.pkg(PACKAGE).scrollable(true)).maxByOrNull { it.visibleBounds.height() }
