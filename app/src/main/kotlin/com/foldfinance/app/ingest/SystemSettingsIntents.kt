package com.foldfinance.app.ingest

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.foldfinance.app.ingest.push.ListenerHealth

/**
 * System settings screens the onboarding and settings lead to. Only public intents; each has a
 * fallback because OEM builds do not always export every screen.
 */
object SystemSettingsIntents {

    /**
     * «Сведения о приложении». Here the ⋮ menu has «Разрешить ограниченные настройки» for a
     * sideloaded APK, the permission switches, and «Батарея → Без ограничений».
     */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** Our own switch in «Доступ к уведомлениям» (falls back to the list of listeners). */
    fun notificationListener(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ListenerHealth.componentName(context).flattenToString(),
            ),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    /**
     * Battery settings of the app. Android has no public per-app battery screen, so this is
     * «Сведения о приложении» (→ «Батарея» → «Без ограничений»), then the system list of battery
     * optimisation exceptions. Requesting the exemption directly would need
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which the app does not declare.
     */
    fun battery(context: Context): List<Intent> = listOf(
        appDetails(context),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    /** Starts the first intent that resolves; `false` if none did. */
    fun launch(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            val flagged = if (context is android.app.Activity) intent else Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(flagged)
                return true
            } catch (_: ActivityNotFoundException) {
                // try the next one
            } catch (_: SecurityException) {
                // some OEM screens are not exported
            }
        }
        return false
    }

    fun launch(context: Context, intent: Intent): Boolean = launch(context, listOf(intent))
}
