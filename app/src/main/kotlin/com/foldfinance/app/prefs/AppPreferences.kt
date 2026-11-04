package com.foldfinance.app.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Тема оформления. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Через сколько минут в фоне приложение снова блокируется. */
enum class RelockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    ONE_MINUTE(TimeUnit.MINUTES.toMillis(1)),
    FIVE_MINUTES(TimeUnit.MINUTES.toMillis(5)),
    FIFTEEN_MINUTES(TimeUnit.MINUTES.toMillis(15)),
}

/**
 * Снимок настроек приложения.
 *
 * @property secureWindow FLAG_SECURE: содержимое не попадает в скриншоты и миниатюры «Недавних».
 * @property lockEnabled блокировка BiometricPrompt + PIN устройства при запуске и после фона.
 * @property hideWidgetAmounts виджет показывает «•••» вместо сумм (счётчик «на проверку» остаётся).
 * @property defaultCurrency ISO 4217: валюта сводок «Сегодня / Месяц» и ручного ввода.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val secureWindow: Boolean = true,
    val lockEnabled: Boolean = true,
    val relockTimeout: RelockTimeout = RelockTimeout.ONE_MINUTE,
    val defaultCurrency: String = DEFAULT_CURRENCY,
    val hideWidgetAmounts: Boolean = false,
) {
    companion object {
        const val DEFAULT_CURRENCY: String = "RUB"
    }
}

/**
 * Настройки приложения (DataStore Preferences, файл `app_prefs`). Значения по умолчанию —
 * в [AppSettings]: фирменная палитра (dynamic color выкл.), FLAG_SECURE вкл., блокировка вкл.
 *
 * Неизвестные значения перечислений (после отката версии) читаются как значения по умолчанию.
 * Пароли и ключи здесь не хранятся никогда.
 */
@Singleton
class AppPreferences @Inject constructor(
    @AppPrefsStore private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setSecureWindow(enabled: Boolean) {
        store.edit { it[SECURE_WINDOW] = enabled }
    }

    suspend fun setLockEnabled(enabled: Boolean) {
        store.edit { it[LOCK_ENABLED] = enabled }
    }

    suspend fun setRelockTimeout(timeout: RelockTimeout) {
        store.edit { it[RELOCK_TIMEOUT] = timeout.name }
    }

    suspend fun setHideWidgetAmounts(hide: Boolean) {
        store.edit { it[HIDE_WIDGET_AMOUNTS] = hide }
    }

    /** @param currency ISO 4217, три заглавные буквы. */
    suspend fun setDefaultCurrency(currency: String) {
        require(currency.matches(Regex("^[A-Z]{3}$"))) { "currency must be ISO 4217" }
        store.edit { it[DEFAULT_CURRENCY] = currency }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOr(this[THEME], defaults.themeMode),
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            secureWindow = this[SECURE_WINDOW] ?: defaults.secureWindow,
            lockEnabled = this[LOCK_ENABLED] ?: defaults.lockEnabled,
            relockTimeout = enumOr(this[RELOCK_TIMEOUT], defaults.relockTimeout),
            defaultCurrency = this[DEFAULT_CURRENCY] ?: defaults.defaultCurrency,
            hideWidgetAmounts = this[HIDE_WIDGET_AMOUNTS] ?: defaults.hideWidgetAmounts,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SECURE_WINDOW = booleanPreferencesKey("secure_window")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val RELOCK_TIMEOUT = stringPreferencesKey("relock_timeout")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val HIDE_WIDGET_AMOUNTS = booleanPreferencesKey("hide_widget_amounts")
    }
}

/** DataStore настроек приложения (отдельно от `ingest_settings` модуля `:core`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppPrefsStore

@Module
@InstallIn(SingletonComponent::class)
object AppPreferencesModule {
    const val FILE_NAME: String = "app_prefs"

    @Provides
    @Singleton
    @AppPrefsStore
    fun provideStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(FILE_NAME) })
}
