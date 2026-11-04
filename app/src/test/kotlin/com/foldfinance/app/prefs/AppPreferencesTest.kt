package com.foldfinance.app.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Логика настроек поверх DataStore. Файловый DataStore не используется: на Windows его атомарное
 * переименование падает при повторной записи (как в `IngestSettingsTest` модуля `:core`).
 */
class AppPreferencesTest {

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    @Test
    fun defaults_areSafe() = runTest {
        val settings = AppPreferences(InMemoryPreferencesStore()).current()
        assertThat(settings).isEqualTo(AppSettings())
        assertThat(settings.secureWindow).isTrue()
        assertThat(settings.lockEnabled).isTrue()
        assertThat(settings.dynamicColor).isFalse()
        assertThat(settings.hideWidgetAmounts).isFalse()
        assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.relockTimeout).isEqualTo(RelockTimeout.ONE_MINUTE)
        assertThat(settings.defaultCurrency).isEqualTo("RUB")
    }

    @Test
    fun setters_persistInStore() = runTest {
        val store = InMemoryPreferencesStore()
        val prefs = AppPreferences(store)
        prefs.setThemeMode(ThemeMode.DARK)
        prefs.setDynamicColor(true)
        prefs.setSecureWindow(false)
        prefs.setLockEnabled(false)
        prefs.setRelockTimeout(RelockTimeout.FIFTEEN_MINUTES)
        prefs.setDefaultCurrency("EUR")
        prefs.setHideWidgetAmounts(true)

        val expected = AppSettings(
            themeMode = ThemeMode.DARK,
            dynamicColor = true,
            secureWindow = false,
            lockEnabled = false,
            relockTimeout = RelockTimeout.FIFTEEN_MINUTES,
            defaultCurrency = "EUR",
            hideWidgetAmounts = true,
        )
        // Новый экземпляр поверх того же хранилища видит те же значения.
        assertThat(AppPreferences(store).current()).isEqualTo(expected)
    }

    @Test
    fun settingsFlow_emitsOnlyOnChange() = runTest {
        val prefs = AppPreferences(InMemoryPreferencesStore())
        prefs.settings.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)
            prefs.setThemeMode(ThemeMode.SYSTEM) // то же значение по умолчанию — снимок не меняется
            prefs.setThemeMode(ThemeMode.LIGHT)
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.LIGHT)
            expectNoEvents()
        }
    }

    @Test
    fun unknownEnumNames_fallBackToDefaults() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "SEPIA"
            it[stringPreferencesKey("relock_timeout")] = "NEVER"
        }
        val settings = AppPreferences(store).current()
        assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.relockTimeout).isEqualTo(RelockTimeout.ONE_MINUTE)
    }

    @Test
    fun defaultCurrency_mustBeIsoCode() = runTest {
        val prefs = AppPreferences(InMemoryPreferencesStore())
        assertThat(runCatching { prefs.setDefaultCurrency("rub") }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(prefs.current().defaultCurrency).isEqualTo("RUB")
    }
}
