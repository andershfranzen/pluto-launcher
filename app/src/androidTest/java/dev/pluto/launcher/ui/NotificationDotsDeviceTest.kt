package dev.pluto.launcher.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Process
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import dev.pluto.launcher.system.NotificationApp
import dev.pluto.launcher.system.NotificationDots
import org.junit.Assert.*
import org.junit.Test

/** Requires notification access on the disposable emulator; never grants it on a user's phone. */
class NotificationDotsDeviceTest {
    @Test fun postsRemovalsAndChannelBadgeRulesUpdateLiveDots() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val app = NotificationApp(context.packageName, serial)
        val channel = "pluto-dot-review"
        val quiet = "pluto-no-dot-review"
        manager.createNotificationChannel(NotificationChannel(channel, "Dot review", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(quiet, "No badge review", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) })
        fun post(id: Int, channelId: String = channel, ongoing: Boolean = false) {
            manager.notify(id, Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Pluto dot review")
                .setOngoing(ongoing).build())
        }
        fun waitFor(present: Boolean) {
            val deadline = System.currentTimeMillis() + 10_000
            while ((app in NotificationDots.apps.value) != present && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals(present, app in NotificationDots.apps.value)
        }
        try {
            post(9401); waitFor(true)
            post(9402)
            manager.cancel(9401); waitFor(true)
            manager.cancel(9402); waitFor(false)
            post(9403, ongoing = true)
            post(9404, quiet)
            Thread.sleep(500)
            waitFor(false)
        } finally {
            (9401..9404).forEach(manager::cancel)
            manager.deleteNotificationChannel(channel)
            manager.deleteNotificationChannel(quiet)
        }
    }
}
