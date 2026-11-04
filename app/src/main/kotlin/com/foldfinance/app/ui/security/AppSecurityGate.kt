package com.foldfinance.app.ui.security

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * Обёртка всего UI (этап 9): тема из настроек (системная/светлая/тёмная, dynamic color),
 * FLAG_SECURE, блокировка BiometricPrompt + DEVICE_CREDENTIAL с повторной блокировкой по таймауту.
 * Заглушка каркаса: системная тема, без блокировки.
 */
@Composable
fun AppSecurityGate(content: @Composable (darkTheme: Boolean, dynamicColor: Boolean) -> Unit) {
    content(isSystemInDarkTheme(), false)
}
