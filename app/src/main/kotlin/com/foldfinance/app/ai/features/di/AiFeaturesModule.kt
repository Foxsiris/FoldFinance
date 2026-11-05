package com.foldfinance.app.ai.features.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.foldfinance.app.ai.features.common.ImageInputSupport
import com.foldfinance.app.ai.features.common.ProbingImageInputSupport
import com.foldfinance.app.ai.features.receipt.NoteReceiptSplitStore
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

    /** Swap for an implementation reading an explicit `LocalAi` capability once it exists. */
    @Binds
    @Singleton
    abstract fun bindImageInputSupport(impl: ProbingImageInputSupport): ImageInputSupport

    /** Interim: the receipt breakdown goes to the note. With the core split API: bind `CoreReceiptSplitStore`. */
    @Binds
    abstract fun bindReceiptSplitStore(impl: NoteReceiptSplitStore): ReceiptSplitStore

    companion object {
        @Provides
        @Singleton
        @AiFeaturesStore
        fun provideStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("ai_features") }
    }
}
