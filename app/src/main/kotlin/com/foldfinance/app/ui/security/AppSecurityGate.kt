package com.foldfinance.app.ui.security

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.prefs.ThemeMode
import com.foldfinance.app.security.DeviceAuthChecker
import com.foldfinance.app.security.DeviceAuthStatus
import com.foldfinance.app.security.LockState
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Обёртка всего UI (этап 9).
 *
 * - **Тема:** системная/светлая/тёмная и dynamic color из настроек передаются в [content].
 * - **FLAG_SECURE** на окне Activity по настройке (по умолчанию вкл., в том числе пока
 *   настройки читаются): содержимое не попадает в скриншоты, запись экрана и «Недавние».
 * - **Блокировка:** при старте процесса и после N минут в фоне вместо приложения показывается
 *   [LockScreen] и `BiometricPrompt` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`). Пока экран
 *   заблокирован, [content] не компонуется вовсе (ни диалогов поверх, ни текста для TalkBack);
 *   его сохраняемое состояние хранит `SaveableStateHolder`, ViewModel живут в Activity, поэтому
 *   после разблокировки всё на месте. Разблокировка живёт только в памяти процесса.
 * - Если на телефоне нет ни биометрии, ни PIN, — честное объяснение и вход без проверки.
 */
@Composable
fun AppSecurityGate(content: @Composable (darkTheme: Boolean, dynamicColor: Boolean) -> Unit) {
    val viewModel: SecurityGateViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    val secure = settings?.secureWindow ?: true
    DisposableEffect(activity, secure) {
        val window = activity?.window
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    val dark = when (settings?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, null -> isSystemInDarkTheme()
    }
    val dynamicColor = settings?.dynamicColor ?: false
    val stateHolder = rememberSaveableStateHolder()

    when (lockState) {
        LockState.UNLOCKED -> stateHolder.SaveableStateProvider(CONTENT_KEY) { content(dark, dynamicColor) }
        LockState.LOADING -> FoldFinanceTheme(darkTheme = dark, dynamicColor = dynamicColor) {
            SystemBars(activity, dark)
            Box(Modifier.fillMaxSize().background(FoldFinanceTheme.palette.ink))
        }
        LockState.LOCKED -> FoldFinanceTheme(darkTheme = dark, dynamicColor = dynamicColor) {
            SystemBars(activity, dark)
            LockRoute(viewModel)
        }
    }
}

private const val CONTENT_KEY = "app"

/** Иконки системных панелей под тему, пока приложение (и его собственная настройка панелей) скрыто. */
@Composable
private fun SystemBars(activity: android.app.Activity?, dark: Boolean) {
    DisposableEffect(activity, dark) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        (activity as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
}

/**
 * Экран блокировки + системный BiometricPrompt. Запрос показывается сам один раз на каждое
 * событие блокировки (когда Activity на переднем плане); дальше — по кнопке «Разблокировать».
 */
@Composable
private fun LockRoute(viewModel: SecurityGateViewModel) {
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    var status by remember { mutableStateOf(viewModel.deviceAuthStatus()) }
    var error by remember { mutableStateOf<String?>(null) }
    // Saveable: после пересоздания Activity (сложили телефон) запрос не дублируется.
    var autoPrompt by rememberSaveable { mutableStateOf(true) }

    val lockoutText = stringResource(R.string.lock_error_lockout)
    val genericText = stringResource(R.string.lock_error_generic)
    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)

    val prompt = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        error = null
                        viewModel.onAuthenticated()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Unit
                            // Отменила система (ушли в фон, повернули): спросим снова при возврате.
                            BiometricPrompt.ERROR_CANCELED -> autoPrompt = true
                            BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> error = lockoutText
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            -> {
                                status = viewModel.deviceAuthStatus()
                                if (status == DeviceAuthStatus.AVAILABLE) error = genericText.format(errString)
                            }
                            else -> error = genericText.format(errString)
                        }
                    }
                },
            )
        }
    }
    val promptInfo = remember(promptTitle, promptSubtitle) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .setAllowedAuthenticators(DeviceAuthChecker.AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
    }

    fun authenticate() {
        error = null
        if (prompt == null) status = DeviceAuthStatus.UNAVAILABLE else prompt.authenticate(promptInfo)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, prompt) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Пользователь мог настроить PIN в системных настройках и вернуться.
            status = viewModel.deviceAuthStatus()
            if (status == DeviceAuthStatus.AVAILABLE && autoPrompt) {
                autoPrompt = false
                authenticate()
            }
        }
    }

    LockScreen(
        mode = when (status) {
            DeviceAuthStatus.AVAILABLE -> LockScreenMode.Locked(error)
            DeviceAuthStatus.NOT_CONFIGURED -> LockScreenMode.NotConfigured
            DeviceAuthStatus.UNAVAILABLE -> LockScreenMode.Unavailable
        },
        onUnlock = ::authenticate,
        onContinueUnprotected = viewModel::onAuthenticated,
        onOpenSecuritySettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        },
    )
}
