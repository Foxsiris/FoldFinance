package com.foldfinance.app.ai.features.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.foldfinance.app.ai.features.receipt.CoreReceiptSplitStore
import com.foldfinance.app.ai.features.receipt.ReceiptSplitStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** DataStore of the AI features (`ai_features`): only dismissed anomaly keys, never amounts or texts. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiFeaturesStore

@Module
@InstallIn(SingletonComponent::class)
abstract class AiFeaturesModule {

    /** Receipt splits go to `transaction_split` (DB v4); analytics counts each part in its category. */
    @Binds
    abstract fun bindReceiptSplitStore(impl: CoreReceiptSplitStore): ReceiptSplitStore

    companion object {
        @Provides
        @Singleton
        @AiFeaturesStore
        fun provideStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("ai_features") }
    }
}
