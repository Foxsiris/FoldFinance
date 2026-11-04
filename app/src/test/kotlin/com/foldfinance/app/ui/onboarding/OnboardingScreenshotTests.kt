package com.foldfinance.app.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ingest.PermissionSnapshot
import com.foldfinance.app.ingest.ScanState
import com.foldfinance.app.ingest.push.InstalledApp
import com.foldfinance.app.ingest.push.ListenerConnection
import com.foldfinance.app.ingest.sms.ScanProgress
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Onboarding steps and the scan progress in 3 Fold configurations × light/dark
 * (`src/test/screenshots/onboarding_*`). Record: `recordRoborazziDebug`, check: `verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenshotTests {

    private val apps = listOf(
        InstalledApp("ru.sberbankmobile", "СберБанк", isKnownBank = true),
        InstalledApp("com.idamob.tinkoff.android", "Т-Банк", isKnownBank = true),
        InstalledApp("com.android.chrome", "Chrome", isKnownBank = false),
        InstalledApp("ru.yandex.taxi", "Яндекс Go", isKnownBank = false),
        InstalledApp("org.telegram.messenger", "Telegram", isKnownBank = false),
    )

    private fun state(step: OnboardingStep, permissions: PermissionSnapshot = PermissionSnapshot.NONE, scan: ScanState = ScanState.Idle) =
        OnboardingUiState(
            step = step,
            stepNumber = OnboardingStep.flow(true).indexOf(if (step == OnboardingStep.SmsRestricted) OnboardingStep.SmsIntro else step) + 1,
            stepCount = OnboardingStep.flow(true).size,
            permissions = permissions,
            sources = SourcesState(apps, setOf("ru.sberbankmobile")),
            scan = scan,
        )

    private val smsOnly = PermissionSnapshot.NONE.copy(sms = true)

    private val midScan = ScanProgress(
        scanned = 1_240, total = 3_512, found = 318,
        incomeCount = 14, expenseCount = 296,
        incomeRubMinor = 18_450_000, expenseRubMinor = 9_731_250,
    )

    @Test fun smsIntro() = captureInAllConfigs("onboarding_sms") {
        OnboardingScreen(state(OnboardingStep.SmsIntro), OnboardingActions.None, layout = layout)
    }

    @Test fun smsRestricted() = captureInAllConfigs("onboarding_sms_restricted") {
        OnboardingScreen(state(OnboardingStep.SmsRestricted), OnboardingActions.None, layout = layout)
    }

    @Test fun notificationAccess() = captureInAllConfigs("onboarding_push_access") {
        OnboardingScreen(state(OnboardingStep.NotificationAccess, smsOnly), OnboardingActions.None, layout = layout)
    }

    @Test fun notificationSources() = captureInAllConfigs("onboarding_sources") {
        OnboardingScreen(state(OnboardingStep.NotificationSources, smsOnly), OnboardingActions.None, layout = layout)
    }

    @Test fun postNotifications() = captureInAllConfigs("onboarding_post_notifications") {
        OnboardingScreen(state(OnboardingStep.PostNotifications, smsOnly), OnboardingActions.None, layout = layout)
    }

    @Test fun battery() = captureInAllConfigs("onboarding_battery") {
        OnboardingScreen(state(OnboardingStep.Battery, smsOnly), OnboardingActions.None, layout = layout)
    }

    @Test fun scanRunning() = captureInAllConfigs("onboarding_scan_running") {
        OnboardingScreen(state(OnboardingStep.Scan, smsOnly, ScanState.Running(midScan)), OnboardingActions.None, layout = layout)
    }

    @Test fun scanFinished() = captureInAllConfigs("onboarding_scan_finished") {
        val done = midScan.copy(scanned = 3_512, found = 902, incomeCount = 41, expenseCount = 840, incomeRubMinor = 52_300_000, expenseRubMinor = 27_984_590)
        OnboardingScreen(state(OnboardingStep.Scan, smsOnly, ScanState.Finished(done)), OnboardingActions.None, layout = layout)
    }

    @Test fun scanWithoutSms() = captureInAllConfigs("onboarding_scan_no_sms") {
        OnboardingScreen(state(OnboardingStep.Scan), OnboardingActions.None, layout = layout)
    }

    /** The settings block as the settings screen embeds it (single column, inside a scroll). */
    @Test fun settingsSection() = captureInAllConfigs("onboarding_settings_section") {
        IngestSettingsContent(
            state = IngestSettingsUiState(
                permissions = PermissionSnapshot(sms = true, notificationAccess = true, postNotifications = false, batteryUnrestricted = false),
                listener = ListenerConnection.DISCONNECTED,
                whitelistSize = 2,
                scan = ScanState.Running(midScan),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}
