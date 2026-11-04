package com.foldfinance.app.ui.analytics

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier

/**
 * Clock of the insight screens (analytics, subscriptions): «today», period boundaries and the
 * calendar are computed in its zone. The system default zone in production; tests pass a fixed
 * clock to the ViewModels directly.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InsightsClock

@Module
@InstallIn(SingletonComponent::class)
object InsightsModule {
    @Provides
    @InsightsClock
    fun provideInsightsClock(): Clock = Clock.systemDefaultZone()
}
