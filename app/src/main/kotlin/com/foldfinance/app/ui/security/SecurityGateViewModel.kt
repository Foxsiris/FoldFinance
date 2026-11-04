package com.foldfinance.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.security.AppLock
import com.foldfinance.app.security.DeviceAuthChecker
import com.foldfinance.app.security.DeviceAuthStatus
import com.foldfinance.app.security.LockState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Состояние [AppSecurityGate]: настройки оформления и окна, состояние блокировки процесса.
 * `null` в [settings] — настройки ещё читаются (окно уже защищено FLAG_SECURE по умолчанию).
 */
@HiltViewModel
class SecurityGateViewModel @Inject constructor(
    preferences: AppPreferences,
    private val appLock: AppLock,
    private val deviceAuth: DeviceAuthChecker,
) : ViewModel() {

    init {
        appLock.start()
    }

    val settings: StateFlow<AppSettings?> =
        preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lockState: StateFlow<LockState> = appLock.state

    fun deviceAuthStatus(): DeviceAuthStatus = deviceAuth.status()

    fun onAuthenticated() = appLock.unlock()
}
