package com.foldfinance.app.ai.backfill

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.ai.AiStatus
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.di.AiCoroutineScope
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.core.data.ai.AiBackfillUseCase
import com.foldfinance.core.data.ingest.IngestObserver
import com.foldfinance.core.data.ingest.IngestOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Фоновая дообработка локальной моделью ([AiBackfillUseCase]): нераспознанные сообщения, имена и
 * категории новых мерчантов. Ничего не делает, если ИИ выключен или модель не готова.
 *
 * AICore выполняет запросы только для приложения на переднем плане (иначе `BACKGROUND_USE_BLOCKED`) —
 * тогда проход останавливается без ошибки и повторяется при следующем запуске/возврате приложения
 * ([AiBackfillScheduler]). Крутится, пока есть работа, но не дольше [TIME_BUDGET_MILLIS].
 */
@HiltWorker
class AiBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ai: LocalAi,
    private val prefs: AppPreferences,
    private val backfill: AiBackfillUseCase,
    private val reader: LocalAiMessageReader,
    private val advisor: LocalAiMerchantAdvisor,
    private val scheduler: AiBackfillScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        scheduler.start()
        Result.success(workDataOf(KEY_OPERATIONS to run()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Ошибка БД/модели: не повторяем в цикле — следующий триггер запустит проход снова.
        if (BuildConfig.DEBUG) Timber.w("AI backfill failed: %s", e.javaClass.simpleName)
        Result.success()
    }

    private suspend fun run(): Int {
        if (!prefs.current().localAiEnabled) return 0
        val status = withTimeoutOrNull(STATUS_WAIT_MILLIS) { ai.status.first { it !is AiStatus.Checking } }
        if (status !is AiStatus.Ready || !backfill.hasPendingWork()) return 0
        val deadline = System.currentTimeMillis() + TIME_BUDGET_MILLIS
        var operations = 0
        while (true) {
            val report = backfill(reader, advisor)
            operations += report.operationsRecorded
            val progressed = report.messagesSent > 0 || report.merchantsDescribed > 0
            if (report.stopped || !progressed || System.currentTimeMillis() > deadline || !backfill.hasPendingWork()) break
        }
        return operations
    }

    companion object {
        const val KEY_OPERATIONS: String = "operations"
        private const val STATUS_WAIT_MILLIS = 15_000L
        private val TIME_BUDGET_MILLIS = TimeUnit.MINUTES.toMillis(8)
    }
}

/**
 * Когда запускать [AiBackfillWorker] (уникальная задача [WORK_NAME], KEEP):
 * - при старте процесса (`IngestScheduler.ensureScheduled`);
 * - при возврате приложения на передний план и когда модель стала готова (после [start]);
 * - после приёма сообщения, которое не распознано или ушло на проверку ([AiIngestHook]).
 */
@Singleton
class AiBackfillScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ai: LocalAi,
    @AiCoroutineScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Подписаться на передний план и готовность модели. Идемпотентно. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            ai.status.map { it is AiStatus.Ready }.distinctUntilChanged().collect { ready -> if (ready) request() }
        }
        scope.launch(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = request()
                },
            )
        }
    }

    private val lastRequestAt = AtomicLong(0L)

    /**
     * Поставить проход. Частые вызовы (полный скан SMS) схлопываются: работающий проход и так
     * доберёт всё, что появится, пока он идёт.
     */
    fun request() {
        val now = System.currentTimeMillis()
        val last = lastRequestAt.get()
        if (now - last < THROTTLE_MILLIS || !lastRequestAt.compareAndSet(last, now)) return
        enqueue(context)
    }

    companion object {
        const val WORK_NAME: String = "ai.backfill"
        private const val DELAY_SECONDS = 3L
        private const val THROTTLE_MILLIS = 2_000L

        /** Поставить проход (без Hilt — для `IngestScheduler`). WorkManager не инициализирован → ничего. */
        fun enqueue(context: Context) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<AiBackfillWorker>()
                    .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}

/**
 * Точка подключения ИИ к приёму: сообщение не распознано, ушло на проверку или признано
 * «не операцией» — поставить фоновую дообработку (если ИИ включён; сам приём модель не ждёт).
 */
class AiIngestHook @Inject constructor(
    private val scheduler: AiBackfillScheduler,
    private val ai: LocalAi,
) : IngestObserver {
    override fun onIngested(outcome: IngestOutcome) {
        val relevant = when (outcome) {
            is IngestOutcome.Unrecognized, is IngestOutcome.NotFinancial -> true
            is IngestOutcome.Recorded -> outcome.needsReview || outcome.createdCount > 0
            is IngestOutcome.AlreadyProcessed -> false
        }
        if (relevant && ai.status.value is AiStatus.Ready) scheduler.request()
    }
}
