package com.foldfinance.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.SystemSettingsIntents
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Entry point of the access onboarding: why SMS → READ_SMS/RECEIVE_SMS → restricted settings
 * (sideload) → notification access → notification sources → POST_NOTIFICATIONS → Samsung battery →
 * first SMS import with live progress. Every step can be skipped.
 *
 * The shell shows this route until [onFinished] is called. If the onboarding was already
 * completed earlier (DataStore flag), [onFinished] is called right away and nothing is drawn.
 * Permissions are re-checked on every ON_RESUME, i.e. when the user comes back from system settings.
 */
@Composable
fun OnboardingRoute(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val alreadyCompleted by viewModel.alreadyCompleted.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(alreadyCompleted, finished) {
        if (alreadyCompleted == true || finished) currentOnFinished()
    }
    if (alreadyCompleted != false) {
        // Unknown yet, or done: an empty ink page for the one frame before the shell takes over.
        Box(modifier.fillMaxSize().background(FoldFinanceTheme.palette.ink))
        return
    }

    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onSmsPermissionResult(result.isNotEmpty() && result.values.all { it })
    }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onPostNotificationsResult()
    }

    val actions = remember(viewModel, context) {
        object : OnboardingActions {
            override fun next() = viewModel.next()
            override fun skip() = viewModel.next()
            override fun requestSms() = smsLauncher.launch(IngestPermissions.SMS_PERMISSIONS)
            override fun openAppDetails() {
                viewModel.onOpenedSettings()
                SystemSettingsIntents.launch(context, SystemSettingsIntents.appDetails(context))
            }
            override fun openNotificationAccess() {
                viewModel.onOpenedSettings()
                SystemSettingsIntents.launch(context, SystemSettingsIntents.notificationListener(context))
            }
            override fun toggleSource(packageName: String) = viewModel.toggleSource(packageName)
            override fun saveSources() = viewModel.saveSources()
            override fun requestPostNotifications() {
                if (IngestPermissions.needsPostNotificationsRequest) {
                    postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onPostNotificationsResult()
                }
            }
            override fun openBatterySettings() {
                viewModel.onOpenedSettings()
                SystemSettingsIntents.launch(context, SystemSettingsIntents.battery(context))
            }
            override fun startScan() = viewModel.startScan()
            override fun finish() = viewModel.finish()
        }
    }

    OnboardingScreen(state = state, actions = actions, modifier = modifier.background(FoldFinanceTheme.palette.ink))
}
