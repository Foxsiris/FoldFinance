package com.foldfinance.app.ai.di

import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.MlKitLocalAi
import com.foldfinance.app.ai.backfill.AiIngestHook
import com.foldfinance.core.data.ingest.IngestObserver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope процесса для локального ИИ: загрузка модели и слежение за статусом переживают экраны. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    /** Gemini Nano через AICore (ML Kit GenAI Prompt API). */
    @Binds
    @Singleton
    abstract fun bindLocalAi(impl: MlKitLocalAi): LocalAi

    /** Приём сообщений ставит фоновую дообработку моделью. */
    @Binds
    @IntoSet
    abstract fun aiIngestHook(impl: AiIngestHook): IngestObserver

    companion object {
        @Provides
        @Singleton
        @AiCoroutineScope
        fun provideAiScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
