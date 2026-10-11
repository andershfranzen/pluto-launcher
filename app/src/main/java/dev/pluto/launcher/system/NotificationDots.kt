package dev.pluto.launcher.system

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Package/profile identifiers only, in memory. Notification contents are never retained. */
data class NotificationApp(val packageName: String, val userSerial: Long)

object NotificationDots {
    private val _apps = MutableStateFlow<Set<NotificationApp>>(emptySet())
    val apps = _apps.asStateFlow()
    private val _access = MutableStateFlow(false)
    val access = _access.asStateFlow()
    internal fun publish(apps: Set<NotificationApp>) { _apps.value = apps }
    fun refreshAccess(context: Context) {
        _access.value = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, PlutoNotificationListener::class.java))
        if (!_access.value) publish(emptySet())
    }
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, PlutoNotificationListener::class.java).flattenToString())
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }
}

/** Coalesce notification bursts and read Android's authoritative metadata off the main thread. */
class PlutoNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private var connected = false
    private var connectionEpoch = 0L

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            for (request in refreshes) {
                if (!connected) continue
                val epoch = connectionEpoch
                val apps = withContext(Dispatchers.IO) { readApps() }
                // Lifecycle callbacks and publication both run on main: disconnect cannot race publication.
                if (connected && epoch == connectionEpoch) NotificationDots.publish(apps)
            }
        }
    }

    override fun onListenerConnected() {
        connectionEpoch++
        connected = true
        NotificationDots.refreshAccess(this)
        refreshes.trySend(Unit)
    }
    override fun onListenerDisconnected() {
        connectionEpoch++
        connected = false
        NotificationDots.publish(emptySet())
    }
    override fun onDestroy() {
        connectionEpoch++
        connected = false
        scope.cancel()
        refreshes.close()
        NotificationDots.publish(emptySet())
        super.onDestroy()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) { refreshes.trySend(Unit) }
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) { refreshes.trySend(Unit) }
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) { refreshes.trySend(Unit) }

    private fun readApps(): Set<NotificationApp> = runCatching {
        val users = getSystemService(UserManager::class.java)
        val serials = HashMap<android.os.UserHandle, Long>()
        val rankings = currentRanking
        val ranking = Ranking()
        buildSet {
            for (sbn in activeNotifications.orEmpty()) {
                val ranked = rankings?.getRanking(sbn.key, ranking) == true
                if (eligibleNotificationDot(sbn.notification.flags, ranked && ranking.canShowBadge(),
                        if (ranked) ranking.importance else NotificationManager.IMPORTANCE_NONE)) {
                    // One profile lookup per batch, instead of a Binder call for every notification.
                    val serial = serials.getOrPut(sbn.user) { users.getSerialNumberForUser(sbn.user) }
                    if (serial >= 0) add(NotificationApp(sbn.packageName, serial))
                }
            }
        }
    }.getOrDefault(emptySet())
}

internal fun eligibleNotificationDot(flags: Int, canShowBadge: Boolean, importance: Int): Boolean =
    canShowBadge && importance > NotificationManager.IMPORTANCE_NONE &&
        flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) == 0
