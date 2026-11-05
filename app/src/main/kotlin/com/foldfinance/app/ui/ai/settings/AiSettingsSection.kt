package com.foldfinance.app.ui.ai.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.settings.SettingsActionRow
import com.foldfinance.app.ui.settings.SettingsIntro
import com.foldfinance.app.ui.settings.SettingsSwitchRow
import com.foldfinance.app.ui.theme.FoldFinanceTheme
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

/**
 * Без ViewModel — для превью и скриншот-тестов. Оформление как у остальных разделов настроек:
 * группа с переключателем, статусом модели и действием загрузки, под ней — пояснение.
 */
@Composable
fun AiSettingsContent(
    state: AiSettingsState,
    onEnabledChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ListGroup(Modifier.fillMaxWidth()) {
            SettingsSwitchRow(
                title = stringResource(R.string.ai_settings_toggle),
                body = stringResource(R.string.ai_settings_toggle_body),
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
            )
            ListGroupDivider(startInset = 16.dp)
            StatusLine(state.enabled, state.status)
            if (state.canDownload) {
                ListGroupDivider()
                val retry = state.status is AiStatus.Unavailable
                SettingsActionRow(
                    icon = Icons.Outlined.Download,
                    title = stringResource(if (retry) R.string.ai_action_retry else R.string.ai_action_download),
                    body = stringResource(R.string.ai_action_download_body),
                    enabled = true,
                    onClick = onDownload,
                )
            }
        }
        SettingsIntro(stringResource(R.string.ai_settings_privacy))
    }
}

@Composable
private fun StatusLine(enabled: Boolean, status: AiStatus) {
    val context = LocalContext.current
    val palette = FoldFinanceTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
        val ready = enabled && status == AiStatus.Ready
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (ready) palette.income else palette.textDisabled, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.text)
        }
        if (enabled && status is AiStatus.Downloading) {
            val total = status.totalBytes
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (status.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.saffron,
                    trackColor = palette.surfaceRaised,
                )
            } else if (!FoldFinanceTheme.reducedMotion) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.saffron, trackColor = palette.surfaceRaised)
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
