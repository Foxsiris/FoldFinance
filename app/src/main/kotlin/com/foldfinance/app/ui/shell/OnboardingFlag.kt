package com.foldfinance.app.ui.shell

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** «Онбординг пройден»: the shell shows `OnboardingRoute` until this is `true`. */
interface OnboardingFlag {
    val done: Flow<Boolean>

    suspend fun markDone()
}

/** DataStore of the shell (file `shell`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ShellStore

/** [OnboardingFlag] in DataStore Preferences, key `onboarding_done`. */
@Singleton
class DataStoreOnboardingFlag @Inject constructor(
    @ShellStore private val store: DataStore<Preferences>,
) : OnboardingFlag {
    override val done: Flow<Boolean> = store.data.map { it[KEY] ?: false }.distinctUntilChanged()

    override suspend fun markDone() {
        store.edit { it[KEY] = true }
    }

    companion object {
        val KEY = booleanPreferencesKey("onboarding_done")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ShellStoreModule {
    @Provides
    @Singleton
    @ShellStore
    fun provideShellStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("shell") })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ShellBindingsModule {
    @Binds
    abstract fun onboardingFlag(impl: DataStoreOnboardingFlag): OnboardingFlag
}
