package com.foldfinance.app.ai.di

import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.UnavailableLocalAi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindLocalAi(impl: UnavailableLocalAi): LocalAi
}
