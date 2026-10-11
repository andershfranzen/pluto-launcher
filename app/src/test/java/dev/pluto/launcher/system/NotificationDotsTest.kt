package dev.pluto.launcher.system

import android.app.Notification
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import dev.pluto.launcher.ui.components.hasDot

class NotificationDotsTest {
    @Test fun onlyBadgeEligibleAlertsShowDots() {
        assertTrue(eligibleNotificationDot(0, true, NotificationManager.IMPORTANCE_DEFAULT))
        assertFalse(eligibleNotificationDot(0, false, NotificationManager.IMPORTANCE_DEFAULT))
        assertFalse(eligibleNotificationDot(0, true, NotificationManager.IMPORTANCE_NONE))
        for (flag in listOf(Notification.FLAG_ONGOING_EVENT, Notification.FLAG_FOREGROUND_SERVICE, Notification.FLAG_GROUP_SUMMARY)) {
            assertFalse(eligibleNotificationDot(flag, true, NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    @Test fun dotsMatchPackageAndProfileTogether() {
        val apps = setOf(NotificationApp("app", 0))
        val entry = dev.pluto.launcher.model.AppEntry(dev.pluto.launcher.model.AppKey("app/.Main", 0), "App")
        assertTrue(apps.hasDot(entry))
        assertFalse(apps.hasDot(entry.copy(key = entry.key.copy(userSerial = 10))))
        assertFalse(apps.hasDot(entry.copy(key = entry.key.copy(component = "other/.Main"))))
    }
}
