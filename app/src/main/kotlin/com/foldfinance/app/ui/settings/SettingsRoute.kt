package com.foldfinance.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.backup.BackupManager
import com.foldfinance.app.ingest.debug.IngestDebugTools
import com.foldfinance.app.prefs.RelockTimeout
import com.foldfinance.app.prefs.ThemeMode
import com.foldfinance.app.ui.categories.CategoriesRoute
import com.foldfinance.app.ui.diagnostics.WindowDiagnosticsRoute
import com.foldfinance.app.ui.onboarding.IngestSettingsSection
import com.foldfinance.app.ui.shell.LocalShellNavigation
import com.foldfinance.app.ui.shell.Section
import java.time.LocalDate

/**
 * «Настройки» — точка входа раздела: доступы и источники, мои карты, валюта, категории,
 * безопасность, резервная копия, оформление; в debug — «Диагностика окна».
 *
 * Выбор файла бэкапа — SAF (`CreateDocument` / `OpenDocument`); пароль спрашивается уже после
 * выбора файла и не сохраняется.
 */
@Composable
fun SettingsRoute(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPane.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shell = LocalShellNavigation.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDeviceAuth() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri ->
        viewModel.onDocumentPicked(BackupKind.EXPORT, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onDocumentPicked(BackupKind.IMPORT, uri)
    }

    val actions = remember(viewModel, context) {
        object : SettingsActions {
            override fun selectPane(pane: SettingsPane?) = viewModel.selectPane(pane)
            override fun setAutoTracking(enabled: Boolean) = viewModel.setAutoTracking(enabled)
            override fun setOwnCard(mask: String, own: Boolean) = viewModel.setOwnCard(mask, own)
            override fun setCardLabel(mask: String, label: String) = viewModel.setCardLabel(mask, label)
            override fun setDefaultCurrency(currency: String) = viewModel.setDefaultCurrency(currency)
            override fun setLockEnabled(enabled: Boolean) = viewModel.setLockEnabled(enabled)
            override fun setRelockTimeout(timeout: RelockTimeout) = viewModel.setRelockTimeout(timeout)
            override fun setSecureWindow(enabled: Boolean) = viewModel.setSecureWindow(enabled)
            override fun setHideWidgetAmounts(hide: Boolean) = viewModel.setHideWidgetAmounts(hide)
            override fun setThemeMode(mode: ThemeMode) = viewModel.setThemeMode(mode)
            override fun setDynamicColor(enabled: Boolean) = viewModel.setDynamicColor(enabled)
            override fun dismissPassword() = viewModel.onPasswordDismissed()
            override fun submitPassword(password: CharArray) = viewModel.onPasswordEntered(password)

            override fun startExport() {
                viewModel.launchSystemUi {
                    exportLauncher.launch(BackupManager.suggestedFileName(LocalDate.now()))
                }
            }

            override fun startImport() {
                viewModel.launchSystemUi {
                    importLauncher.launch(arrayOf(BackupManager.MIME_TYPE, "*/*"))
                }
            }

            override fun openSecuritySettings() {
                viewModel.launchSystemUi { startSecuritySettings(context) }
            }
        }
    }

    SettingsScreen(
        state = state,
        selected = selected,
        actions = actions,
        modifier = modifier,
        ingestSection = {
            Column {
                IngestSettingsSection()
                // Только debug: тестовая SMS и тестовое банковское уведомление. В release пусто.
                IngestDebugTools()
            }
        },
        categories = { onBack -> CategoriesRoute(onBack = onBack) },
        diagnostics = { onBack -> WindowDiagnosticsRoute(modifier, onBack = onBack) },
        onOpenSubscriptions = { shell.openSection(Section.Subscriptions) },
    )
}

/**
 * Запуск системного экрана. Если его нет на телефоне, кнопка ничего не делает, а льгота
 * блокировки, выданная перед запуском, отменяется, чтобы не «залипнуть».
 */
private inline fun SettingsViewModel.launchSystemUi(block: () -> Unit) {
    onLeavingForSystemUi()
    try {
        block()
    } catch (_: ActivityNotFoundException) {
        onSystemUiLaunchFailed()
    }
}

private fun startSecuritySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
