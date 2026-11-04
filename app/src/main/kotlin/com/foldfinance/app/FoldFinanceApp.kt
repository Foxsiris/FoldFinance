package com.foldfinance.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.foldfinance.app.ingest.IngestScheduler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FoldFinanceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Timber только в debug. Тексты сообщений и суммы не логируются нигде.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        // Периодический дельта-скан SMS и проверка перепарсинга (идемпотентно, KEEP).
        IngestScheduler(this).ensureScheduled()
    }
}
