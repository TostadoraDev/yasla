package xyz.tosdev.yetasimplelyicsapp.media

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import xyz.tosdev.yetasimplelyicsapp.LyricsApplication

class MediaNotificationListenerService : NotificationListenerService() {
    private val monitor by lazy(LazyThreadSafetyMode.NONE) {
        MediaPlaybackMonitor.get(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // The system can recreate this service without ever recreating MainActivity. Starting the
        // application owner is idempotent and restores the widget pipeline with the listener.
        (application as? LyricsApplication)?.container?.start()
        monitor.onNotificationListenerConnected()
        monitor.traceLiveness("NotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        monitor.onNotificationListenerDisconnected()
        super.onListenerDisconnected()

        // The system can disconnect a listener while reclaiming the process.
        // This API is explicitly safe after onListenerDisconnected and lets
        // Android rebind us without requiring the activity to be reopened.
        requestRebind(ComponentName(this, MediaNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != packageName) monitor.refreshSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName != packageName) monitor.refreshSessions()
    }
}
