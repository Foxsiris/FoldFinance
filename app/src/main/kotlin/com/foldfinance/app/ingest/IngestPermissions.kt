package com.foldfinance.app.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of everything the ingest layer needs from the system. */
data class PermissionSnapshot(
    /** READ_SMS and RECEIVE_SMS are both granted. */
    val sms: Boolean,
    /** The user enabled our notification listener in system settings. */
    val notificationAccess: Boolean,
    /** POST_NOTIFICATIONS (always `true` before Android 13). */
    val postNotifications: Boolean,
    /** Battery optimisation is off for the app («Без ограничений»). */
    val batteryUnrestricted: Boolean,
) {
    companion object {
        val NONE = PermissionSnapshot(sms = false, notificationAccess = false, postNotifications = false, batteryUnrestricted = false)
    }
}

/** Reads permission state. Never asks for anything: requests go through ActivityResult in the UI. */
@Singleton
class IngestPermissions @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasSms(): Boolean = SMS_PERMISSIONS.all { granted(it) }

    fun hasNotificationAccess(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun hasPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)

    fun isBatteryUnrestricted(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun snapshot(): PermissionSnapshot = PermissionSnapshot(
        sms = hasSms(),
        notificationAccess = hasNotificationAccess(),
        postNotifications = hasPostNotifications(),
        batteryUnrestricted = isBatteryUnrestricted(),
    )

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        val SMS_PERMISSIONS: Array<String> = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

        /** POST_NOTIFICATIONS exists (and must be requested) only from Android 13. */
        val needsPostNotificationsRequest: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
