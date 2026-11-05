package com.foldfinance.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.backup.BackupError
import com.foldfinance.app.backup.BackupManager
import com.foldfinance.app.backup.BackupResult
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.prefs.RelockTimeout
import com.foldfinance.app.prefs.ThemeMode
import com.foldfinance.app.security.AppLock
import com.foldfinance.app.security.DeviceAuthChecker
import com.foldfinance.app.security.DeviceAuthStatus
import com.foldfinance.app.widget.FinanceWidgetUpdater
import com.foldfinance.core.data.repository.CardRepository
import com.foldfinance.core.data.settings.IngestSettings
import com.foldfinance.core.model.Card
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Разделы настроек в порядке списка. [DIAGNOSTICS] есть только в debug. */
enum class SettingsPane { SOURCES, CARDS, CURRENCY, CATEGORIES, AI, SECURITY, BACKUP, APPEARANCE, DIAGNOSTICS;

    companion object {
        fun visible(debug: Boolean = BuildConfig.DEBUG): List<SettingsPane> = entries.filter { debug || it != DIAGNOSTICS }
    }
}

/** Какая операция бэкапа. */
enum class BackupKind { EXPORT, IMPORT }

/** Ход последней операции бэкапа (для индикатора и итогового сообщения). */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data class Working(val kind: BackupKind) : BackupStatus
    data class Done(val kind: BackupKind, val transactions: Int) : BackupStatus
    data class Failed(val kind: BackupKind, val error: BackupError) : BackupStatus
}

/** Открытый диалог пароля: файл уже выбран через SAF. Сам пароль сюда не попадает никогда. */
data class PasswordRequest(val kind: BackupKind, val uri: Uri)

/** Всё, что показывает экран настроек. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val autoTracking: Boolean = true,
    val cards: List<Card> = emptyList(),
    val deviceAuth: DeviceAuthStatus = DeviceAuthStatus.AVAILABLE,
    val backup: BackupStatus = BackupStatus.Idle,
    val passwordRequest: PasswordRequest? = null,
)

/**
 * Экран настроек. Выбранный раздел и открытый диалог пароля лежат в [SavedStateHandle] —
 * переживают складывание, поворот и смерть процесса; пароль — нет (только `CharArray`
 * в момент нажатия, затирается [BackupManager]).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferences: AppPreferences,
    private val ingestSettings: IngestSettings,
    private val cards: CardRepository,
    private val backupManager: BackupManager,
    private val deviceAuthChecker: DeviceAuthChecker,
    private val appLock: AppLock,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val deviceAuth = MutableStateFlow(deviceAuthChecker.status())
    private val backup = MutableStateFlow<BackupStatus>(BackupStatus.Idle)

    /** Выбранный раздел; `null` — на внешнем экране показан список разделов. */
    val selectedPane: StateFlow<SettingsPane?> = savedStateHandle.getStateFlow<String?>(KEY_PANE, null)
        .map(::resolvePane)
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolvePane(savedStateHandle[KEY_PANE]))

    private fun resolvePane(name: String?): SettingsPane? = SettingsPane.visible().firstOrNull { it.name == name }

    val state: StateFlow<SettingsUiState> = combine(
        preferences.settings,
        ingestSettings.autoTrackingEnabled,
        cards.observeAll(),
        deviceAuth,
        combine(
            backup,
            savedStateHandle.getStateFlow<String?>(KEY_REQUEST_KIND, null),
            savedStateHandle.getStateFlow<Uri?>(KEY_REQUEST_URI, null),
        ) { b, _, _ -> b to passwordRequest() },
    ) { settings, tracking, cardList, auth, (backupStatus, request) ->
        SettingsUiState(settings, tracking, cardList, auth, backupStatus, request)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun selectPane(pane: SettingsPane?) {
        savedStateHandle[KEY_PANE] = pane?.name
    }

    /** Экран снова виден (вернулись из системных настроек): перепроверить PIN/биометрию. */
    fun refreshDeviceAuth() {
        deviceAuth.value = deviceAuthChecker.status()
    }

    fun setAutoTracking(enabled: Boolean) = launch { ingestSettings.setAutoTrackingEnabled(enabled) }

    fun setDefaultCurrency(currency: String) = launch {
        preferences.setDefaultCurrency(currency)
        FinanceWidgetUpdater.requestUpdate(context)
    }

    fun setLockEnabled(enabled: Boolean) = launch { preferences.setLockEnabled(enabled) }

    fun setRelockTimeout(timeout: RelockTimeout) = launch { preferences.setRelockTimeout(timeout) }

    fun setSecureWindow(enabled: Boolean) = launch { preferences.setSecureWindow(enabled) }

    fun setHideWidgetAmounts(hide: Boolean) = launch {
        preferences.setHideWidgetAmounts(hide)
        FinanceWidgetUpdater.requestUpdate(context)
    }

    fun setThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launch { preferences.setDynamicColor(enabled) }

    /**
     * Сейчас откроется системный экран (выбор файла, настройки телефона): возвращение из него
     * не должно сразу блокировать приложение.
     */
    fun onLeavingForSystemUi() = appLock.allowExternalActivity()

    /** Системный экран не открылся: льгота блокировки не нужна. */
    fun onSystemUiLaunchFailed() = appLock.cancelExternalActivity()

    /** Файл выбран (SAF): спросить пароль. `null` — пользователь закрыл окно выбора. */
    fun onDocumentPicked(kind: BackupKind, uri: Uri?) {
        if (uri == null) return
        backup.value = BackupStatus.Idle
        savedStateHandle[KEY_REQUEST_URI] = uri
        savedStateHandle[KEY_REQUEST_KIND] = kind.name
    }

    /** Пользователь закрыл диалог пароля. Пустой файл, созданный для экспорта, удаляется. */
    fun onPasswordDismissed() {
        val request = passwordRequest() ?: return
        clearPasswordRequest()
        if (request.kind == BackupKind.EXPORT) launch { backupManager.discardCreated(request.uri) }
    }

    /** Запустить экспорт/импорт. [password] затирается в любом исходе. */
    fun onPasswordEntered(password: CharArray) {
        val request = passwordRequest()
        if (request == null || backup.value is BackupStatus.Working) {
            password.fill('\u0000')
            return
        }
        clearPasswordRequest()
        backup.value = BackupStatus.Working(request.kind)
        launch {
            val result = when (request.kind) {
                BackupKind.EXPORT -> backupManager.export(request.uri, password)
                BackupKind.IMPORT -> backupManager.import(request.uri, password)
            }
            backup.value = when (result) {
                is BackupResult.Success -> BackupStatus.Done(request.kind, result.transactions)
                is BackupResult.Failure -> BackupStatus.Failed(request.kind, result.error)
            }
        }
    }

    private fun passwordRequest(): PasswordRequest? {
        val kind = BackupKind.entries.firstOrNull { it.name == savedStateHandle.get<String>(KEY_REQUEST_KIND) } ?: return null
        val uri = savedStateHandle.get<Uri>(KEY_REQUEST_URI) ?: return null
        return PasswordRequest(kind, uri)
    }

    private fun clearPasswordRequest() {
        savedStateHandle[KEY_REQUEST_KIND] = null
        savedStateHandle[KEY_REQUEST_URI] = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val KEY_PANE = "settings.selectedPane"
        const val KEY_REQUEST_KIND = "settings.passwordRequest.kind"
        const val KEY_REQUEST_URI = "settings.passwordRequest.uri"

        /** Минимальная длина пароля бэкапа: файл можно перебирать офлайн. */
        const val MIN_PASSWORD_LENGTH = 8
    }
}
