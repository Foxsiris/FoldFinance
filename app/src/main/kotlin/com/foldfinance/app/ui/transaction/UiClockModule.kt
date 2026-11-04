package com.foldfinance.app.ui.transaction

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier

/**
 * Clock of screen ViewModels: «today», «this month», the default time of a manual operation.
 * The system clock in the user's time zone; tests pass a fixed clock directly.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UiClock

@Module
@InstallIn(SingletonComponent::class)
object UiClockModule {
    @Provides
    @UiClock
    fun provideUiClock(): Clock = Clock.systemDefaultZone()
}
