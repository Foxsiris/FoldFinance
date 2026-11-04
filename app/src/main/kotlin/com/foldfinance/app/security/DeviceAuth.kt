package com.foldfinance.app.security

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Чем телефон может подтвердить пользователя. */
enum class DeviceAuthStatus {
    /** Есть надёжная биометрия или PIN/пароль/графический ключ. */
    AVAILABLE,

    /** На телефоне не настроены ни биометрия, ни блокировка экрана. */
    NOT_CONFIGURED,

    /** Проверка временно недоступна (датчик занят, нужно обновление безопасности). */
    UNAVAILABLE,
}

/**
 * Доступность BiometricPrompt с `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (допустимо с API 30,
 * minSdk 31). Если биометрии нет, но экран телефона защищён PIN, проверка всё равно доступна.
 */
@Singleton
class DeviceAuthChecker @Inject constructor(@ApplicationContext private val context: Context) {

    fun status(): DeviceAuthStatus {
        val secure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        return when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> DeviceAuthStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            -> if (secure) DeviceAuthStatus.AVAILABLE else DeviceAuthStatus.NOT_CONFIGURED
            else -> if (secure) DeviceAuthStatus.AVAILABLE else DeviceAuthStatus.UNAVAILABLE
        }
    }

    companion object {
        const val AUTHENTICATORS: Int = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}
