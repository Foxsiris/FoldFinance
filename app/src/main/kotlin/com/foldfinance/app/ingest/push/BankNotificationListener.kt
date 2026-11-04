package com.foldfinance.app.ingest.push

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Заглушка каркаса. Реализация — этап 4. Ничего не сохраняет. */
class BankNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit
}
