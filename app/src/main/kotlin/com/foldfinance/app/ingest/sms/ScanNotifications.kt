package com.foldfinance.app.ingest.sms

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.foldfinance.app.R

/**
 * The «Сканирование SMS» notification of the foreground scan. Shows counts only — never texts or
 * sums (the shade is visible on the lock screen).
 */
object ScanNotifications {
    const val CHANNEL_ID: String = "ingest_scan"
    const val NOTIFICATION_ID: Int = 4101

    fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.ingest_channel_scan_name))
                .setDescription(context.getString(R.string.ingest_channel_scan_description))
                .setShowBadge(false)
                .build(),
        )
    }

    fun build(context: Context, progress: ScanProgress): Notification {
        ensureChannel(context)
        val text = if (progress.total > 0) {
            context.getString(R.string.ingest_scan_notification_progress, progress.scanned, progress.total, progress.found)
        } else {
            context.getString(R.string.ingest_scan_notification_starting)
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.ingest_scan_notification_title))
            .setContentText(text)
            .setProgress(progress.total, progress.scanned.coerceAtMost(progress.total), progress.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .build()
    }

    fun foregroundInfo(context: Context, progress: ScanProgress): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, build(context, progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
