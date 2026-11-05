package com.foldfinance.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.ScanState
import com.foldfinance.app.ingest.SystemSettingsIntents
import com.foldfinance.app.ingest.push.ListenerConnection
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.SectionHeader
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/** Sheets the settings block can open. */
private enum class IngestSheet { None, Sources, Battery, SmsRestricted }

/**
 * Блок экрана настроек «Доступы и источники»: статусы разрешений (SMS, доступ к уведомлениям,
 * POST_NOTIFICATIONS), здоровье слушателя с «Переподключить» (`requestRebind`), источники
 * уведомлений (выбор в нижнем листе), «Сканировать SMS заново» (идемпотентно) и инструкция про
 * батарею Samsung. Рассчитан на встраивание в прокручиваемый экран настроек: сам не скроллится
 * и не использует ленивых списков (кроме листа выбора источников).
 */
@Composable
fun IngestSettingsSection(modifier: Modifier = Modifier) {
    val viewModel: IngestSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf(IngestSheet.None) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.refresh()
        if (!result.values.all { it } || result.isEmpty()) sheet = IngestSheet.SmsRestricted
    }
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }

    IngestSettingsContent(
        state = state,
        modifier = modifier,
        onRequestSms = { smsLauncher.launch(IngestPermissions.SMS_PERMISSIONS) },
        onOpenNotificationAccess = { SystemSettingsIntents.launch(context, SystemSettingsIntents.notificationListener(context)) },
        onRebind = viewModel::rebindListener,
        onClearPushFailures = viewModel::clearPushFailures,
        onRequestPostNotifications = {
            if (IngestPermissions.needsPostNotificationsRequest) postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onOpenSources = {
            viewModel.openSources()
            sheet = IngestSheet.Sources
        },
        onRescan = viewModel::rescan,
        onOpenBattery = { sheet = IngestSheet.Battery },
    )

    when (sheet) {
        IngestSheet.None -> Unit
        IngestSheet.Sources -> IngestBottomSheet(onDismiss = { sheet = IngestSheet.None }) {
            SectionHeader(title = stringResource(R.string.onboarding_sources_title))
            Text(
                stringResource(R.string.onboarding_sources_body),
                style = MaterialTheme.typography.bodyMedium,
                color = FoldFinanceTheme.palette.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            NotificationSourcesList(
                sources = state.sources,
                onToggle = viewModel::toggleSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .weight(1f, fill = false),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                stringResource(R.string.onboarding_sources_save),
                onClick = {
                    viewModel.saveSources()
                    sheet = IngestSheet.None
                },
                enabled = state.sources.apps != null,
            )
        }
        IngestSheet.Battery -> IngestBottomSheet(onDismiss = { sheet = IngestSheet.None }) {
            SectionHeader(title = stringResource(R.string.onboarding_battery_title))
            Text(
                stringResource(R.string.onboarding_battery_body),
                style = MaterialTheme.typography.bodyMedium,
                color = FoldFinanceTheme.palette.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            InstructionList(listOf(stringResource(R.string.onboarding_battery_step1), stringResource(R.string.onboarding_battery_step2)))
            Spacer(Modifier.height(16.dp))
            PrimaryAction(stringResource(R.string.onboarding_battery_action), onClick = {
                SystemSettingsIntents.launch(context, SystemSettingsIntents.battery(context))
            })
        }
        IngestSheet.SmsRestricted -> IngestBottomSheet(onDismiss = { sheet = IngestSheet.None }) {
            SectionHeader(title = stringResource(R.string.onboarding_restricted_title))
            Text(
                stringResource(R.string.onboarding_restricted_body),
                style = MaterialTheme.typography.bodyMedium,
                color = FoldFinanceTheme.palette.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            InstructionList(
                listOf(
                    stringResource(R.string.onboarding_restricted_step1),
                    stringResource(R.string.onboarding_restricted_step2),
                    stringResource(R.string.onboarding_restricted_step3),
                    stringResource(R.string.onboarding_restricted_step4),
                ),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryAction(stringResource(R.string.onboarding_open_app_details), onClick = {
                SystemSettingsIntents.launch(context, SystemSettingsIntents.appDetails(context))
                sheet = IngestSheet.None
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngestBottomSheet(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = FoldShapes.sheet,
        containerColor = FoldFinanceTheme.palette.ink,
        contentColor = FoldFinanceTheme.palette.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            content = content,
        )
    }
}

/** Stateless body of [IngestSettingsSection] (also used by screenshot tests). */
@Composable
fun IngestSettingsContent(
    state: IngestSettingsUiState,
    modifier: Modifier = Modifier,
    onRequestSms: () -> Unit = {},
    onOpenNotificationAccess: () -> Unit = {},
    onRebind: () -> Unit = {},
    onClearPushFailures: () -> Unit = {},
    onRequestPostNotifications: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    onRescan: () -> Unit = {},
    onOpenBattery: () -> Unit = {},
) {
    val palette = FoldFinanceTheme.palette
    val perms = state.permissions
    Column(modifier) {
        SectionHeader(
            title = stringResource(R.string.ingest_settings_title),
            supporting = stringResource(R.string.ingest_settings_supporting),
        )
        Spacer(Modifier.height(8.dp))
        ListGroup(Modifier.fillMaxWidth()) {
            IngestRow(
                icon = Icons.Outlined.Sms,
                title = stringResource(R.string.ingest_settings_sms),
                supporting = stringResource(if (perms.sms) R.string.ingest_status_granted else R.string.ingest_status_denied),
                tint = if (perms.sms) palette.mint else palette.saffron,
            ) {
                if (!perms.sms) RowAction(stringResource(R.string.ingest_action_allow), onRequestSms)
            }
            ListGroupDivider()
            IngestRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.ingest_settings_push_access),
                supporting = stringResource(
                    if (perms.notificationAccess) R.string.ingest_status_enabled else R.string.ingest_status_disabled,
                ),
                tint = if (perms.notificationAccess) palette.mint else palette.saffron,
            ) {
                RowAction(stringResource(R.string.ingest_action_open), onOpenNotificationAccess)
            }
            ListGroupDivider()
            val connected = state.listener == ListenerConnection.CONNECTED
            IngestRow(
                icon = Icons.Outlined.Hub,
                title = stringResource(R.string.ingest_settings_listener),
                supporting = stringResource(
                    when (state.listener) {
                        ListenerConnection.CONNECTED -> R.string.ingest_status_connected
                        ListenerConnection.DISCONNECTED -> R.string.ingest_status_disconnected
                        ListenerConnection.UNKNOWN -> R.string.ingest_status_not_bound
                    },
                ),
                tint = if (connected) palette.mint else palette.saffron,
            ) {
                if (perms.notificationAccess && !connected) RowAction(stringResource(R.string.ingest_action_rebind), onRebind)
            }
            val failures = state.pushFailures
            if (failures.count > 0) {
                ListGroupDivider()
                IngestRow(
                    icon = Icons.Outlined.ErrorOutline,
                    title = pluralStringResource(R.plurals.ingest_push_failures_title, failures.count, failures.count),
                    supporting = stringResource(R.string.ingest_push_failures_body, failures.stored, failures.lost),
                    tint = palette.alert,
                ) {
                    RowAction(stringResource(R.string.ingest_action_dismiss), onClearPushFailures)
                }
            }
            if (IngestPermissions.needsPostNotificationsRequest) {
                ListGroupDivider()
                IngestRow(
                    icon = Icons.Outlined.Notifications,
                    title = stringResource(R.string.ingest_settings_post_notifications),
                    supporting = stringResource(
                        if (perms.postNotifications) R.string.ingest_status_granted else R.string.ingest_status_denied,
                    ),
                    tint = if (perms.postNotifications) palette.mint else palette.saffron,
                ) {
                    if (!perms.postNotifications) RowAction(stringResource(R.string.ingest_action_allow), onRequestPostNotifications)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ListGroup(Modifier.fillMaxWidth()) {
            IngestRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.ingest_settings_sources),
                supporting = if (state.whitelistSize == 0) {
                    stringResource(R.string.ingest_status_no_sources)
                } else {
                    pluralStringResource(R.plurals.ingest_status_sources, state.whitelistSize, state.whitelistSize)
                },
                onClick = onOpenSources,
            ) {
                RowAction(stringResource(R.string.ingest_action_choose), onOpenSources)
            }
            ListGroupDivider()
            val scan = state.scan
            IngestRow(
                icon = Icons.Outlined.Sync,
                title = stringResource(R.string.ingest_settings_rescan),
                supporting = when {
                    scan is ScanState.Running && scan.progress.total > 0 ->
                        stringResource(R.string.ingest_settings_rescan_running, scan.progress.scanned, scan.progress.total)
                    scan is ScanState.Running -> stringResource(R.string.ingest_settings_rescan_queued)
                    else -> stringResource(R.string.ingest_settings_rescan_body)
                },
            ) {
                if (perms.sms && scan !is ScanState.Running) RowAction(stringResource(R.string.ingest_action_scan), onRescan)
            }
            ListGroupDivider()
            IngestRow(
                icon = Icons.Outlined.BatteryChargingFull,
                title = stringResource(R.string.ingest_settings_battery),
                supporting = stringResource(
                    if (perms.batteryUnrestricted) R.string.onboarding_battery_ok else R.string.onboarding_battery_limited,
                ),
                tint = if (perms.batteryUnrestricted) palette.mint else palette.saffron,
                onClick = onOpenBattery,
            ) {
                RowAction(stringResource(R.string.ingest_action_how), onOpenBattery)
            }
        }
    }
}
