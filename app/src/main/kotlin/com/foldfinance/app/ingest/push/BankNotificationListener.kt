package com.foldfinance.app.ingest.push

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.foldfinance.app.ingest.IngestScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Bank push notifications. A thin shell over [PushNotificationHandler]:
 * `onNotificationPosted` passes only the package name for the whitelist check and a lambda that
 * reads the text, which the handler calls only for whitelisted packages. Nothing slow happens in
 * the callback.
 *
 * Health: connect/disconnect go to [ListenerHealth]; on disconnect the listener asks the system to
 * rebind it. Binding is controlled by the system (`BIND_NOTIFICATION_LISTENER_SERVICE`).
 */
@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {

    @Inject lateinit var handler: PushNotificationHandler
    @Inject lateinit var health: ListenerHealth
    @Inject lateinit var scheduler: IngestScheduler

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        handler.onPosted(notification.packageName) { PushContent.from(notification) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        health.onConnected()
        // The system starts our process to bind the listener: a good moment to make sure the
        // periodic SMS delta scan and the reparse check exist.
        scheduler.ensureScheduled()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        health.onDisconnected()
        requestRebind(ListenerHealth.componentName(this))
    }
}
