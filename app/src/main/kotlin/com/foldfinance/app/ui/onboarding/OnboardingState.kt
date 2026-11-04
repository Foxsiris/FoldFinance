package com.foldfinance.app.ui.onboarding

import androidx.compose.runtime.Immutable
import com.foldfinance.app.ingest.PermissionSnapshot
import com.foldfinance.app.ingest.ScanState
import com.foldfinance.app.ingest.push.InstalledApp

/** Steps of the access onboarding, in order. Each one can be skipped. */
enum class OnboardingStep {
    /** Why SMS access is needed → READ_SMS / RECEIVE_SMS. */
    SmsIntro,

    /** Shown only after a denial: «Разрешить ограниченные настройки» for a sideloaded APK. */
    SmsRestricted,

    /** Notification access (system screen), with the same restricted-settings hint. */
    NotificationAccess,

    /** Which apps' notifications are read (the whitelist). */
    NotificationSources,

    /** POST_NOTIFICATIONS for the scan progress notification (Android 13+ only). */
    PostNotifications,

    /** Samsung battery: «Без ограничений» / «Приложения без спящего режима». */
    Battery,

    /** First import of the SMS history with live counters. */
    Scan,
    ;

    companion object {
        /** Steps of this device; [SmsRestricted] is a detour, not counted. */
        fun flow(needsPostNotifications: Boolean): List<OnboardingStep> =
            entries.filter { it != SmsRestricted && (it != PostNotifications || needsPostNotifications) }
    }
}

/** Notification-source picker state. [apps] is `null` while loading. */
@Immutable
data class SourcesState(
    val apps: List<InstalledApp>? = null,
    val selected: Set<String> = emptySet(),
)

/** Everything the onboarding screen draws. */
@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SmsIntro,
    val stepNumber: Int = 1,
    val stepCount: Int = 1,
    val permissions: PermissionSnapshot = PermissionSnapshot.NONE,
    val sources: SourcesState = SourcesState(),
    val scan: ScanState = ScanState.Idle,
)

/** User actions of the onboarding screen. */
interface OnboardingActions {
    fun next()
    fun skip()
    fun requestSms()
    fun openAppDetails()
    fun openNotificationAccess()
    fun toggleSource(packageName: String)
    fun saveSources()
    fun requestPostNotifications()
    fun openBatterySettings()
    fun startScan()
    fun finish()

    /** No-op actions for previews and screenshots. */
    object None : OnboardingActions {
        override fun next() = Unit
        override fun skip() = Unit
        override fun requestSms() = Unit
        override fun openAppDetails() = Unit
        override fun openNotificationAccess() = Unit
        override fun toggleSource(packageName: String) = Unit
        override fun saveSources() = Unit
        override fun requestPostNotifications() = Unit
        override fun openBatterySettings() = Unit
        override fun startScan() = Unit
        override fun finish() = Unit
    }
}
