package com.foldfinance.app.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.backup.BackupError
import com.foldfinance.app.captureInAllConfigs
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

    @Test fun cards() = captureInAllConfigs("settings_cards") {
        SettingsScreen(state = sample, selected = SettingsPane.CARDS, actions = SettingsActions.None)
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

    @Test fun lock() = captureInAllConfigs("lock_screen") {
        LockScreen(mode = LockScreenMode.Locked(), onUnlock = {}, onContinueUnprotected = {}, onOpenSecuritySettings = {})
    }

    @Test fun lockUnprotected() = captureInAllConfigs("lock_unprotected") {
        LockScreen(mode = LockScreenMode.NotConfigured, onUnlock = {}, onContinueUnprotected = {}, onOpenSecuritySettings = {})
    }
}
