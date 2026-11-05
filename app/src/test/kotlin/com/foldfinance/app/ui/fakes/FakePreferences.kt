package com.foldfinance.app.ui.fakes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.foldfinance.app.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory DataStore: the file one cannot rewrite on Windows (see `AppPreferencesTest`). */
class InMemoryPreferencesStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

/**
 * Real [AppPreferences] over memory. [currency] — «Валюта» already chosen in the settings
 * (`null` keeps the default RUB), readable synchronously by ViewModels on the first emission.
 */
fun fakePreferences(currency: String? = null): AppPreferences {
    val initial = if (currency == null) emptyPreferences() else mutablePreferencesOf(stringPreferencesKey("default_currency") to currency)
    return AppPreferences(InMemoryPreferencesStore(initial))
}
