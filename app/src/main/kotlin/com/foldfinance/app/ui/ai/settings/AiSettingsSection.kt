package com.foldfinance.app.ui.ai.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.R
import com.foldfinance.app.ai.AiFailureReasons
import com.foldfinance.app.ai.AiStatus
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.backfill.AiBackfillScheduler
import com.foldfinance.app.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние блока «Локальный ИИ». */
data class AiSettingsState(
    val enabled: Boolean = true,
    val status: AiStatus = AiStatus.Checking,
) {
    /** Кнопка загрузки: модель можно скачать или прошлая загрузка не удалась. */
    val canDownload: Boolean
        get() = enabled && (
            status is AiStatus.Downloadable ||
                (status is AiStatus.Unavailable &&
                    status.reason in setOf(AiFailureReasons.DOWNLOAD_FAILED, AiFailureReasons.NOT_ENOUGH_DISK))
            )
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val ai: LocalAi,
    private val prefs: AppPreferences,
    scheduler: AiBackfillScheduler,
) : ViewModel() {

    init {
        scheduler.start()
    }

    val state: StateFlow<AiSettingsState> = combine(prefs.localAiEnabled, ai.status) { enabled, status ->
        AiSettingsState(enabled, status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AiSettingsState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setLocalAiEnabled(enabled) }
    }

    /** Загрузку ведёт AICore в scope ИИ: уход с экрана её не прерывает. */
    fun download() {
        viewModelScope.launch { ai.prepare() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Блок настроек «Локальный ИИ»: переключатель, статус модели, прогресс и кнопка загрузки,
 * пояснение про приватность. Встраивается в экран настроек как есть.
 */
@Composable
fun AiSettingsSection(modifier: Modifier = Modifier, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AiSettingsContent(state = state, onEnabledChange = viewModel::setEnabled, onDownload = viewModel::download, modifier = modifier)
}

/** Без ViewModel — для превью и скриншот-тестов. */
@Composable
fun AiSettingsContent(
    state: AiSettingsState,
    onEnabledChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_settings_toggle), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ai_settings_toggle_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
        }

        StatusLine(state.enabled, state.status)

        if (state.canDownload) {
            FilledTonalButton(onClick = onDownload) {
                val retry = state.status is AiStatus.Unavailable
                Text(stringResource(if (retry) R.string.ai_action_retry else R.string.ai_action_download))
            }
        }

        Text(
            stringResource(R.string.ai_settings_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusLine(enabled: Boolean, status: AiStatus) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val text = when {
            !enabled -> stringResource(R.string.ai_status_disabled)
            else -> when (status) {
                AiStatus.Checking -> stringResource(R.string.ai_status_checking)
                AiStatus.Ready -> stringResource(R.string.ai_status_ready)
                AiStatus.Downloadable -> stringResource(R.string.ai_status_downloadable)
                is AiStatus.Downloading -> when {
                    status.totalBytes != null -> stringResource(
                        R.string.ai_status_downloading_progress,
                        Formatter.formatShortFileSize(context, status.bytesDownloaded),
                        Formatter.formatShortFileSize(context, status.totalBytes),
                    )
                    status.bytesDownloaded > 0 -> stringResource(
                        R.string.ai_status_downloading_bytes,
                        Formatter.formatShortFileSize(context, status.bytesDownloaded),
                    )
                    else -> stringResource(R.string.ai_status_downloading)
                }
                is AiStatus.Unavailable -> unavailableText(status.reason)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (enabled && status is AiStatus.Downloading) {
            val total = status.totalBytes
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (status.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun unavailableText(reason: String): String = when (reason) {
    AiFailureReasons.DISABLED -> stringResource(R.string.ai_status_disabled)
    AiFailureReasons.NOT_SUPPORTED -> stringResource(R.string.ai_status_not_supported)
    AiFailureReasons.NEEDS_SYSTEM_UPDATE, AiFailureReasons.AICORE_INCOMPATIBLE -> stringResource(R.string.ai_status_needs_update)
    AiFailureReasons.DOWNLOAD_FAILED -> stringResource(R.string.ai_status_download_failed)
    AiFailureReasons.NOT_ENOUGH_DISK -> stringResource(R.string.ai_status_no_space)
    else -> stringResource(R.string.ai_status_error, reason)
}
