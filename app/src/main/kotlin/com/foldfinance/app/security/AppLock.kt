package com.foldfinance.app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.foldfinance.app.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Монотонное время (не меняется при переводе часов пользователем), мс. */
fun interface MonotonicClock {
    fun nowMillis(): Long

    companion object {
        val System: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
    }
}

/** Что показывать вместо приложения. */
enum class LockState {
    /** Настройки ещё не прочитаны: пустой фон, без содержимого. */
    LOADING,

    /** Нужна проверка пользователя: экран блокировки, содержимое не компонуется. */
    LOCKED,

    /** Приложение открыто. */
    UNLOCKED,
}

/**
 * Логика блокировки без Android: что делать при запуске, уходе в фон и возвращении.
 *
 * - При старте процесса с включённой блокировкой — [LockState.LOCKED]. Состояние живёт только
 *   в памяти: после убийства процесса приложение снова заблокировано.
 * - Возвращение из фона через [timeoutMillis] и дольше блокирует снова (`0` — сразу).
 * - Включение блокировки в настройках не блокирует текущий сеанс, выключение — разблокирует.
 * - [allowExternalActivity]: приложение само открывает системный экран (выбор файла SAF,
 *   настройки телефона) — следующее возвращение из фона блокирует только после
 *   [EXTERNAL_ACTIVITY_GRACE_MILLIS], а не сразу.
 */
class LockStateMachine(private val clock: MonotonicClock) {
    private var enabled: Boolean? = null
    private var timeoutMillis: Long = 0
    private var backgroundAt: Long? = null
    private var externalActivity = false

    var state: LockState = LockState.LOADING
        private set

    fun configure(enabled: Boolean, timeoutMillis: Long) {
        require(timeoutMillis >= 0) { "timeout must not be negative" }
        val first = this.enabled == null
        this.enabled = enabled
        this.timeoutMillis = timeoutMillis
        state = when {
            !enabled -> LockState.UNLOCKED
            first -> LockState.LOCKED
            else -> state
        }
    }

    fun onBackground() {
        backgroundAt = clock.nowMillis()
    }

    fun onForeground() {
        val since = backgroundAt ?: return
        backgroundAt = null
        val grace = externalActivity
        externalActivity = false
        if (enabled != true || state != LockState.UNLOCKED) return
        val limit = if (grace) maxOf(timeoutMillis, EXTERNAL_ACTIVITY_GRACE_MILLIS) else timeoutMillis
        if (clock.nowMillis() - since >= limit) state = LockState.LOCKED
    }

    fun unlock() {
        if (state == LockState.LOCKED) state = LockState.UNLOCKED
    }

    fun allowExternalActivity() {
        externalActivity = true
    }

    companion object {
        val EXTERNAL_ACTIVITY_GRACE_MILLIS: Long = TimeUnit.MINUTES.toMillis(3)
    }
}

/**
 * Блокировка приложения на уровне процесса: следит за уходом в фон через
 * [ProcessLifecycleOwner] (переживает пересоздание Activity при складывании) и за настройками.
 * Сама проверка пользователя (BiometricPrompt) — в `ui/security`.
 */
@Singleton
class AppLock internal constructor(
    private val preferences: AppPreferences,
    clock: MonotonicClock,
) {
    @Inject constructor(preferences: AppPreferences) : this(preferences, MonotonicClock.System)

    private val machine = LockStateMachine(clock)
    private val _state = MutableStateFlow(machine.state)
    private var started = false

    val state: StateFlow<LockState> = _state.asStateFlow()

    /** Подписаться на процесс и настройки. Идемпотентно; вызывать на главном потоке. */
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = update { onBackground() }
            override fun onStart(owner: LifecycleOwner) = update { onForeground() }
        })
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            preferences.settings.collect { s -> update { configure(s.lockEnabled, s.relockTimeout.millis) } }
        }
    }

    /** Пользователь подтвердил личность (или устройство без защиты и он выбрал «Продолжить»). */
    fun unlock() = update { unlock() }

    /** См. [LockStateMachine.allowExternalActivity]. */
    fun allowExternalActivity() = update { allowExternalActivity() }

    private fun update(block: LockStateMachine.() -> Unit) {
        synchronized(machine) {
            machine.block()
            _state.value = machine.state
        }
    }
}
