package com.foldfinance.app.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.backup.BackupError
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ai.AiStatus
import com.foldfinance.app.ui.Shell
import com.foldfinance.app.ui.ai.settings.AiSettingsContent
import com.foldfinance.app.ui.ai.settings.AiSettingsState
import com.foldfinance.app.ui.shell.Section
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.prefs.RelockTimeout
import com.foldfinance.app.prefs.ThemeMode
import com.foldfinance.app.security.DeviceAuthStatus
import com.foldfinance.app.ui.security.LockScreen
import com.foldfinance.app.ui.security.LockScreenMode
import com.foldfinance.core.model.Card
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Экран настроек и экран блокировки в 3 конфигурациях Fold × светлая/тёмная.
 * Чужие блоки (доступы и источники, категории) — слоты; здесь они пустые.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTests {

    private val sample = SettingsUiState(
        settings = AppSettings(relockTimeout = RelockTimeout.FIVE_MINUTES),
        autoTracking = true,
        cards = listOf(
            Card(mask = "*1234", label = "Зарплатная", isOwn = true),
            Card(mask = "*4417", label = "Кредитка", isOwn = true),
            Card(mask = "*9876", label = null, isOwn = false),
        ),
        deviceAuth = DeviceAuthStatus.AVAILABLE,
    )

    /** Внешний экран — список разделов; две панели — список и первый раздел рядом. */
    @Test fun list() = captureInAllConfigs("settings_list") {
        SettingsScreen(state = sample, selected = null, actions = SettingsActions.None)
    }

    /**
     * Inside the real shell: on the 674 dp inner screen the rail leaves ≈ 230 dp to the list of
     * sections (compact rows), on the 337 dp outer one the list gets the «Подписки» row.
     */
    @Test fun listInShell() = captureInAllConfigs(
        "settings_shell",
        configs = listOf(DeviceConfigs.InnerPortrait, DeviceConfigs.Inner, DeviceConfigs.Tabletop, DeviceConfigs.OuterNarrow),
    ) {
        Shell(Section.Settings) { m ->
            SettingsScreen(state = sample, selected = SettingsPane.CURRENCY, actions = SettingsActions.None, modifier = m, onOpenSubscriptions = {})
        }
    }

    @Test fun security() = captureInAllConfigs("settings_security") {
        SettingsScreen(state = sample, selected = SettingsPane.SECURITY, actions = SettingsActions.None)
    }

    /** Телефон без PIN: предупреждение «блокировка не работает» с кнопкой в настройки телефона. */
    @Test fun securityUnprotected() = captureInAllConfigs("settings_security_unprotected", configs = listOf(DeviceConfigs.Outer)) {
        SettingsScreen(
            state = sample.copy(deviceAuth = DeviceAuthStatus.NOT_CONFIGURED),
            selected = SettingsPane.SECURITY,
            actions = SettingsActions.None,
        )
    }

    /** «Мои карты» open «Счета и карты» on the whole section (its screenshots: AccountsScreenshotTest). */
    @Test fun cardsOpenAccounts() = captureInAllConfigs("settings_cards", configs = listOf(DeviceConfigs.Outer)) {
        SettingsScreen(
            state = sample,
            selected = SettingsPane.CARDS,
            actions = SettingsActions.None,
            accounts = { _ -> androidx.compose.material3.Text("Счета и карты") },
        )
    }

    @Test fun backup() = captureInAllConfigs("settings_backup") {
        SettingsScreen(
            state = sample.copy(backup = BackupStatus.Failed(BackupKind.IMPORT, BackupError.WRONG_PASSWORD)),
            selected = SettingsPane.BACKUP,
            actions = SettingsActions.None,
        )
    }

    @Test fun appearance() = captureInAllConfigs("settings_appearance") {
        SettingsScreen(
            state = sample.copy(settings = sample.settings.copy(themeMode = ThemeMode.DARK)),
            selected = SettingsPane.APPEARANCE,
            actions = SettingsActions.None,
        )
    }

    /** «Локальный ИИ»: модель скачивается — статус, прогресс; блок ИИ встроен слотом, как в приложении. */
    @Test fun localAi() = captureInAllConfigs("settings_ai") {
        SettingsScreen(
            state = sample,
            selected = SettingsPane.AI,
            actions = SettingsActions.None,
            aiSection = {
                AiSettingsContent(
                    state = AiSettingsState(enabled = true, status = AiStatus.Downloading(640L * 1024 * 1024, 1_536L * 1024 * 1024)),
                    onEnabledChange = {},
                    onDownload = {},
                )
            },
        )
    }

    /** Модель можно скачать: строка-действие «Скачать модель». */
    @Test fun localAiDownloadable() = captureInAllConfigs("settings_ai_downloadable", configs = listOf(DeviceConfigs.Outer)) {
        SettingsScreen(
            state = sample,
            selected = SettingsPane.AI,
            actions = SettingsActions.None,
            aiSection = { AiSettingsContent(AiSettingsState(enabled = true, status = AiStatus.Downloadable), {}, {}) },
        )
    }

    @Test fun lock() = captureInAllConfigs("lock_screen") {
        LockScreen(mode = LockScreenMode.Locked(), onUnlock = {}, onContinueUnprotected = {}, onOpenSecuritySettings = {})
    }

    @Test fun lockUnprotected() = captureInAllConfigs("lock_unprotected") {
        LockScreen(mode = LockScreenMode.NotConfigured, onUnlock = {}, onContinueUnprotected = {}, onOpenSecuritySettings = {})
    }
}
