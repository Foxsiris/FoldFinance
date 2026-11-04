package com.foldfinance.app.ingest

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.foldfinance.app.ingest.push.InstalledAppsSource
import com.foldfinance.app.ingest.push.PackageManagerAppsSource
import com.foldfinance.app.ingest.sms.ContentResolverSmsReader
import com.foldfinance.app.ingest.sms.DatabaseOperationLookup
import com.foldfinance.app.ingest.sms.OperationLookup
import com.foldfinance.app.ingest.sms.SmsReader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Process-wide scope of the ingest layer: push notifications are ingested here, outside the
 * listener callback. A failing child never cancels the others.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IngestCoroutineScope

/** DataStore of ingest runtime state (`ingest_runtime`): onboarding flag, full-scan parser version. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IngestRuntimeStore

/**
 * Bindings of the ingest layer. `ParserRegistry`, `@DataClock Clock`, `IngestRepository` and
 * `IngestSettings` come from `:core` and are not re-declared here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IngestModule {

    @Binds
    abstract fun smsReader(impl: ContentResolverSmsReader): SmsReader

    @Binds
    abstract fun operationLookup(impl: DatabaseOperationLookup): OperationLookup

    @Binds
    abstract fun installedApps(impl: PackageManagerAppsSource): InstalledAppsSource

    companion object {
        @Provides
        @Singleton
        @IngestCoroutineScope
        fun provideIngestScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @Singleton
        @IngestRuntimeStore
        fun provideRuntimeStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("ingest_runtime") })
    }
}
