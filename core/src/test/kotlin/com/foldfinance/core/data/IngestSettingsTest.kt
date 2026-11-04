package com.foldfinance.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import com.foldfinance.core.data.settings.IngestSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Логика настроек поверх DataStore. Файловый DataStore здесь не используется: на Windows его
 * атомарное переименование файла падает при повторной записи (на Android/Linux проблемы нет).
 */
class IngestSettingsTest {

    private class InMemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private fun settings() = IngestSettings(InMemoryPreferencesStore())

    @Test
    fun lastProcessedSmsId_isMonotonic_andResettable() = runTest {
        val s = settings()
        assertThat(s.lastProcessedSmsId()).isEqualTo(0L)
        s.advanceLastProcessedSmsId(10)
        s.advanceLastProcessedSmsId(7)
        assertThat(s.lastProcessedSmsId()).isEqualTo(10L)
        s.resetLastProcessedSmsId()
        assertThat(s.lastProcessedSmsId()).isEqualTo(0L)
    }

    @Test
    fun whitelist_addRemove() = runTest {
        val s = settings()
        s.pushPackageWhitelist.test {
            assertThat(awaitItem()).isEmpty()
            s.addToWhitelist(" ru.bank.one ")
            assertThat(awaitItem()).containsExactly("ru.bank.one")
            s.setPushPackageWhitelist(setOf("ru.bank.one", "ru.bank.two", " "))
            assertThat(awaitItem()).containsExactly("ru.bank.one", "ru.bank.two")
            s.removeFromWhitelist("ru.bank.one")
            assertThat(awaitItem()).containsExactly("ru.bank.two")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
