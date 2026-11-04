package com.foldfinance.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Настройки приёма сообщений (DataStore Preferences, файл `ingest_settings`).
 *
 * - [lastProcessedSmsId] — курсор дельта-скана SMS: максимальный обработанный `_id`.
 * - [pushPackageWhitelist] — пакеты, чьи уведомления парсятся. По умолчанию пусто:
 *   пользователь выбирает банки сам, остальные уведомления отбрасываются без сохранения.
 */
@Singleton
class IngestSettings @Inject constructor(
    @IngestSettingsStore private val store: DataStore<Preferences>,
) {
    /** Курсор дельта-скана; `0`, пока ничего не сканировалось. */
    val lastProcessedSmsId: Flow<Long> =
        store.data.map { it[KEY_LAST_SMS_ID] ?: 0L }.distinctUntilChanged()

    val pushPackageWhitelist: Flow<Set<String>> =
        store.data.map { it[KEY_WHITELIST] ?: emptySet() }.distinctUntilChanged()

    /** Автоотслеживание SMS и уведомлений (переключатель в настройках). По умолчанию включено. */
    val autoTrackingEnabled: Flow<Boolean> =
        store.data.map { it[KEY_AUTO_TRACKING] ?: true }.distinctUntilChanged()

    suspend fun autoTrackingEnabled(): Boolean = autoTrackingEnabled.first()

    suspend fun setAutoTrackingEnabled(enabled: Boolean) {
        store.edit { it[KEY_AUTO_TRACKING] = enabled }
    }

    suspend fun lastProcessedSmsId(): Long = lastProcessedSmsId.first()

    suspend fun pushPackageWhitelist(): Set<String> = pushPackageWhitelist.first()

    /** Сдвинуть курсор вперёд. Меньшее значение игнорируется (курсор монотонный). */
    suspend fun advanceLastProcessedSmsId(smsId: Long) {
        store.edit { prefs ->
            val current = prefs[KEY_LAST_SMS_ID] ?: 0L
            if (smsId > current) prefs[KEY_LAST_SMS_ID] = smsId
        }
    }

    /** Сбросить курсор (полное повторное сканирование; оно идемпотентно). */
    suspend fun resetLastProcessedSmsId() {
        store.edit { it.remove(KEY_LAST_SMS_ID) }
    }

    suspend fun setPushPackageWhitelist(packages: Set<String>) {
        store.edit { it[KEY_WHITELIST] = packages.map(String::trim).filter(String::isNotEmpty).toSet() }
    }

    suspend fun addToWhitelist(packageName: String) {
        val pkg = packageName.trim()
        require(pkg.isNotEmpty())
        store.edit { it[KEY_WHITELIST] = (it[KEY_WHITELIST] ?: emptySet()) + pkg }
    }

    suspend fun removeFromWhitelist(packageName: String) {
        store.edit { it[KEY_WHITELIST] = (it[KEY_WHITELIST] ?: emptySet()) - packageName.trim() }
    }

    private companion object {
        val KEY_LAST_SMS_ID = longPreferencesKey("last_processed_sms_id")
        val KEY_WHITELIST = stringSetPreferencesKey("push_package_whitelist")
        val KEY_AUTO_TRACKING = booleanPreferencesKey("auto_tracking_enabled")
    }
}
